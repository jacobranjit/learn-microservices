package com.mc.oletrex.customer.dto;

import java.time.Instant;

public record CustomerResponse(
        Long id,
        Long authUserId,
        String name,
        String email,
        String phone,
        AddressDto deliveryAddress,
        Instant createdAt,
        Instant updatedAt
) {
}
