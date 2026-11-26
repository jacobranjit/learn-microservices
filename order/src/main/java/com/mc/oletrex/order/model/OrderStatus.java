package com.mc.oletrex.order.model;

/**
 * PLACED: saved, products and stock not yet confirmed.
 * CONFIRMED: products verified and stock deducted.
 * CANCELLED: placement failed; see Order.statusReason.
 */
public enum OrderStatus {
    PLACED,
    CONFIRMED,
    CANCELLED
}
