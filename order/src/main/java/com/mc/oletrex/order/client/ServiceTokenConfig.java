package com.mc.oletrex.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/** Wires the service-token client from the {@code services.auth.*} properties. */
@Configuration
public class ServiceTokenConfig {

    @Bean
    ServiceTokenProvider serviceTokenProvider(@Value("${services.auth.base-url}") String baseUrl,
                                              @Value("${services.auth.client-id}") String clientId,
                                              @Value("${services.auth.client-secret}") String clientSecret,
                                              @Value("${services.http.connect-timeout}") Duration connectTimeout,
                                              @Value("${services.http.read-timeout}") Duration readTimeout) {
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        requestFactory.setReadTimeout(readTimeout);
        RestClient authApi = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        return new ServiceTokenProvider(authApi, clientId, clientSecret, Clock.systemUTC());
    }

    @Bean
    ServiceTokenInterceptor serviceTokenInterceptor(ServiceTokenProvider provider) {
        return new ServiceTokenInterceptor(provider);
    }
}
