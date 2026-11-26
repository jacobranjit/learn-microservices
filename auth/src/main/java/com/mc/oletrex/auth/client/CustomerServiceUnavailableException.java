package com.mc.oletrex.auth.client;

public class CustomerServiceUnavailableException extends RuntimeException {

    public CustomerServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
