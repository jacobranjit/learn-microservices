package com.mc.oletrex.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductImageDto(
        @NotBlank @Size(max = 2048) String url,
        @Size(max = 255) String altText
) {
}
