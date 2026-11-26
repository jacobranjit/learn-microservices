package com.mc.oletrex.order.dto;

import com.mc.oletrex.order.model.OrderStatus;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String orderNumber,
        OrderStatus status,
        Instant createdAt,
        List<OrderLineItemResponse> lineItems
) {
}
