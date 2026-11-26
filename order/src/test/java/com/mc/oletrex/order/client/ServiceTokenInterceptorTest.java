package com.mc.oletrex.order.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ServiceTokenInterceptorTest {

    private ServiceTokenProvider provider;
    private MockRestServiceServer server;
    private RestClient client;

    @BeforeEach
    void setUp() {
        provider = mock(ServiceTokenProvider.class);
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://down.test")
                .requestInterceptor(new ServiceTokenInterceptor(provider));
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build();
    }

    @Test
    void addsBearerToken() {
        when(provider.token()).thenReturn("t1");
        server.expect(once(), requestTo("http://down.test/x"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer t1"))
                .andRespond(withSuccess());

        client.get().uri("/x").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void on401InvalidatesAndRetriesOnceWithNewToken() {
        when(provider.token()).thenReturn("old", "new");
        server.expect(once(), requestTo("http://down.test/x"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer old"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(once(), requestTo("http://down.test/x"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer new"))
                .andRespond(withSuccess());

        var response = client.get().uri("/x").retrieve().toBodilessEntity();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(provider).invalidate();
        server.verify();
    }

    @Test
    void twice401ReturnsSecond401WithoutLooping() {
        when(provider.token()).thenReturn("old", "new");
        server.expect(once(), requestTo("http://down.test/x")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(once(), requestTo("http://down.test/x")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.get().uri("/x").retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);

        verify(provider, times(1)).invalidate();
        verify(provider, times(2)).token();
        server.verify();
    }
}
