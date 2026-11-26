package com.mc.oletrex.masterdata.service;

import com.mc.oletrex.masterdata.client.Outcome;
import com.mc.oletrex.masterdata.client.ServicesClient;
import com.mc.oletrex.masterdata.csv.MasterData;
import com.mc.oletrex.masterdata.csv.MasterDataCsvReader;
import com.mc.oletrex.masterdata.dto.MasterDataSummary;
import com.mc.oletrex.masterdata.dto.PopulateReport;
import com.mc.oletrex.masterdata.dto.RowError;
import com.mc.oletrex.masterdata.dto.StepReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Loads the CSV master data into the other services through their APIs, in dependency order:
 * locations, then products, then stock (inventory checks that the product and location exist),
 * then customers (registered through the auth service).
 * Running it again resets every record listed in the CSVs back to its CSV values.
 */
@Service
public class MasterDataService {

    private static final Logger log = LoggerFactory.getLogger(MasterDataService.class);
    private static final int MAX_REPORTED_ERRORS = 50;

    private final MasterDataCsvReader csvReader;
    private final ServicesClient client;
    private final int parallelism;
    private final String customerPassword;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MasterDataService(MasterDataCsvReader csvReader, ServicesClient client,
                             @Value("${masterdata.parallelism}") int parallelism,
                             @Value("${masterdata.customer-password}") String customerPassword) {
        this.customerPassword = customerPassword;
        this.csvReader = csvReader;
        this.client = client;
        this.parallelism = Math.max(1, parallelism);
    }

    public MasterDataSummary summary() {
        MasterData data = csvReader.read();
        return new MasterDataSummary(data.source(), data.locations().size(), data.products().size(),
                data.inventory().size(), data.customers().size());
    }

    /**
     * @throws PopulationInProgressException if another run has not finished
     * @throws com.mc.oletrex.masterdata.csv.InvalidMasterDataException if a CSV file is missing or malformed
     * @throws com.mc.oletrex.masterdata.client.ServiceUnavailableException if a service is down
     */
    public PopulateReport populate() {
        if (!running.compareAndSet(false, true)) {
            throw new PopulationInProgressException();
        }
        try {
            long start = System.nanoTime();
            MasterData data = csvReader.read();           // fail before calling anything if the CSVs are bad
            client.checkServicesReachable();               // fail fast if a service is down
            List<RowError> errors = Collections.synchronizedList(new ArrayList<>());

            StepReport locations = runStep("locations", data.locations(), r -> r.code(), client::saveLocation, errors);
            StepReport products = runStep("products", data.products(), r -> r.skuCode(), client::saveProduct, errors);
            StepReport inventory = runStep("inventory", data.inventory(),
                    r -> r.skuCode() + "@" + r.locationCode(), client::saveInventory, errors);
            StepReport customers = runStep("customers", data.customers(), r -> r.email(),
                    r -> client.saveCustomer(r, customerPassword), errors);

            long durationMs = (System.nanoTime() - start) / 1_000_000;
            boolean success = locations.failed() + products.failed() + inventory.failed() + customers.failed() == 0;
            PopulateReport report = new PopulateReport(success, data.source(), durationMs, locations, products,
                    inventory, customers, List.copyOf(errors));
            log.info("Master data populated in {} ms: locations {}, products {}, inventory {}, customers {}",
                    durationMs, locations, products, inventory, customers);
            return report;
        } finally {
            running.set(false);
        }
    }

    /** Sends every row of one step, {@code parallelism} calls at a time, and counts the outcomes. */
    private <T> StepReport runStep(String step, List<T> rows, Function<T, String> key,
                                   Function<T, Outcome> save, List<RowError> errors) {
        AtomicInteger created = new AtomicInteger();
        AtomicInteger updated = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        // try-with-resources: close() waits until every submitted call has finished.
        try (ExecutorService pool = Executors.newFixedThreadPool(parallelism)) {
            for (T row : rows) {
                pool.submit(() -> {
                    try {
                        if (save.apply(row) == Outcome.CREATED) {
                            created.incrementAndGet();
                        } else {
                            updated.incrementAndGet();
                        }
                    } catch (RuntimeException e) { // ApiCallException, or anything unexpected for this row
                        failed.incrementAndGet();
                        if (errors.size() < MAX_REPORTED_ERRORS) {
                            errors.add(new RowError(step, key.apply(row), e.getMessage()));
                        }
                    }
                });
            }
        }
        return new StepReport(rows.size(), created.get(), updated.get(), failed.get());
    }
}
