package com.mc.oletrex.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Body for POST /api/inventory: sets the stock of a SKU at a location, creating the record if needed. */
public record InventoryRequest(
        @NotBlank @Size(max = 255) String skuCode,
        @NotBlank @Size(max = 50) String locationCode,
        @NotNull @PositiveOrZero Integer quantity
) {
}
