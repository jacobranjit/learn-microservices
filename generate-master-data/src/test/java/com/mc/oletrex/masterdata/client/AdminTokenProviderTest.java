package com.mc.oletrex.masterdata.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AdminTokenProviderTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    private MockRestServiceServer server;
    private AdminTokenProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth.test");
        server = MockRestServiceServer.bindTo(builder).build();
        Clock clock = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        provider = new AdminTokenProvider(builder.build(), "admin@x.test", "pw", clock);
    }

    private void expectLogin(String token) {
        server.expect(once(), requestTo("http://auth.test/api/auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.email").value("admin@x.test"))
                .andExpect(jsonPath("$.password").value("pw"))
                .andRespond(withSuccess("{\"accessToken\":\"" + token + "\",\"tokenType\":\"Bearer\",\"expiresIn\":3600}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    void cachesUntilSixtySecondsBeforeExpiryThenRefreshes() {
        expectLogin("a");
        assertThat(provider.token()).isEqualTo("a");
        now.set(now.get().plusSeconds(3500));
        assertThat(provider.token()).isEqualTo("a");

        server.verify();
        server.reset();
        expectLogin("b");
        now.set(now.get().plusSeconds(100));
        assertThat(provider.token()).isEqualTo("b");
        server.verify();
    }

    @Test
    void invalidateForcesANewLogin() {
        expectLogin("a");
        provider.token();
        provider.invalidate();
        server.reset();
        expectLogin("b");
        assertThat(provider.token()).isEqualTo("b");
    }

    @Test
    void rejectedAdminCredentialsSurfaceAsServiceUnavailable() {
        server.expect(requestTo("http://auth.test/api/auth/login")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(provider::token).isInstanceOf(ServiceUnavailableException.class);
    }
}
