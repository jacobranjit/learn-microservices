package com.mc.oletrex.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrderRequest(
        @NotNull @Valid AddressDto shippingAddress,
        @NotNull @Valid AddressDto billingAddress,
        @NotEmpty List<@Valid OrderLineItemRequest> lineItems
) {
}
