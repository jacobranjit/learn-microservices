package com.mc.oletrex.inventory.dto;

/**
 * Outcome of a create-or-update.
 *
 * @param inventory the record as it is now
 * @param created   true if the record was new, false if an existing one was updated
 */
public record InventoryUpsertResult(
        InventoryRecordResponse inventory,
        boolean created
) {
}
