package com.mc.oletrex.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Body for POST /api/inventory/deductions: all items are deducted together or not at all. */
public record StockDeductionRequest(
        @NotEmpty List<@Valid StockItem> items
) {
}
