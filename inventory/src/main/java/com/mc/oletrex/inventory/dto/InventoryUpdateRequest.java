package com.mc.oletrex.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Body for PUT /api/inventory/{skuCode}: sets the stock level. The SKU itself cannot change. */
public record InventoryUpdateRequest(
        @NotNull @PositiveOrZero Integer quantity
) {
}
