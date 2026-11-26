package com.mc.oletrex.masterdata.client;

/** A single API call was rejected (e.g. validation) or failed; the message says why. */
public class ApiCallException extends RuntimeException {

    public ApiCallException(String message) {
        super(message);
    }
}
