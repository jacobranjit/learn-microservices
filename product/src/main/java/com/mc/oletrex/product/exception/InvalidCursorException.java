package com.mc.oletrex.product.exception;

public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException(String cursor) {
        super("Invalid cursor: " + cursor);
    }
}
