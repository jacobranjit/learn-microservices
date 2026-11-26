package com.mc.oletrex.order.client;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Fetches and caches a SERVICE access token from the auth service
 * ({@code POST /api/auth/token}). The token is refreshed once it is within 60 seconds of
 * expiring, or after {@link #invalidate()} (e.g. when a downstream service answered 401).
 */
public class ServiceTokenProvider {

    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private final RestClient authApi;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;

    private String cachedToken;
    private Instant refreshAt = Instant.MIN;

    public ServiceTokenProvider(RestClient authApi, String clientId, String clientSecret, Clock clock) {
        this.authApi = authApi;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    /** @throws DownstreamServiceException if the auth service cannot be reached or rejects the credentials */
    public synchronized String token() {
        if (cachedToken == null || !clock.instant().isBefore(refreshAt)) {
            fetch();
        }
        return cachedToken;
    }

    public synchronized void invalidate() {
        cachedToken = null;
        refreshAt = Instant.MIN;
    }

    private void fetch() {
        TokenResponse response;
        try {
            response = authApi.post().uri("/api/auth/token")
                    .body(Map.of("clientId", clientId, "clientSecret", clientSecret))
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException e) {
            throw new DownstreamServiceException("Auth", e);
        }
        if (response == null || response.accessToken() == null) {
            throw new DownstreamServiceException("Auth", new IllegalStateException("empty token response"));
        }
        cachedToken = response.accessToken();
        refreshAt = clock.instant().plusSeconds(response.expiresIn()).minus(REFRESH_MARGIN);
    }

    private record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }
}
