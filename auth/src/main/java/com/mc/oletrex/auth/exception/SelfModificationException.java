package com.mc.oletrex.auth.exception;

/** An admin tried to demote or disable their own account. */
public class SelfModificationException extends RuntimeException {

    public SelfModificationException() {
        super("You cannot change your own role or disable yourself");
    }
}
