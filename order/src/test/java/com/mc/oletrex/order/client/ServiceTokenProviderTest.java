package com.mc.oletrex.order.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ServiceTokenProviderTest {

    private static final String URL = "http://auth.test/api/auth/token";

    private MutableClock clock;
    private MockRestServiceServer server;
    private ServiceTokenProvider provider;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth.test");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new ServiceTokenProvider(builder.build(), "inventory-service", "s3cret", clock);
    }

    private void expectToken(String token, int expiresIn) {
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.clientId").value("inventory-service"))
                .andExpect(jsonPath("$.clientSecret").value("s3cret"))
                .andRespond(withSuccess("""
                        {"accessToken":"%s","tokenType":"Bearer","expiresIn":%d}""".formatted(token, expiresIn),
                        MediaType.APPLICATION_JSON));
    }

    @Test
    void fetchesOnceAndCachesWithinLifetime() {
        expectToken("t1", 3600);

        assertThat(provider.token()).isEqualTo("t1");
        clock.advance(Duration.ofSeconds(100));
        assertThat(provider.token()).isEqualTo("t1");

        server.verify();
    }

    @Test
    void refreshesSixtySecondsBeforeExpiry() {
        expectToken("t1", 3600);
        assertThat(provider.token()).isEqualTo("t1");

        clock.advance(Duration.ofSeconds(3600 - 61));
        assertThat(provider.token()).isEqualTo("t1");

        server.reset();
        expectToken("t2", 3600);
        clock.advance(Duration.ofSeconds(2)); // now = expiresIn - 59s
        assertThat(provider.token()).isEqualTo("t2");
        server.verify();
    }

    @Test
    void invalidateForcesNewFetch() {
        expectToken("t1", 3600);
        assertThat(provider.token()).isEqualTo("t1");

        provider.invalidate();
        server.reset();
        expectToken("t2", 3600);
        assertThat(provider.token()).isEqualTo("t2");
        server.verify();
    }

    @Test
    void authRejectingCredentialsThrowsDownstreamServiceException() {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(provider::token).isInstanceOf(DownstreamServiceException.class);
    }

    @Test
    void authServerErrorThrowsDownstreamServiceException() {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(provider::token).isInstanceOf(DownstreamServiceException.class);
    }

    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
