package com.mc.oletrex.masterdata.dto;

import java.util.List;

/**
 * @param success true if every row was created or updated
 * @param errors  the first failures (at most 50); counts in each step include all of them
 */
public record PopulateReport(
        boolean success,
        String source,
        long durationMs,
        StepReport locations,
        StepReport products,
        StepReport inventory,
        List<RowError> errors
) {
}
