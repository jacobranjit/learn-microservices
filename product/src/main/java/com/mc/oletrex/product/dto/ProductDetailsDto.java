package com.mc.oletrex.product.dto;

import com.mc.oletrex.product.model.DimensionUnit;
import com.mc.oletrex.product.model.WeightUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Used for both requests and responses. Every field is optional. */
public record ProductDetailsDto(
        @Size(max = 255) String brand,
        @Size(max = 255) String category,
        @Positive BigDecimal weight,
        WeightUnit weightUnit,
        @Positive BigDecimal length,
        @Positive BigDecimal width,
        @Positive BigDecimal height,
        DimensionUnit dimensionUnit,
        @Size(max = 255) String color,
        @Size(max = 255) String size,
        @Size(max = 255) String material,
        List<@Valid ProductAttributeDto> attributes,
        /* In display order; the first image is the main image. */
        List<@Valid ProductImageDto> images
) {
}
