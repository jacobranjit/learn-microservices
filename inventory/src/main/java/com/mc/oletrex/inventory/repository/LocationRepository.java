package com.mc.oletrex.inventory.repository;

import com.mc.oletrex.inventory.model.Location;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findByCode(String code);

    boolean existsByCode(String code);

    /** Keyset page: locations after the given id, in id order. */
    List<Location> findByIdGreaterThanOrderByIdAsc(Long afterId, Limit limit);
}
