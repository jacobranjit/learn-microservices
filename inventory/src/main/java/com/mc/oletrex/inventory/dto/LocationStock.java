package com.mc.oletrex.inventory.dto;

public record LocationStock(
        String locationCode,
        Integer quantity,
        /* Inactive locations never supply orders. */
        boolean locationActive
) {
}
