package com.mc.oletrex.inventory.service.impl;

import com.mc.oletrex.inventory.dto.CursorPage;
import com.mc.oletrex.inventory.dto.InventoryRequest;
import com.mc.oletrex.inventory.dto.InventoryResponse;
import com.mc.oletrex.inventory.dto.InventoryUpdateRequest;
import com.mc.oletrex.inventory.exception.InventoryAlreadyExistsException;
import com.mc.oletrex.inventory.exception.InventoryNotFoundException;
import com.mc.oletrex.inventory.model.Inventory;
import com.mc.oletrex.inventory.repository.InventoryRepository;
import com.mc.oletrex.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    @Override
    public InventoryResponse createInventory(InventoryRequest request) {
        if (inventoryRepository.existsBySkuCode(request.skuCode())) {
            throw new InventoryAlreadyExistsException(request.skuCode());
        }
        Inventory inventory = Inventory.builder()
                .skuCode(request.skuCode())
                .quantity(request.quantity())
                .build();
        return toResponse(inventoryRepository.save(inventory));
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

    @Override
    public InventoryResponse updateInventory(String skuCode, InventoryUpdateRequest request) {
        Inventory inventory = findInventory(skuCode);
        inventory.setQuantity(request.quantity());
        return toResponse(inventory);
    }

    @Override
    public void deleteInventory(String skuCode) {
        inventoryRepository.delete(findInventory(skuCode));
    }

    private Inventory findInventory(String skuCode) {
        return inventoryRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new InventoryNotFoundException(skuCode));
    }

    private InventoryResponse toResponse(Inventory inventory) {
        return new InventoryResponse(inventory.getSkuCode(), inventory.getQuantity(), inventory.getQuantity() > 0);
    }
}
