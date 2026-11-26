package com.mc.oletrex.masterdata.dto;

/**
 * @param step    locations, products or inventory
 * @param key     location code or SKU of the row that failed
 * @param message why the API call failed
 */
public record RowError(
        String step,
        String key,
        String message
) {
}
