package com.mc.oletrex.inventory.exception;

public class InventoryAlreadyExistsException extends RuntimeException {

    public InventoryAlreadyExistsException(String skuCode) {
        super("Inventory already exists for SKU: " + skuCode);
    }
}
