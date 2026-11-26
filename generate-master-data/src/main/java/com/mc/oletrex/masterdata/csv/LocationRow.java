package com.mc.oletrex.masterdata.csv;

/** One row of locations.csv. */
public record LocationRow(
        String code,
        String name,
        String type,
        String addressLine,
        String city,
        String state,
        String postalCode,
        String country,
        Integer priority,
        Boolean active
) {
}
