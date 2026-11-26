package com.mc.oletrex.inventory.repository;

import com.mc.oletrex.inventory.model.Inventory;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findBySkuCode(String skuCode);

    /**
     * Atomically subtracts {@code quantity} if enough stock is available.
     * The check and the update happen in one statement, so concurrent orders cannot oversell.
     *
     * @return 1 if deducted, 0 if the SKU has no record or not enough stock
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Inventory i set i.quantity = i.quantity - :quantity "
            + "where i.skuCode = :skuCode and i.quantity >= :quantity")
    int deductIfAvailable(@Param("skuCode") String skuCode, @Param("quantity") int quantity);

    /** Keyset page: records after the given id, in id order. Uses the primary-key index. */
    List<Inventory> findByIdGreaterThanOrderByIdAsc(Long afterId, Limit limit);
}
