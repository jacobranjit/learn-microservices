package com.mc.oletrex.auth.client;

import com.mc.oletrex.auth.dto.AddressDto;
import com.mc.oletrex.auth.dto.RegisterRequest;
import com.mc.oletrex.auth.dto.TokenResponse;
import com.mc.oletrex.auth.exception.EmailAlreadyRegisteredException;
import com.mc.oletrex.auth.token.JwtIssuer;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerClientTest {

    private HttpServer server;
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> authHeader = new AtomicReference<>();
    private int status;
    private String responseBody;
    private CustomerClient client;

    private static final RegisterRequest REQUEST = new RegisterRequest("alice@example.com", "password1", "Alice",
            "+1 555 0100", new AddressDto("1 Main St", null, "Springfield", null, "12345", "US"));

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = responseBody == null ? new byte[0] : responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
        JwtIssuer issuer = mock(JwtIssuer.class);
        when(issuer.issueServiceToken("auth-service")).thenReturn(TokenResponse.bearer("svc-token", 3600));
        client = new CustomerClient("http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(2), Duration.ofSeconds(5), issuer);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void createReturnsIdAndSendsBearerToken() {
        status = 201;
        responseBody = "{\"id\":7,\"name\":\"Alice\"}";

        assertThat(client.createCustomer(5L, REQUEST)).isEqualTo(7L);
        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo("/api/customers");
        assertThat(authHeader.get()).isEqualTo("Bearer svc-token");
    }

    @Test
    void conflictMapsToEmailAlreadyRegistered() {
        status = 409;
        responseBody = "{}";
        assertThatThrownBy(() -> client.createCustomer(5L, REQUEST)).isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void serverErrorMapsToUnavailable() {
        status = 500;
        responseBody = "{}";
        assertThatThrownBy(() -> client.createCustomer(5L, REQUEST)).isInstanceOf(CustomerServiceUnavailableException.class);
    }

    @Test
    void connectionRefusedMapsToUnavailable() {
        server.stop(0);
        assertThatThrownBy(() -> client.createCustomer(5L, REQUEST)).isInstanceOf(CustomerServiceUnavailableException.class);
    }

    @Test
    void responseWithoutIdMapsToUnavailable() {
        status = 201;
        responseBody = "{\"name\":\"Alice\"}";
        assertThatThrownBy(() -> client.createCustomer(5L, REQUEST)).isInstanceOf(CustomerServiceUnavailableException.class);
    }

    @Test
    void deleteSendsDeleteWithBearerToken() {
        status = 204;
        responseBody = null;

        client.deleteCustomer(7L);

        assertThat(method.get()).isEqualTo("DELETE");
        assertThat(path.get()).isEqualTo("/api/customers/7");
        assertThat(authHeader.get()).isEqualTo("Bearer svc-token");
    }
}
