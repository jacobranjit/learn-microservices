package com.mc.oletrex.masterdata.client;

import com.mc.oletrex.masterdata.csv.InventoryRow;
import com.mc.oletrex.masterdata.csv.LocationRow;
import com.mc.oletrex.masterdata.csv.ProductRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Calls the product and inventory APIs. Every save is "create, or reset to these values if it
 * already exists": POST first; on 409 the record exists, so PUT it with the CSV values.
 */
@Component
public class ServicesClient {

    private final RestClient productApi;
    private final RestClient inventoryApi;

    public ServicesClient(@Value("${services.product.base-url}") String productBaseUrl,
                          @Value("${services.inventory.base-url}") String inventoryBaseUrl,
                          @Value("${services.http.connect-timeout}") Duration connectTimeout,
                          @Value("${services.http.read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.productApi = RestClient.builder().baseUrl(productBaseUrl).requestFactory(requestFactory).build();
        this.inventoryApi = RestClient.builder().baseUrl(inventoryBaseUrl).requestFactory(requestFactory).build();
    }

    /** Fails fast if either service is down, instead of failing thousands of calls one by one. */
    public void checkServicesReachable() {
        ping(productApi, "/api/products?limit=1", "Product");
        ping(inventoryApi, "/api/locations?limit=1", "Inventory");
    }

    public Outcome saveLocation(LocationRow row) {
        Payloads.Location body = new Payloads.Location(row.code(), row.name(), row.type(), row.addressLine(),
                row.city(), row.state(), row.postalCode(), row.country(), row.priority(), row.active());
        return createOrReset(
                () -> post(inventoryApi, "/api/locations", body),
                () -> put(inventoryApi, "/api/locations/{key}", row.code(),
                        new Payloads.Location(null, row.name(), row.type(), row.addressLine(), row.city(),
                                row.state(), row.postalCode(), row.country(), row.priority(),
                                row.active() == null || row.active())));
    }

    public Outcome saveProduct(ProductRow row) {
        Payloads.ProductDetails details = new Payloads.ProductDetails(row.brand(), row.category(), row.weight(),
                row.weightUnit(), row.length(), row.width(), row.height(), row.dimensionUnit(),
                row.color(), row.size(), row.material(),
                row.attributes().stream().map(a -> new Payloads.Attribute(a.name(), a.value())).toList(),
                row.images().stream().map(i -> new Payloads.Image(i.url(), i.altText())).toList());
        boolean active = row.active() == null || row.active();
        return createOrReset(
                () -> post(productApi, "/api/products",
                        new Payloads.Product(row.skuCode(), row.name(), row.description(), row.price(),
                                row.currency(), active, details)),
                () -> put(productApi, "/api/products/{key}", row.skuCode(),
                        new Payloads.Product(null, row.name(), row.description(), row.price(),
                                row.currency(), active, details)));
    }

    /** Inventory's POST already creates or updates: 201 created, 200 updated. */
    public Outcome saveInventory(InventoryRow row) {
        return call(() -> {
            ResponseEntity<Void> response = post(inventoryApi, "/api/inventory",
                    new Payloads.Inventory(row.skuCode(), row.locationCode(), row.quantity()));
            return response.getStatusCode().isSameCodeAs(HttpStatus.CREATED) ? Outcome.CREATED : Outcome.UPDATED;
        });
    }

    // --- helpers ---

    private static Outcome createOrReset(Supplier<ResponseEntity<Void>> create, Runnable reset) {
        return call(() -> {
            try {
                create.get();
                return Outcome.CREATED;
            } catch (HttpClientErrorException.Conflict alreadyExists) {
                reset.run();
                return Outcome.UPDATED;
            }
        });
    }

    /** Runs one API call, turning failures into ApiCallException with the service's explanation. */
    private static Outcome call(Supplier<Outcome> apiCall) {
        try {
            return apiCall.get();
        } catch (HttpStatusCodeException e) {
            throw new ApiCallException(e.getStatusCode().value() + " " + problemDetail(e));
        } catch (RestClientException e) {
            throw new ApiCallException(e.getMessage());
        }
    }

    private static ResponseEntity<Void> post(RestClient api, String path, Object body) {
        return api.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
    }

    private static void put(RestClient api, String path, String key, Object body) {
        api.put().uri(path, key).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
    }

    private static void ping(RestClient api, String path, String service) {
        try {
            api.get().uri(path).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new ServiceUnavailableException(service + " service is not reachable: " + e.getMessage(), e);
        }
    }

    private static String problemDetail(HttpStatusCodeException e) {
        try {
            Map<?, ?> problem = e.getResponseBodyAs(Map.class);
            if (problem != null && problem.get("detail") != null) {
                return String.valueOf(problem.get("detail"));
            }
        } catch (RuntimeException ignored) {
            // body was not JSON
        }
        return e.getStatusText();
    }
}
