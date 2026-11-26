package com.mc.oletrex.order.service.impl;

import com.mc.oletrex.order.dto.CursorPage;
import com.mc.oletrex.order.dto.OrderLineItemRequest;
import com.mc.oletrex.order.dto.OrderLineItemResponse;
import com.mc.oletrex.order.dto.OrderRequest;
import com.mc.oletrex.order.dto.OrderResponse;
import com.mc.oletrex.order.exception.OrderNotFoundException;
import com.mc.oletrex.order.model.Order;
import com.mc.oletrex.order.model.OrderLineItem;
import com.mc.oletrex.order.model.OrderStatus;
import com.mc.oletrex.order.repository.OrderRepository;
import com.mc.oletrex.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;

    @Override
    public OrderResponse createOrder(OrderRequest request) {
        Order order = Order.builder()
                .orderNumber(UUID.randomUUID().toString())
                .status(OrderStatus.PLACED)
                .createdAt(Instant.now())
                .build();
        request.lineItems().forEach(item -> order.addLineItem(toEntity(item)));
        return toResponse(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderNumber) {
        return toResponse(findOrder(orderNumber));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<OrderResponse> getOrders(String cursor, int limit) {
        long afterId = IdCursor.decode(cursor);

        // Ask for one extra row: if it comes back, there is a next page.
        List<Order> rows = orderRepository.findByIdGreaterThanOrderByIdAsc(afterId, Limit.of(limit + 1));
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

    private OrderLineItem toEntity(OrderLineItemRequest item) {
        return OrderLineItem.builder()
                .skuCode(item.skuCode())
                .price(item.price())
                .quantity(item.quantity())
                .build();
    }

    private OrderResponse toResponse(Order order) {
        List<OrderLineItemResponse> items = order.getLineItems().stream()
                .map(i -> new OrderLineItemResponse(i.getSkuCode(), i.getPrice(), i.getQuantity()))
                .toList();
        return new OrderResponse(order.getOrderNumber(), order.getStatus(), order.getCreatedAt(), items);
    }
}
