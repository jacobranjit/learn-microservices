package com.mc.oletrex.inventory.exception;

public class LocationNotFoundException extends RuntimeException {

    public LocationNotFoundException(String code) {
        super("Location not found: " + code);
    }
}
