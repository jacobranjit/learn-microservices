package com.mc.oletrex.product.repository;

import com.mc.oletrex.product.model.Product;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySkuCode(String skuCode);

    boolean existsBySkuCode(String skuCode);

    /** Keyset page: products after the given id, in id order. Uses the primary-key index. */
    List<Product> findByIdGreaterThanOrderByIdAsc(Long afterId, Limit limit);
}
