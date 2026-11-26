package com.mc.oletrex.auth.exception;

public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException(String cursor) {
        super("Invalid cursor: " + cursor);
    }
}
