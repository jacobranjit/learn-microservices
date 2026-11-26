package com.mc.oletrex.inventory.dto;

import com.mc.oletrex.inventory.model.LocationType;

import java.time.Instant;

public record LocationResponse(
        String code,
        String name,
        LocationType type,
        String addressLine,
        String city,
        String state,
        String postalCode,
        String country,
        int priority,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
