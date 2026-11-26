package com.mc.oletrex.order.dto;

import com.mc.oletrex.order.model.OrderStatus;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String orderNumber,
        OrderStatus status,
        /* Inventory location that supplied the order; null unless CONFIRMED. */
        String fulfilmentLocationCode,
        /* Why the order was cancelled; null unless status is CANCELLED. */
        String statusReason,
        Instant createdAt,
        Long customerId,
        AddressDto shippingAddress,
        AddressDto billingAddress,
        List<OrderLineItemResponse> lineItems
) {
}
