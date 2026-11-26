package com.mc.oletrex.order.repository;

import com.mc.oletrex.order.model.Order;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Fetch line items in the same query to avoid lazy-loading / N+1 selects.
    @EntityGraph(attributePaths = "lineItems")
    Optional<Order> findByOrderNumber(String orderNumber);

    /** Keyset page: orders after the given id, in id order. Uses the primary-key index. */
    List<Order> findByIdGreaterThanOrderByIdAsc(Long afterId, Limit limit);

    /*
     * Line items for a page of orders, in one query. Kept separate from the page query because
     * combining a collection fetch with a row limit makes Hibernate load every order and apply
     * the limit in memory.
     */
    @Query("select o from Order o left join fetch o.lineItems where o.id in :ids")
    List<Order> findAllWithLineItemsByIdIn(@Param("ids") Collection<Long> ids);
}
