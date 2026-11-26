package com.mc.oletrex.inventory.exception;

/** Stock was sent for a SKU that the product service does not know. */
public class UnknownProductException extends RuntimeException {

    public UnknownProductException(String skuCode) {
        super("No product with SKU " + skuCode + " exists in the product service");
    }
}
