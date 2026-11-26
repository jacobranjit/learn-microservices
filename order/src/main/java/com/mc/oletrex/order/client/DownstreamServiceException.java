package com.mc.oletrex.order.client;

/** A service the order depends on is unreachable, timed out, or answered unexpectedly. */
public class DownstreamServiceException extends RuntimeException {

    public DownstreamServiceException(String service, Throwable cause) {
        super(service + " service is unavailable", cause);
    }
}
