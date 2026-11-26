package com.mc.oletrex.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressDto(
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 100) String state,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "must be an ISO 3166-1 alpha-2 country code, e.g. US") String country
) {
}
