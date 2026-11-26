package com.mc.oletrex.order.exception;

public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException(String cursor) {
        super("Invalid cursor: " + cursor);
    }
}
