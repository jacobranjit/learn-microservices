package com.mc.oletrex.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** The price is not sent by the client: it is taken from the product service. */
public record OrderLineItemRequest(
        @NotBlank String skuCode,
        @NotNull @Positive Integer quantity
) {
}
