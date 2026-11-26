package com.mc.oletrex.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OrderRequest(
        @NotEmpty List<@Valid OrderLineItemRequest> lineItems
) {
}
