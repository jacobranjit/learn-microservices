package com.mc.oletrex.masterdata.client;

import com.mc.oletrex.masterdata.csv.CustomerRow;
import com.mc.oletrex.masterdata.csv.InventoryRow;
import com.mc.oletrex.masterdata.csv.LocationRow;
import com.mc.oletrex.masterdata.csv.ProductRow;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Calls the product, inventory, auth and customer APIs. Every save is "create, or reset to these
 * values if it already exists": POST first; on 409 the record exists, so PUT it with the CSV values.
 * Product and inventory calls carry an admin token; customers are registered (or logged in as and
 * updated) with their own credentials.
 */
@Component
public class ServicesClient {

    private final RestClient productApi;
    private final RestClient inventoryApi;
    private final RestClient authApi;
    private final RestClient customerApi;

    @Autowired
    public ServicesClient(@Value("${services.product.base-url}") String productBaseUrl,
                          @Value("${services.inventory.base-url}") String inventoryBaseUrl,
                          @Value("${services.auth.base-url}") String authBaseUrl,
                          @Value("${services.customer.base-url}") String customerBaseUrl,
                          @Value("${masterdata.admin.email}") String adminEmail,
                          @Value("${masterdata.admin.password}") String adminPassword,
                          @Value("${services.http.connect-timeout}") Duration connectTimeout,
                          @Value("${services.http.read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.authApi = RestClient.builder().baseUrl(authBaseUrl).requestFactory(requestFactory).build();
        this.customerApi = RestClient.builder().baseUrl(customerBaseUrl).requestFactory(requestFactory).build();
        BearerInterceptor adminToken = new BearerInterceptor(
                new AdminTokenProvider(authApi, adminEmail, adminPassword, Clock.systemUTC()));
        this.productApi = RestClient.builder().baseUrl(productBaseUrl).requestFactory(requestFactory)
                .requestInterceptor(adminToken).build();
        this.inventoryApi = RestClient.builder().baseUrl(inventoryBaseUrl).requestFactory(requestFactory)
                .requestInterceptor(adminToken).build();
    }

    /** For tests: the four API clients ready-made. */
    ServicesClient(RestClient productApi, RestClient inventoryApi, RestClient authApi, RestClient customerApi) {
        this.productApi = productApi;
        this.inventoryApi = inventoryApi;
        this.authApi = authApi;
        this.customerApi = customerApi;
    }

    /** Fails fast if a service is down, instead of failing thousands of calls one by one. */
    public void checkServicesReachable() {
        ping(authApi, "/.well-known/jwks.json", "Auth");
        ping(customerApi, "/actuator/health", "Customer");
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

    /**
     * Registers the customer; if the email is taken, logs in with the seed password and resets the
     * profile through {@code PUT /api/customers/me}. Neither call carries the admin token.
     */
    public Outcome saveCustomer(CustomerRow row, String password) {
        Payloads.Address address = new Payloads.Address(row.line1(), row.line2(), row.city(), row.state(),
                row.postalCode(), row.country());
        return call(() -> {
            try {
                authApi.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .body(new Payloads.Register(row.email(), password, row.name(), row.phone(), address))
                        .retrieve().toBodilessEntity();
                return Outcome.CREATED;
            } catch (HttpClientErrorException.Conflict alreadyExists) {
                String token = login(row.email(), password);
                customerApi.put().uri("/api/customers/me").contentType(MediaType.APPLICATION_JSON)
                        .headers(h -> h.setBearerAuth(token))
                        .body(new Payloads.CustomerUpdate(row.name(), row.phone(), address))
                        .retrieve().toBodilessEntity();
                return Outcome.UPDATED;
            }
        });
    }

    private String login(String email, String password) {
        try {
            Map<?, ?> response = authApi.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .body(new Payloads.Login(email, password)).retrieve().body(Map.class);
            return String.valueOf(response.get("accessToken"));
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new ApiCallException("customer exists but the seed password no longer works (401 on login)");
        }
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
