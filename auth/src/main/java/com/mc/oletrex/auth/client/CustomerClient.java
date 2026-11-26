package com.mc.oletrex.auth.client;

import com.mc.oletrex.auth.dto.RegisterRequest;
import com.mc.oletrex.auth.exception.EmailAlreadyRegisteredException;
import com.mc.oletrex.auth.token.JwtIssuer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Calls the customer service (POST /api/customers, DELETE /api/customers/{id}) as the SERVICE role. */
@Component
public class CustomerClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerClient.class);
    private static final String CLIENT_ID = "auth-service";
    /** Renew the cached service token this long before it expires. */
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final JwtIssuer jwtIssuer;

    private String cachedToken;
    private Instant cachedUntil = Instant.EPOCH;

    public CustomerClient(@Value("${services.customer.base-url}") String baseUrl,
                          @Value("${services.http.connect-timeout}") Duration connectTimeout,
                          @Value("${services.http.read-timeout}") Duration readTimeout,
                          JwtIssuer jwtIssuer) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.jwtIssuer = jwtIssuer;
    }

    /** @return the id of the new customer */
    public long createCustomer(long authUserId, RegisterRequest r) {
        Map<String, Object> body = Map.of(
                "authUserId", authUserId,
                "name", r.name(),
                "email", r.email(),
                "phone", r.phone(),
                "deliveryAddress", r.deliveryAddress());
        try {
            CreatedCustomer created = restClient.post()
                    .uri("/api/customers")
                    .header("Authorization", "Bearer " + serviceToken())
                    .body(body)
                    .retrieve()
                    .body(CreatedCustomer.class);
            if (created == null || created.id() == null) {
                throw new CustomerServiceUnavailableException("Customer service returned no customer id", null);
            }
            return created.id();
        } catch (HttpClientErrorException.Conflict e) {
            throw new EmailAlreadyRegisteredException("Email already registered");
        } catch (RestClientException e) {
            log.warn("Customer service create failed", e);
            throw new CustomerServiceUnavailableException("Customer service is unavailable", e);
        }
    }

    public void deleteCustomer(long customerId) {
        try {
            restClient.delete()
                    .uri("/api/customers/{id}", customerId)
                    .header("Authorization", "Bearer " + serviceToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Customer service delete failed", e);
            throw new CustomerServiceUnavailableException("Customer service is unavailable", e);
        }
    }

    private synchronized String serviceToken() {
        if (cachedToken == null || Instant.now().isAfter(cachedUntil)) {
            var token = jwtIssuer.issueServiceToken(CLIENT_ID);
            cachedToken = token.accessToken();
            cachedUntil = Instant.now().plusSeconds(token.expiresIn()).minus(REFRESH_MARGIN);
        }
        return cachedToken;
    }

    private record CreatedCustomer(Long id) {
    }
}
