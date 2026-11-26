package com.mc.oletrex.order.client;

import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** Builds RestClients with timeouts, so a slow or dead service cannot hang order placement. */
final class RestClients {

    private RestClients() {
    }

    static RestClient create(String baseUrl, Duration connectTimeout, Duration readTimeout,
                              ClientHttpRequestInterceptor interceptor) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(interceptor)
                .build();
    }
}
