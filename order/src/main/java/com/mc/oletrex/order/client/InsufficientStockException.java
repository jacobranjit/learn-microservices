package com.mc.oletrex.order.client;

/** Inventory refused the deduction; the message is inventory's explanation. */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
