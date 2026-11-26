package com.mc.oletrex.order.service.impl;

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
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
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
