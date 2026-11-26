package com.mc.oletrex.order.dto;

/**
 * Outcome of placing an order. The order is always saved; outcome says how placement ended.
 */
public record OrderPlacementResult(
        OrderResponse order,
        Outcome outcome
) {
    public enum Outcome {
        /** Products verified, stock deducted: status CONFIRMED. */
        CONFIRMED,
        /** Unknown/inactive product, mixed currencies or insufficient stock: status CANCELLED. */
        REJECTED,
        /** Product or inventory service unreachable: status CANCELLED. */
        SERVICE_UNAVAILABLE
    }
}
