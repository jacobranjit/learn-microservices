package com.mc.oletrex.masterdata.client;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Logs in as the admin account ({@code POST /api/auth/login}) and caches the access token. It is
 * refreshed once it is within 60 seconds of expiring, or after {@link #invalidate()} (e.g. when a
 * service answered 401).
 */
public class AdminTokenProvider {

    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private final RestClient authApi;
    private final String email;
    private final String password;
    private final Clock clock;

    private String cachedToken;
    private Instant refreshAt = Instant.MIN;

    public AdminTokenProvider(RestClient authApi, String email, String password, Clock clock) {
        this.authApi = authApi;
        this.email = email;
        this.password = password;
        this.clock = clock;
    }

    /** @throws ServiceUnavailableException if the auth service cannot be reached or rejects the admin login */
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
            response = authApi.post().uri("/api/auth/login")
                    .body(new Payloads.Login(email, password))
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException e) {
            throw new ServiceUnavailableException("Cannot log in as admin '" + email + "': " + e.getMessage(), e);
        }
        if (response == null || response.accessToken() == null) {
            throw new ServiceUnavailableException("Admin login returned no token",
                    new IllegalStateException("empty token response"));
        }
        cachedToken = response.accessToken();
        refreshAt = clock.instant().plusSeconds(response.expiresIn()).minus(REFRESH_MARGIN);
    }

    private record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }
}
