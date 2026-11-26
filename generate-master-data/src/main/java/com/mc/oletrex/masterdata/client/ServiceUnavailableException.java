package com.mc.oletrex.masterdata.client;

/** A service to populate cannot be reached, so populating is not attempted. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
