package com.mc.oletrex.order.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** The fields the order service needs from the product service's ProductResponse. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductInfo(
        String skuCode,
        String name,
        BigDecimal price,
        String currency,
        boolean active
) {
}
