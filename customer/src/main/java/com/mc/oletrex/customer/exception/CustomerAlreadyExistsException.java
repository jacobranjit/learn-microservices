package com.mc.oletrex.customer.exception;

public class CustomerAlreadyExistsException extends RuntimeException {

    public CustomerAlreadyExistsException(String detail) {
        super("Customer already exists: " + detail);
    }
}
