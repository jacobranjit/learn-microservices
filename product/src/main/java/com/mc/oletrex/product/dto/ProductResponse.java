package com.mc.oletrex.product.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Single-product view, including details (null if the product has none). */
public record ProductResponse(
        String skuCode,
        String name,
        String description,
        BigDecimal price,
        String currency,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        ProductDetailsDto details
) {
}
