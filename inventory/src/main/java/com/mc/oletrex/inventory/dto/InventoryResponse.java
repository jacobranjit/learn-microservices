package com.mc.oletrex.inventory.dto;

import java.util.List;

/**
 * Stock of one SKU across all locations.
 *
 * @param quantity  total across all locations
 * @param inStock   true if at least one active location has stock
 * @param locations per-location breakdown, preferred locations first
 */
public record InventoryResponse(
        String skuCode,
        Integer quantity,
        boolean inStock,
        List<LocationStock> locations
) {
}
