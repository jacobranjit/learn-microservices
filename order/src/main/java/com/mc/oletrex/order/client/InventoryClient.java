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
     * Deducts all items in one all-or-nothing call.
     *
     * @throws InsufficientStockException  if any SKU is short (nothing was deducted)
     * @throws DownstreamServiceException  if the inventory service cannot be reached or fails
     */
    public void deductStock(List<StockItem> items) {
        try {
            restClient.post()
                    .uri("/api/inventory/deductions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("items", items))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            Map<?, ?> problem = e.getResponseBodyAs(Map.class);
            Object detail = problem == null ? null : problem.get("detail");
            throw new InsufficientStockException(detail != null ? detail.toString() : "Insufficient stock");
        } catch (RestClientException e) {
            throw new DownstreamServiceException("Inventory", e);
        }
    }
}
