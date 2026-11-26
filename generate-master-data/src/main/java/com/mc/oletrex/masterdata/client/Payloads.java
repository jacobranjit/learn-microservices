package com.mc.oletrex.masterdata.client;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request bodies for the product and inventory APIs. Null fields are left out, so the same
 * record serves create (with the key) and update (key null: it goes in the URL instead).
 */
final class Payloads {

    private Payloads() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Location(String code, String name, String type, String addressLine, String city, String state,
                    String postalCode, String country, Integer priority, Boolean active) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Product(String skuCode, String name, String description, BigDecimal price, String currency,
                   Boolean active, ProductDetails details) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ProductDetails(String brand, String category, BigDecimal weight, String weightUnit,
                          BigDecimal length, BigDecimal width, BigDecimal height, String dimensionUnit,
                          String color, String size, String material,
                          List<Attribute> attributes, List<Image> images) {
    }

    record Attribute(String name, String value) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Image(String url, String altText) {
    }

    record Inventory(String skuCode, String locationCode, Integer quantity) {
    }
}
