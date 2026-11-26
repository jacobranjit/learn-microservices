package com.mc.oletrex.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductAttributeDto(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String value
) {
}
