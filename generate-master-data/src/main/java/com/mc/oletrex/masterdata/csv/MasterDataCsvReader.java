package com.mc.oletrex.masterdata.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Reads locations.csv, products.csv and inventory.csv from {@code masterdata.source}
 * (a classpath: or file: folder). Only the CSV structure is checked here; the product and
 * inventory services validate the values themselves when the data is sent.
 */
@Component
public class MasterDataCsvReader {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .get();

    private final ResourceLoader resourceLoader;
    private final String source;

    public MasterDataCsvReader(ResourceLoader resourceLoader, @Value("${masterdata.source}") String source) {
        this.resourceLoader = resourceLoader;
        this.source = source.endsWith("/") ? source : source + "/";
    }

    public String source() {
        return source;
    }

    /** @throws InvalidMasterDataException if a file is missing or a row is malformed */
    public MasterData read() {
        return new MasterData(source,
                read("locations.csv", this::toLocation),
                read("products.csv", this::toProduct),
                read("inventory.csv", this::toInventory),
                read("customers.csv", this::toCustomer));
    }

    private <T> List<T> read(String fileName, Function<CSVRecord, T> mapper) {
        Resource resource = resourceLoader.getResource(source + fileName);
        if (!resource.exists()) {
            throw new InvalidMasterDataException(fileName + " not found in " + source);
        }
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = FORMAT.parse(reader)) {
            List<T> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                try {
                    rows.add(mapper.apply(record));
                } catch (RuntimeException e) {
                    // +1 for the header line
                    throw new InvalidMasterDataException(fileName + " line " + (record.getRecordNumber() + 1)
                            + ": " + e.getMessage(), e);
                }
            }
            return rows;
        } catch (IOException | IllegalArgumentException e) {
            throw new InvalidMasterDataException("Cannot read " + fileName + " from " + source + ": " + e.getMessage(), e);
        }
    }

    private LocationRow toLocation(CSVRecord r) {
        return new LocationRow(text(r, "code"), text(r, "name"), text(r, "type"), text(r, "addressLine"),
                text(r, "city"), text(r, "state"), text(r, "postalCode"), text(r, "country"),
                integer(r, "priority"), bool(r, "active"));
    }

    private ProductRow toProduct(CSVRecord r) {
        return new ProductRow(text(r, "skuCode"), text(r, "name"), text(r, "description"),
                decimal(r, "price"), text(r, "currency"), bool(r, "active"),
                text(r, "brand"), text(r, "category"),
                decimal(r, "weight"), text(r, "weightUnit"),
                decimal(r, "length"), decimal(r, "width"), decimal(r, "height"), text(r, "dimensionUnit"),
                text(r, "color"), text(r, "size"), text(r, "material"),
                split(text(r, "attributes"), part -> {
                    String[] kv = part.split("=", 2);
                    if (kv.length != 2) {
                        throw new IllegalArgumentException("attribute '" + part + "' must look like Name=Value");
                    }
                    return new ProductRow.Attribute(kv[0].trim(), kv[1].trim());
                }),
                split(text(r, "images"), part -> {
                    String[] ua = part.split("\\|", 2);
                    return new ProductRow.Image(ua[0].trim(), ua.length > 1 ? ua[1].trim() : null);
                }));
    }

    private InventoryRow toInventory(CSVRecord r) {
        return new InventoryRow(text(r, "skuCode"), text(r, "locationCode"), integer(r, "quantity"));
    }

    private CustomerRow toCustomer(CSVRecord r) {
        return new CustomerRow(text(r, "email"), text(r, "name"), text(r, "phone"), text(r, "line1"),
                text(r, "line2"), text(r, "city"), text(r, "state"), text(r, "postalCode"), text(r, "country"));
    }

    // --- value helpers: empty cells become null ---

    private static String text(CSVRecord r, String column) {
        if (!r.isMapped(column)) {
            throw new IllegalArgumentException("missing column '" + column + "'");
        }
        String value = r.get(column);
        return value == null || value.isBlank() ? null : value;
    }

    private static BigDecimal decimal(CSVRecord r, String column) {
        String value = text(r, column);
        try {
            return value == null ? null : new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(column + " '" + value + "' is not a number");
        }
    }

    private static Integer integer(CSVRecord r, String column) {
        String value = text(r, column);
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(column + " '" + value + "' is not a whole number");
        }
    }

    private static Boolean bool(CSVRecord r, String column) {
        String value = text(r, column);
        if (value == null) {
            return null;
        }
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(column + " '" + value + "' must be true or false");
        }
        return Boolean.valueOf(value);
    }

    private static <T> List<T> split(String value, Function<String, T> mapper) {
        if (value == null) {
            return List.of();
        }
        return Arrays.stream(value.split(";")).map(String::trim).filter(s -> !s.isEmpty()).map(mapper).toList();
    }
}
