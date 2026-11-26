package com.mc.oletrex.inventory.exception;

public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException(String cursor) {
        super("Invalid cursor: " + cursor);
    }
}
