package com.mc.oletrex.inventory.exception;

/** Stock was sent for a location that does not exist or is inactive. */
public class InvalidLocationException extends RuntimeException {

    public InvalidLocationException(String message) {
        super(message);
    }
}
