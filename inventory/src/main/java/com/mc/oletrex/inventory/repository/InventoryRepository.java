package com.mc.oletrex.inventory.repository;

import com.mc.oletrex.inventory.model.Inventory;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findBySkuCode(String skuCode);

    boolean existsBySkuCode(String skuCode);

    /** Keyset page: records after the given id, in id order. Uses the primary-key index. */
    List<Inventory> findByIdGreaterThanOrderByIdAsc(Long afterId, Limit limit);
}
