package com.mc.oletrex.customer.service.impl;

import com.mc.oletrex.customer.dto.AddressDto;
import com.mc.oletrex.customer.dto.CursorPage;
import com.mc.oletrex.customer.dto.CustomerCreateRequest;
import com.mc.oletrex.customer.dto.CustomerResponse;
import com.mc.oletrex.customer.dto.CustomerUpdateRequest;
import com.mc.oletrex.customer.exception.CustomerAlreadyExistsException;
import com.mc.oletrex.customer.exception.CustomerNotFoundException;
import com.mc.oletrex.customer.model.Address;
import com.mc.oletrex.customer.model.Customer;
import com.mc.oletrex.customer.repository.CustomerRepository;
import com.mc.oletrex.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    @Override
    public CustomerResponse create(CustomerCreateRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (customerRepository.existsByEmail(email)) {
            throw new CustomerAlreadyExistsException(email);
        }
        if (customerRepository.existsByAuthUserId(request.authUserId())) {
            throw new CustomerAlreadyExistsException("authUserId " + request.authUserId());
        }
        Customer saved = customerRepository.saveAndFlush(Customer.builder()
                .authUserId(request.authUserId())
                .name(request.name())
                .email(email)
                .phone(request.phone())
                .deliveryAddress(toAddress(request.deliveryAddress()))
                .build());
        return toResponse(saved);
    }

    @Override
    public void delete(Long id) {
        customerRepository.delete(find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return toResponse(find(id));
    }

    @Override
    public CustomerResponse update(Long id, CustomerUpdateRequest request) {
        Customer customer = find(id);
        customer.setName(request.name());
        customer.setPhone(request.phone());
        customer.setDeliveryAddress(toAddress(request.deliveryAddress()));
        return toResponse(customerRepository.saveAndFlush(customer));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<CustomerResponse> list(String cursor, int limit) {
        long afterId = IdCursor.decode(cursor);

        // Ask for one extra row: if it comes back, there is a next page.
        List<Customer> rows = customerRepository.findByIdGreaterThanOrderByIdAsc(afterId, Limit.of(limit + 1));
        boolean hasMore = rows.size() > limit;
        List<Customer> page = hasMore ? rows.subList(0, limit) : rows;
        if (page.isEmpty()) {
            return new CursorPage<>(List.of(), null, false);
        }
        String nextCursor = hasMore ? IdCursor.encode(page.getLast().getId()) : null;
        return new CursorPage<>(page.stream().map(this::toResponse).toList(), nextCursor, hasMore);
    }

    private Customer find(Long id) {
        return customerRepository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
    }

    private Address toAddress(AddressDto dto) {
        return new Address(dto.line1(), dto.line2(), dto.city(), dto.state(), dto.postalCode(), dto.country());
    }

    private CustomerResponse toResponse(Customer c) {
        Address a = c.getDeliveryAddress();
        AddressDto address = new AddressDto(a.getLine1(), a.getLine2(), a.getCity(), a.getState(),
                a.getPostalCode(), a.getCountry());
        return new CustomerResponse(c.getId(), c.getAuthUserId(), c.getName(), c.getEmail(), c.getPhone(),
                address, c.getCreatedAt(), c.getUpdatedAt());
    }
}
