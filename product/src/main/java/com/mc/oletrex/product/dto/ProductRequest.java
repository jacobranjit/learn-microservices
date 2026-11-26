package com.mc.oletrex.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Body for POST /api/products. */
public record ProductRequest(
        @NotBlank @Size(max = 255) String skuCode,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String description,
        @NotNull @PositiveOrZero BigDecimal price,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO 4217 code, e.g. USD") String currency,
        /* Defaults to true when omitted. */
        Boolean active,
        @Valid ProductDetailsDto details
) {
}
