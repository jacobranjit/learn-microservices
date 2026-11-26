package com.mc.oletrex.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record OrderLineItemRequest(
        @NotBlank String skuCode,
        @NotNull @Positive BigDecimal price,
        @NotNull @Positive Integer quantity
) {
}
