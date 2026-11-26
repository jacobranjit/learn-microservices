package com.mc.oletrex.inventory.service.impl;

import com.mc.oletrex.inventory.client.ProductClient;
import com.mc.oletrex.inventory.dto.CursorPage;
import com.mc.oletrex.inventory.dto.InventoryRequest;
import com.mc.oletrex.inventory.dto.InventoryResponse;
import com.mc.oletrex.inventory.dto.InventoryUpsertResult;
import com.mc.oletrex.inventory.dto.StockDeductionRequest;
import com.mc.oletrex.inventory.dto.StockItem;
import com.mc.oletrex.inventory.dto.StockShortage;
import com.mc.oletrex.inventory.exception.InsufficientStockException;
import com.mc.oletrex.inventory.exception.InventoryNotFoundException;
import com.mc.oletrex.inventory.exception.UnknownProductException;
import com.mc.oletrex.inventory.model.Inventory;
import com.mc.oletrex.inventory.repository.InventoryRepository;
import com.mc.oletrex.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductClient productClient;

    @Override
    // No surrounding transaction: the product-service call must not hold a DB connection open.
    // Each repository call below runs in its own transaction.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public InventoryUpsertResult saveInventory(InventoryRequest request) {
        Inventory existing = inventoryRepository.findBySkuCode(request.skuCode()).orElse(null);
        if (existing != null) {
            // Existing record: just a stock update, no product-service call needed.
            existing.setQuantity(request.quantity());
            return new InventoryUpsertResult(toResponse(inventoryRepository.save(existing)), false);
        }

        // New SKU: only create stock for products that exist (inactive ones are allowed).
        if (!productClient.productExists(request.skuCode())) {
            throw new UnknownProductException(request.skuCode());
        }
        Inventory created = inventoryRepository.save(Inventory.builder()
                .skuCode(request.skuCode())
                .quantity(request.quantity())
                .build());
        return new InventoryUpsertResult(toResponse(created), true);
    }

    @Override
    public List<InventoryResponse> deductStock(StockDeductionRequest request) {
        // Merge duplicate SKUs, and process them in SKU order so concurrent multi-item
        // deductions lock rows in the same order (avoids deadlocks).
        Map<String, Integer> totals = new TreeMap<>();
        for (StockItem item : request.items()) {
            totals.merge(item.skuCode(), item.quantity(), Integer::sum);
        }

        List<StockShortage> shortages = new ArrayList<>();
        totals.forEach((sku, quantity) -> {
            if (inventoryRepository.deductIfAvailable(sku, quantity) == 0) {
                int available = inventoryRepository.findBySkuCode(sku).map(Inventory::getQuantity).orElse(0);
                shortages.add(new StockShortage(sku, quantity, available));
            }
        });
        if (!shortages.isEmpty()) {
            // Unchecked exception: the transaction rolls back, undoing deductions already made.
            throw new InsufficientStockException(shortages);
        }

        return totals.keySet().stream().map(this::findInventory).map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryResponse getInventory(String skuCode) {
        return toResponse(findInventory(skuCode));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<InventoryResponse> getInventoryPage(String cursor, int limit) {
        long afterId = IdCursor.decode(cursor);

        // Ask for one extra row: if it comes back, there is a next page. One query per page.
        List<Inventory> rows = inventoryRepository.findByIdGreaterThanOrderByIdAsc(afterId, Limit.of(limit + 1));
        boolean hasMore = rows.size() > limit;
        List<Inventory> page = hasMore ? rows.subList(0, limit) : rows;

        List<InventoryResponse> items = page.stream().map(this::toResponse).toList();
        String nextCursor = hasMore ? IdCursor.encode(page.getLast().getId()) : null;
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    private Inventory findInventory(String skuCode) {
        return inventoryRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new InventoryNotFoundException(skuCode));
    }

    private InventoryResponse toResponse(Inventory inventory) {
        return new InventoryResponse(inventory.getSkuCode(), inventory.getQuantity(), inventory.getQuantity() > 0);
    }
}
