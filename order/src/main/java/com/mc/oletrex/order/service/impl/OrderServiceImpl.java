package com.mc.oletrex.order.service.impl;

import com.mc.oletrex.order.client.DownstreamServiceException;
import com.mc.oletrex.order.client.InsufficientStockException;
import com.mc.oletrex.order.client.InventoryClient;
import com.mc.oletrex.order.client.ProductClient;
import com.mc.oletrex.order.client.ProductInfo;
import com.mc.oletrex.order.client.StockItem;
import com.mc.oletrex.order.dto.AddressDto;
import com.mc.oletrex.order.dto.CursorPage;
import com.mc.oletrex.order.dto.OrderLineItemRequest;
import com.mc.oletrex.order.dto.OrderLineItemResponse;
import com.mc.oletrex.order.dto.OrderPlacementResult;
import com.mc.oletrex.order.dto.OrderPlacementResult.Outcome;
import com.mc.oletrex.order.dto.OrderRequest;
import com.mc.oletrex.order.dto.OrderResponse;
import com.mc.oletrex.order.exception.OrderNotFoundException;
import com.mc.oletrex.order.model.Order;
import com.mc.oletrex.order.model.OrderAddress;
import com.mc.oletrex.order.model.OrderLineItem;
import com.mc.oletrex.order.model.OrderStatus;
import com.mc.oletrex.order.repository.OrderRepository;
import com.mc.oletrex.order.service.Caller;
import com.mc.oletrex.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;

    @Override
    // No surrounding transaction: remote calls must not hold a DB connection open, and the
    // PLACED order must be saved even if a later step fails. Each save is its own transaction.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderPlacementResult createOrder(OrderRequest request, long customerId) {
        Order order = Order.builder()
                .orderNumber(UUID.randomUUID().toString())
                .status(OrderStatus.PLACED)
                .createdAt(Instant.now())
                .customerId(customerId)
                .shippingAddress(toEntity(request.shippingAddress()))
                .billingAddress(toEntity(request.billingAddress()))
                .build();
        request.lineItems().forEach(item -> order.addLineItem(toEntity(item)));
        Order placed = orderRepository.save(order);

        try {
            priceLineItems(placed);                        // 1. product service
            String locationCode = inventoryClient.deductStock(placed.getLineItems().stream()
                    .map(i -> new StockItem(i.getSkuCode(), i.getQuantity()))
                    .toList());                            // 2. inventory service
            placed.setFulfilmentLocationCode(locationCode);
            placed.setStatus(OrderStatus.CONFIRMED);       // 3. done
            return new OrderPlacementResult(toResponse(orderRepository.save(placed)), Outcome.CONFIRMED);
        } catch (OrderRejectedException | InsufficientStockException e) {
            return new OrderPlacementResult(cancel(placed, e.getMessage()), Outcome.REJECTED);
        } catch (DownstreamServiceException e) {
            return new OrderPlacementResult(cancel(placed, e.getMessage()), Outcome.SERVICE_UNAVAILABLE);
        }
    }

    /** Copies price and currency from the product service onto each line item. */
    private void priceLineItems(Order order) {
        Map<String, ProductInfo> products = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (OrderLineItem item : order.getLineItems()) {
            String sku = item.getSkuCode();
            if (products.containsKey(sku)) {
                continue;
            }
            ProductInfo product = productClient.findProduct(sku).orElse(null);
            if (product == null) {
                problems.add("Product not found: " + sku);
            } else if (!product.active()) {
                problems.add("Product is not active: " + sku);
            }
            products.put(sku, product);
        }
        if (!problems.isEmpty()) {
            throw new OrderRejectedException(String.join("; ", problems));
        }
        if (products.values().stream().map(ProductInfo::currency).distinct().count() > 1) {
            throw new OrderRejectedException("All products in an order must have the same currency");
        }
        for (OrderLineItem item : order.getLineItems()) {
            ProductInfo product = products.get(item.getSkuCode());
            item.setPrice(product.price());
            item.setCurrency(product.currency());
        }
    }

    private OrderResponse cancel(Order order, String reason) {
        order.setStatus(OrderStatus.CANCELLED);
        order.setStatusReason(reason);
        return toResponse(orderRepository.save(order));
    }

    /** A business rule failed while placing the order (not a technical error). */
    private static class OrderRejectedException extends RuntimeException {
        OrderRejectedException(String message) {
            super(message);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderNumber, Caller caller) {
        Order order = caller.admin()
                ? findOrder(orderNumber)
                : findOwnedOrder(orderNumber, caller);
        return toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<OrderResponse> getOrders(String cursor, int limit, Caller caller) {
        long afterId = IdCursor.decode(cursor);

        // Ask for one extra row: if it comes back, there is a next page.
        List<Order> rows = caller.admin()
                ? orderRepository.findByIdGreaterThanOrderByIdAsc(afterId, Limit.of(limit + 1))
                : orderRepository.findByCustomerIdAndIdGreaterThanOrderByIdAsc(
                        requireCustomerId(caller), afterId, Limit.of(limit + 1));
        boolean hasMore = rows.size() > limit;
        List<Order> page = hasMore ? rows.subList(0, limit) : rows;
        if (page.isEmpty()) {
            return new CursorPage<>(List.of(), null, false);
        }

        // 2 queries per page in total: orders (above) and their line items.
        // Same transaction, so this fills lineItems on the orders already loaded.
        orderRepository.findAllWithLineItemsByIdIn(page.stream().map(Order::getId).toList());

        List<OrderResponse> items = page.stream().map(this::toResponse).toList();
        String nextCursor = hasMore ? IdCursor.encode(page.getLast().getId()) : null;
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    @Override
    public void deleteOrder(String orderNumber) {
        // Line items are removed too (cascade = ALL, orphanRemoval = true on Order).
        orderRepository.delete(findOrder(orderNumber));
    }

    private Order findOrder(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException(orderNumber));
    }

    /** Another customer's order is reported as not found, so its existence is not revealed. */
    private Order findOwnedOrder(String orderNumber, Caller caller) {
        return orderRepository.findByOrderNumberAndCustomerId(orderNumber, requireCustomerId(caller))
                .orElseThrow(() -> new OrderNotFoundException(orderNumber));
    }

    private static Long requireCustomerId(Caller caller) {
        if (caller.customerId() == null) {
            throw new AccessDeniedException("Token has no customerId");
        }
        return caller.customerId();
    }

    private OrderAddress toEntity(AddressDto a) {
        return new OrderAddress(a.line1(), a.line2(), a.city(), a.state(), a.postalCode(), a.country());
    }

    private AddressDto toDto(OrderAddress a) {
        return a == null ? null
                : new AddressDto(a.getLine1(), a.getLine2(), a.getCity(), a.getState(), a.getPostalCode(), a.getCountry());
    }

    private OrderLineItem toEntity(OrderLineItemRequest item) {
        return OrderLineItem.builder()
                .skuCode(item.skuCode())
                .quantity(item.quantity())
                .build();
    }

    private OrderResponse toResponse(Order order) {
        List<OrderLineItemResponse> items = order.getLineItems().stream()
                .map(i -> new OrderLineItemResponse(i.getSkuCode(), i.getPrice(), i.getCurrency(), i.getQuantity()))
                .toList();
        return new OrderResponse(order.getOrderNumber(), order.getStatus(), order.getFulfilmentLocationCode(),
                order.getStatusReason(),
                order.getCreatedAt(), order.getCustomerId(),
                toDto(order.getShippingAddress()), toDto(order.getBillingAddress()), items);
    }
}
