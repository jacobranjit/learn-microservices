package com.mc.oletrex.inventory.dto;

import java.util.List;

/**
 * @param locationCode the single location that supplied every item
 * @param items        stock left at that location for each SKU
 */
public record StockDeductionResponse(
        String locationCode,
        List<InventoryRecordResponse> items
) {
}
