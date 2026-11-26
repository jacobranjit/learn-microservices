package com.mc.oletrex.inventory.exception;

public class InventoryNotFoundException extends RuntimeException {

    public InventoryNotFoundException(String skuCode) {
        super("Inventory not found for SKU: " + skuCode);
    }
}
