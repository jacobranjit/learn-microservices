package com.mc.oletrex.masterdata.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Adds {@code Authorization: Bearer <admin token>} to outgoing calls. On a 401 it discards the
 * cached token and retries once with a fresh one. Must be the last interceptor on the client.
 */
public class BearerInterceptor implements ClientHttpRequestInterceptor {

    private final AdminTokenProvider tokens;

    public BearerInterceptor(AdminTokenProvider tokens) {
        this.tokens = tokens;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().setBearerAuth(tokens.token());
        ClientHttpResponse response = execution.execute(request, body);
        if (response.getStatusCode() != HttpStatus.UNAUTHORIZED) {
            return response;
        }
        response.close();
        tokens.invalidate();
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token());
        return execution.execute(request, body);
    }
}
