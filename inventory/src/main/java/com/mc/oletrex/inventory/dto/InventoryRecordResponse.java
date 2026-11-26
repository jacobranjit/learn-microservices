package com.mc.oletrex.inventory.dto;

/** Stock of one SKU at one location. */
public record InventoryRecordResponse(
        String skuCode,
        String locationCode,
        Integer quantity,
        boolean inStock
) {
}
