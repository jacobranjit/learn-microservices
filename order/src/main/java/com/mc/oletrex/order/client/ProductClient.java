package com.mc.oletrex.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Optional;

/** Calls the product service: GET /api/products/{skuCode}. */
@Component
public class ProductClient {

    private final RestClient restClient;

    public ProductClient(@Value("${services.product.base-url}") String baseUrl,
                         @Value("${services.http.connect-timeout}") Duration connectTimeout,
                         @Value("${services.http.read-timeout}") Duration readTimeout,
                         ServiceTokenInterceptor serviceTokenInterceptor) {
        this.restClient = RestClients.create(baseUrl, connectTimeout, readTimeout, serviceTokenInterceptor);
    }

    /**
     * @return the product, or empty if the product service has no product with this SKU
     * @throws DownstreamServiceException if the product service cannot be reached or fails
     */
    public Optional<ProductInfo> findProduct(String skuCode) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/products/{skuCode}", skuCode)
                    .retrieve()
                    .body(ProductInfo.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new DownstreamServiceException("Product", e);
        }
    }
}
