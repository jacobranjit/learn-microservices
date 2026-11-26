package com.mc.oletrex.product.exception;

public class ProductAlreadyExistsException extends RuntimeException {

    public ProductAlreadyExistsException(String skuCode) {
        super("Product already exists: " + skuCode);
    }
}
