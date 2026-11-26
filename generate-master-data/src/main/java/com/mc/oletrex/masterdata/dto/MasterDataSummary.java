package com.mc.oletrex.masterdata.dto;

/** Row counts in the CSV files that populate would load. */
public record MasterDataSummary(
        String source,
        int locations,
        int products,
        int inventory,
        int customers
) {
}
