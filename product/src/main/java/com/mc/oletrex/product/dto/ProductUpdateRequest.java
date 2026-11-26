package com.mc.oletrex.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body for PUT /api/products/{skuCode}: replaces the whole product.
 * The SKU is not part of the body because orders and inventory refer to it, so it never changes.
 * Omitting "details" removes the product's details.
 */
public record ProductUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String description,
        @NotNull @PositiveOrZero BigDecimal price,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO 4217 code, e.g. USD") String currency,
        @NotNull Boolean active,
        @Valid ProductDetailsDto details
) {
}
