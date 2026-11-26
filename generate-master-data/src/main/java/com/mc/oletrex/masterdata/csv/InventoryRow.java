package com.mc.oletrex.masterdata.csv;

/** One row of inventory.csv. */
public record InventoryRow(
        String skuCode,
        String locationCode,
        Integer quantity
) {
}
