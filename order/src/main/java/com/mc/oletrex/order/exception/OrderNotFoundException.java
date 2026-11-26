package com.mc.oletrex.order.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String orderNumber) {
        super("Order not found: " + orderNumber);
    }
}
