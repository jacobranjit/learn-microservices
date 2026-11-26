package com.mc.oletrex.inventory.service.impl;

import com.mc.oletrex.inventory.client.ProductClient;
import com.mc.oletrex.inventory.dto.CursorPage;
import com.mc.oletrex.inventory.dto.InventoryRecordResponse;
import com.mc.oletrex.inventory.dto.InventoryRequest;
import com.mc.oletrex.inventory.dto.InventoryResponse;
import com.mc.oletrex.inventory.dto.InventoryUpsertResult;
import com.mc.oletrex.inventory.dto.LocationStock;
import com.mc.oletrex.inventory.dto.StockDeductionRequest;
import com.mc.oletrex.inventory.dto.StockDeductionResponse;
import com.mc.oletrex.inventory.dto.StockItem;
import com.mc.oletrex.inventory.dto.StockShortage;
import com.mc.oletrex.inventory.exception.InsufficientStockException;
import com.mc.oletrex.inventory.exception.InvalidLocationException;
import com.mc.oletrex.inventory.exception.InventoryNotFoundException;
import com.mc.oletrex.inventory.exception.NoSingleLocationException;
import com.mc.oletrex.inventory.exception.UnknownProductException;
import com.mc.oletrex.inventory.model.Inventory;
import com.mc.oletrex.inventory.model.Location;
import com.mc.oletrex.inventory.repository.InventoryRepository;
import com.mc.oletrex.inventory.repository.LocationRepository;
import com.mc.oletrex.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final ProductClient productClient;

    @Override
    public StockDeductionResponse deductStock(StockDeductionRequest request) {
        // Merge duplicate SKUs (sorted, so locks are taken in a consistent order).
        Map<String, Integer> needed = new TreeMap<>();
        for (StockItem item : request.items()) {
            needed.merge(item.skuCode(), item.quantity(), Integer::sum);
        }

        // Lock all records of these SKUs, then group them by active location.
        Map<Location, Map<String, Inventory>> byLocation = new LinkedHashMap<>();
        for (Inventory record : inventoryRepository.lockAllBySkuCodeIn(needed.keySet())) {
            if (record.getLocation().isActive()) {
                byLocation.computeIfAbsent(record.getLocation(), l -> new HashMap<>()).put(record.getSkuCode(), record);
            }
        }

        // Candidates: locations that have enough of EVERY item. Prefer lowest priority, then oldest.
        Location chosen = byLocation.entrySet().stream()
                .filter(e -> needed.entrySet().stream().allMatch(n -> {
                    Inventory record = e.getValue().get(n.getKey());
                    return record != null && record.getQuantity() >= n.getValue();
                }))
                .map(Map.Entry::getKey)
                .min(Comparator.comparingInt(Location::getPriority).thenComparing(Location::getId))
                .orElse(null);

        if (chosen == null) {
            throw explainShortfall(needed, byLocation);
        }

        Map<String, Inventory> records = byLocation.get(chosen);
        List<InventoryRecordResponse> items = needed.entrySet().stream().map(n -> {
            Inventory record = records.get(n.getKey());
            record.setQuantity(record.getQuantity() - n.getValue()); // saved on commit (dirty checking)
            return toRecord(record, chosen.getCode());
        }).toList();
        return new StockDeductionResponse(chosen.getCode(), items);
    }

    /** Insufficient stock overall, or enough overall but spread over several locations. */
    private RuntimeException explainShortfall(Map<String, Integer> needed,
                                              Map<Location, Map<String, Inventory>> byLocation) {
        List<StockShortage> shortages = new ArrayList<>();
        needed.forEach((sku, quantity) -> {
            int available = byLocation.values().stream()
                    .map(records -> records.get(sku))
                    .filter(r -> r != null)
                    .mapToInt(Inventory::getQuantity)
                    .sum();
            if (available < quantity) {
                shortages.add(new StockShortage(sku, quantity, available));
            }
        });
        if (!shortages.isEmpty()) {
            return new InsufficientStockException(shortages);
        }
        return new NoSingleLocationException(needed.entrySet().stream()
                .map(n -> n.getKey() + " x" + n.getValue())
                .collect(Collectors.joining(", ")));
    }

    @Override
    // No surrounding transaction: the product-service call must not hold a DB connection open.
    // Each repository call below runs in its own transaction.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public InventoryUpsertResult saveInventory(InventoryRequest request) {
        Location location = locationRepository.findByCode(request.locationCode())
                .orElseThrow(() -> new InvalidLocationException("No location with code " + request.locationCode()));
        if (!location.isActive()) {
            throw new InvalidLocationException("Location " + request.locationCode() + " is not active");
        }

        Inventory existing = inventoryRepository.findBySkuCodeAndLocation(request.skuCode(), location).orElse(null);
        if (existing != null) {
            // Existing record: just a stock update, no product-service call needed.
            existing.setQuantity(request.quantity());
            return new InventoryUpsertResult(toRecord(inventoryRepository.save(existing), location.getCode()), false);
        }

        // First stock for this SKU anywhere: only accept products that exist (inactive ones are allowed).
        if (!inventoryRepository.existsBySkuCode(request.skuCode())
                && !productClient.productExists(request.skuCode())) {
            throw new UnknownProductException(request.skuCode());
        }
        Inventory created = inventoryRepository.save(Inventory.builder()
                .skuCode(request.skuCode())
                .location(location)
                .quantity(request.quantity())
                .build());
        return new InventoryUpsertResult(toRecord(created, location.getCode()), true);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryResponse getInventory(String skuCode) {
        List<Inventory> records = inventoryRepository.findAllBySkuCodeWithLocation(skuCode);
        if (records.isEmpty()) {
            throw new InventoryNotFoundException(skuCode);
        }
        int total = records.stream().mapToInt(Inventory::getQuantity).sum();
        boolean inStock = records.stream().anyMatch(r -> r.getLocation().isActive() && r.getQuantity() > 0);
        List<LocationStock> locations = records.stream()
                .map(r -> new LocationStock(r.getLocation().getCode(), r.getQuantity(), r.getLocation().isActive()))
                .toList();
        return new InventoryResponse(skuCode, total, inStock, locations);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<InventoryRecordResponse> getInventoryPage(String cursor, int limit, String skuCode, String locationCode) {
        long afterId = IdCursor.decode(cursor);

        // Ask for one extra row: if it comes back, there is a next page. One query per page.
        List<Inventory> rows = inventoryRepository.findPage(afterId, blankToNull(skuCode), blankToNull(locationCode),
                Limit.of(limit + 1));
        boolean hasMore = rows.size() > limit;
        List<Inventory> page = hasMore ? rows.subList(0, limit) : rows;

        List<InventoryRecordResponse> items = page.stream()
                .map(r -> toRecord(r, r.getLocation().getCode()))
                .toList();
        String nextCursor = hasMore ? IdCursor.encode(page.getLast().getId()) : null;
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private InventoryRecordResponse toRecord(Inventory record, String locationCode) {
        return new InventoryRecordResponse(record.getSkuCode(), locationCode, record.getQuantity(), record.getQuantity() > 0);
    }
}
