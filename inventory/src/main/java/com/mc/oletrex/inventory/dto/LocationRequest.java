package com.mc.oletrex.inventory.dto;

import com.mc.oletrex.inventory.model.LocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Body for POST /api/locations. */
public record LocationRequest(
        @NotBlank @Size(max = 50)
        @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]*", message = "use uppercase letters, digits, '-' or '_', e.g. BLR-WH1")
        String code,
        @NotBlank @Size(max = 255) String name,
        @NotNull LocationType type,
        @Size(max = 255) String addressLine,
        @Size(max = 255) String city,
        @Size(max = 255) String state,
        @Size(max = 20) String postalCode,
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be a 2-letter ISO 3166 code, e.g. IN") String country,
        /* Lower is preferred when choosing which location supplies an order. */
        @NotNull @PositiveOrZero Integer priority,
        /* Defaults to true when omitted. */
        Boolean active
) {
}
