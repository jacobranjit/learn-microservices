package com.mc.oletrex.inventory.exception;

public class LocationAlreadyExistsException extends RuntimeException {

    public LocationAlreadyExistsException(String code) {
        super("Location already exists: " + code);
    }
}
