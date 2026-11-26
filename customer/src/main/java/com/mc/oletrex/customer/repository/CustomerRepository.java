package com.mc.oletrex.customer.repository;

import com.mc.oletrex.customer.model.Customer;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByEmail(String email);

    boolean existsByAuthUserId(Long authUserId);

    /** Keyset pagination: the next rows after the given id, in id order. */
    List<Customer> findByIdGreaterThanOrderByIdAsc(Long afterId, Limit limit);
}
