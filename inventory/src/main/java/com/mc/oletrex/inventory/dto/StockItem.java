package com.mc.oletrex.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StockItem(
        @NotBlank String skuCode,
        @NotNull @Positive Integer quantity
) {
}
