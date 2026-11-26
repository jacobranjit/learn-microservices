package com.mc.oletrex.inventory.repository;

import com.mc.oletrex.inventory.model.Inventory;
import com.mc.oletrex.inventory.model.Location;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findBySkuCodeAndLocation(String skuCode, Location location);

    /** True if the SKU has stock records at any location. */
    boolean existsBySkuCode(String skuCode);

    /** All records of a SKU with their locations, preferred locations first. */
    @Query("select i from Inventory i join fetch i.location l where i.skuCode = :skuCode order by l.priority, l.id")
    List<Inventory> findAllBySkuCodeWithLocation(@Param("skuCode") String skuCode);

    /**
     * Locks every record of the given SKUs (SELECT ... FOR UPDATE) until the transaction ends,
     * so no other deduction can change them while a location is chosen. Rows are locked in
     * (SKU, location) order so concurrent deductions cannot deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i join fetch i.location l where i.skuCode in :skuCodes order by i.skuCode, l.id")
    List<Inventory> lockAllBySkuCodeIn(@Param("skuCodes") Collection<String> skuCodes);

    /** Keyset page of records, in id order, optionally filtered by SKU and/or location code. */
    @Query("select i from Inventory i join fetch i.location l where i.id > :afterId "
            + "and (:skuCode is null or i.skuCode = :skuCode) "
            + "and (:locationCode is null or l.code = :locationCode) order by i.id")
    List<Inventory> findPage(@Param("afterId") Long afterId,
                             @Param("skuCode") String skuCode,
                             @Param("locationCode") String locationCode,
                             Limit limit);
}
