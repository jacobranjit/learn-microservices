package com.mc.oletrex.customer.service;

import com.mc.oletrex.customer.dto.CursorPage;
import com.mc.oletrex.customer.dto.CustomerCreateRequest;
import com.mc.oletrex.customer.dto.CustomerResponse;
import com.mc.oletrex.customer.dto.CustomerUpdateRequest;

public interface CustomerService {

    CustomerResponse create(CustomerCreateRequest request);

    void delete(Long id);

    CustomerResponse get(Long id);

    CustomerResponse update(Long id, CustomerUpdateRequest request);

    CursorPage<CustomerResponse> list(String cursor, int limit);
}
