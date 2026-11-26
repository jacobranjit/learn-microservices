package com.mc.oletrex.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Calls the inventory service: POST /api/inventory/deductions. */
@Component
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient(@Value("${services.inventory.base-url}") String baseUrl,
                           @Value("${services.http.connect-timeout}") Duration connectTimeout,
                           @Value("${services.http.read-timeout}") Duration readTimeout) {
        this.restClient = RestClients.create(baseUrl, connectTimeout, readTimeout);
    }

    /**
     * Deducts all items in one all-or-nothing call; inventory takes them all from a single location.
     *
     * @return the code of the location that supplied the items
     *
     * @throws InsufficientStockException  if the items can't be supplied (nothing was deducted)
     * @throws DownstreamServiceException  if the inventory service cannot be reached or fails
     */
    public String deductStock(List<StockItem> items) {
        try {
            StockDeductionResult result = restClient.post()
                    .uri("/api/inventory/deductions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("items", items))
                    .retrieve()
                    .body(StockDeductionResult.class);
            if (result == null || result.locationCode() == null) {
                throw new RestClientException("Inventory returned no location");
            }
            return result.locationCode();
        } catch (HttpClientErrorException.Conflict e) {
            Map<?, ?> problem = e.getResponseBodyAs(Map.class);
            Object detail = problem == null ? null : problem.get("detail");
            throw new InsufficientStockException(detail != null ? detail.toString() : "Insufficient stock");
        } catch (RestClientException e) {
            throw new DownstreamServiceException("Inventory", e);
        }
    }
}
