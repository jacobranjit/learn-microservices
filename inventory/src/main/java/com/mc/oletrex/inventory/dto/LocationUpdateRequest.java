package com.mc.oletrex.inventory.dto;

import com.mc.oletrex.inventory.model.LocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Body for PUT /api/locations/{code}: replaces everything except the code, which never changes. */
public record LocationUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull LocationType type,
        @Size(max = 255) String addressLine,
        @Size(max = 255) String city,
        @Size(max = 255) String state,
        @Size(max = 20) String postalCode,
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be a 2-letter ISO 3166 code, e.g. IN") String country,
        @NotNull @PositiveOrZero Integer priority,
        @NotNull Boolean active
) {
}
