package com.mc.oletrex.inventory.service;

import com.mc.oletrex.inventory.dto.CursorPage;
import com.mc.oletrex.inventory.dto.InventoryRecordResponse;
import com.mc.oletrex.inventory.dto.InventoryRequest;
import com.mc.oletrex.inventory.dto.InventoryResponse;
import com.mc.oletrex.inventory.dto.InventoryUpsertResult;
import com.mc.oletrex.inventory.dto.StockDeductionRequest;
import com.mc.oletrex.inventory.dto.StockDeductionResponse;

public interface InventoryService {

    /**
     * Deducts every item from ONE active location that has enough of all of them, preferring
     * the lowest priority number. All items are deducted or none are.
     *
     * @throws com.mc.oletrex.inventory.exception.InsufficientStockException if active locations together don't have enough
     * @throws com.mc.oletrex.inventory.exception.NoSingleLocationException  if they do, but no single location has everything
     */
    StockDeductionResponse deductStock(StockDeductionRequest request);

    /**
     * Sets the stock of a SKU at a location: creates the record if needed, otherwise updates it.
     * A SKU that has no records at any location is first checked against the product service.
     *
     * @throws com.mc.oletrex.inventory.exception.InvalidLocationException   unknown or inactive location
     * @throws com.mc.oletrex.inventory.exception.UnknownProductException    new SKU unknown to the product service
     * @throws com.mc.oletrex.inventory.client.DownstreamServiceException new SKU and the product service is unavailable
     */
    InventoryUpsertResult saveInventory(InventoryRequest request);

    /** @throws com.mc.oletrex.inventory.exception.InventoryNotFoundException if the SKU has no records */
    InventoryResponse getInventory(String skuCode);

    /**
     * One page of stock records (one per SKU and location), ordered by creation.
     * skuCode and locationCode are optional filters.
     *
     * @throws com.mc.oletrex.inventory.exception.InvalidCursorException if the cursor cannot be decoded
     */
    CursorPage<InventoryRecordResponse> getInventoryPage(String cursor, int limit, String skuCode, String locationCode);
}
