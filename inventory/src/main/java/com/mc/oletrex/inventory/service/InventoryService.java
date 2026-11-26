package com.mc.oletrex.inventory.service;

import com.mc.oletrex.inventory.dto.CursorPage;
import com.mc.oletrex.inventory.dto.InventoryRequest;
import com.mc.oletrex.inventory.dto.InventoryResponse;
import com.mc.oletrex.inventory.dto.InventoryUpsertResult;
import com.mc.oletrex.inventory.dto.StockDeductionRequest;

import java.util.List;

public interface InventoryService {

    /**
     * Deducts every item in one transaction: either all succeed or nothing changes.
     * Quantities for the same SKU are added together first.
     *
     * @return the stock levels after the deduction
     * @throws com.mc.oletrex.inventory.exception.InsufficientStockException listing every SKU that is short
     */
    List<InventoryResponse> deductStock(StockDeductionRequest request);

    /**
     * Sets the stock level of a SKU: creates the record if the SKU has none, otherwise updates it.
     * Creating checks the product service first; updating an existing record does not.
     *
     * @throws com.mc.oletrex.inventory.exception.UnknownProductException  new SKU unknown to the product service
     * @throws com.mc.oletrex.inventory.client.DownstreamServiceException new SKU and the product service is unavailable
     */
    InventoryUpsertResult saveInventory(InventoryRequest request);

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

}
