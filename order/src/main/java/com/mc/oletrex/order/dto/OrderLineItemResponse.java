package com.mc.oletrex.order.dto;

import java.math.BigDecimal;

public record OrderLineItemResponse(
        String skuCode,
        BigDecimal price,
        String currency,
        Integer quantity
) {
}
