package com.mc.oletrex.inventory.dto;

public record InventoryResponse(
        String skuCode,
        Integer quantity,
        boolean inStock
) {
}
