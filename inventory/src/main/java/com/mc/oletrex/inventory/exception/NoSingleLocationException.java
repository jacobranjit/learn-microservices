package com.mc.oletrex.inventory.exception;

/** Enough stock exists in total, but no single active location can supply every item. */
public class NoSingleLocationException extends RuntimeException {

    public NoSingleLocationException(String items) {
        super("No single location can supply the whole order: " + items);
    }
}
