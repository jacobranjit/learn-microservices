package com.mc.oletrex.masterdata.dto;

/** Result of one populate step (locations, products or inventory). */
public record StepReport(
        int total,
        int created,
        int updated,
        int failed
) {
}
