package com.mc.oletrex.inventory.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

/** Calls the product service: GET /api/products/{skuCode}. */
@Component
public class ProductClient {

    private final RestClient restClient;

    public ProductClient(@Value("${services.product.base-url}") String baseUrl,
                         @Value("${services.http.connect-timeout}") Duration connectTimeout,
                         @Value("${services.http.read-timeout}") Duration readTimeout,
                         ServiceTokenInterceptor serviceTokenInterceptor) {
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory)
                .requestInterceptor(serviceTokenInterceptor).build();
    }

    /**
     * @return true if the product service has a product with this SKU (active or not)
     * @throws DownstreamServiceException if the product service cannot be reached or fails
     */
    public boolean productExists(String skuCode) {
        try {
            restClient.get().uri("/api/products/{skuCode}", skuCode).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (RestClientException e) {
            throw new DownstreamServiceException("Product", e);
        }
    }
}
