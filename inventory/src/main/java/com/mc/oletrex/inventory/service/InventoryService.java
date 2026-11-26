package com.mc.oletrex.inventory.service;

import com.mc.oletrex.inventory.dto.CursorPage;
import com.mc.oletrex.inventory.dto.InventoryRequest;
import com.mc.oletrex.inventory.dto.InventoryResponse;
import com.mc.oletrex.inventory.dto.InventoryUpdateRequest;

public interface InventoryService {

    /** @throws com.mc.oletrex.inventory.exception.InventoryAlreadyExistsException if the SKU already has a record */
    InventoryResponse createInventory(InventoryRequest request);

    /** @throws com.mc.oletrex.inventory.exception.InventoryNotFoundException if the SKU has no record */
    InventoryResponse getInventory(String skuCode);

    /**
     * One page of inventory records, ordered by creation (id).
     *
     * @param cursor null/blank for the first page, otherwise the nextCursor of the previous page
     * @param limit  page size
     * @throws com.mc.oletrex.inventory.exception.InvalidCursorException if the cursor cannot be decoded
     */
    CursorPage<InventoryResponse> getInventoryPage(String cursor, int limit);

    /** @throws com.mc.oletrex.inventory.exception.InventoryNotFoundException if the SKU has no record */
    InventoryResponse updateInventory(String skuCode, InventoryUpdateRequest request);

    /** @throws com.mc.oletrex.inventory.exception.InventoryNotFoundException if the SKU has no record */
    void deleteInventory(String skuCode);
}
