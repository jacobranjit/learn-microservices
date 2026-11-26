package com.mc.oletrex.order.repository;

import com.mc.oletrex.order.model.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Fetch line items in the same query to avoid lazy-loading / N+1 selects.
    @EntityGraph(attributePaths = "lineItems")
    Optional<Order> findByOrderNumber(String orderNumber);

    @Override
    @EntityGraph(attributePaths = "lineItems")
    List<Order> findAll();
}
