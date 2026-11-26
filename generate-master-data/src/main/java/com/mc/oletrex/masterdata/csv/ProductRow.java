package com.mc.oletrex.masterdata.csv;

import java.math.BigDecimal;
import java.util.List;

/** One row of products.csv. attributes: "Name=Value;Name=Value". images: "url|alt text;url|alt text". */
public record ProductRow(
        String skuCode,
        String name,
        String description,
        BigDecimal price,
        String currency,
        Boolean active,
        String brand,
        String category,
        BigDecimal weight,
        String weightUnit,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        String dimensionUnit,
        String color,
        String size,
        String material,
        List<Attribute> attributes,
        List<Image> images
) {
    public record Attribute(String name, String value) {
    }

    public record Image(String url, String altText) {
    }
}
