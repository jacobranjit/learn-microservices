package com.mc.oletrex.inventory.dto;

/** A SKU that could not be deducted: available is 0 when the SKU has no inventory record. */
public record StockShortage(
        String skuCode,
        int requested,
        int available
) {
}
