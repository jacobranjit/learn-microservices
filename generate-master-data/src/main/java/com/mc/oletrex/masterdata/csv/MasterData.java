package com.mc.oletrex.masterdata.csv;

import java.util.List;

/** Everything read from the three CSV files. */
public record MasterData(
        String source,
        List<LocationRow> locations,
        List<ProductRow> products,
        List<InventoryRow> inventory
) {
}
