package com.mc.oletrex.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Body for POST /api/inventory. */
public record InventoryRequest(
        @NotBlank @Size(max = 255) String skuCode,
        @NotNull @PositiveOrZero Integer quantity
) {
}
