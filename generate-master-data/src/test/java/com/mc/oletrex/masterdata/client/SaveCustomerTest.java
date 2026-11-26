package com.mc.oletrex.masterdata.client;

import com.mc.oletrex.masterdata.csv.CustomerRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SaveCustomerTest {

    private static final CustomerRow ROW = new CustomerRow("customer001@example.com", "Tessa Lockhart",
            "+1 555 0352", "34 Sunset Rd", "Apt 4", "Milton", "MA", "02186", "US");

    private MockRestServiceServer authServer;
    private MockRestServiceServer customerServer;
    private ServicesClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder auth = RestClient.builder().baseUrl("http://auth.test");
        RestClient.Builder customer = RestClient.builder().baseUrl("http://customer.test");
        RestClient.Builder unused = RestClient.builder().baseUrl("http://unused.test");
        authServer = MockRestServiceServer.bindTo(auth).build();
        customerServer = MockRestServiceServer.bindTo(customer).build();
        client = new ServicesClient(unused.build(), unused.build(), auth.build(), customer.build());
    }

    @Test
    void registersNewCustomerWithoutAnAuthorizationHeader() {
        authServer.expect(requestTo("http://auth.test/api/auth/register"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andExpect(jsonPath("$.email").value("customer001@example.com"))
                .andExpect(jsonPath("$.password").value("Pw123456!"))
                .andExpect(jsonPath("$.deliveryAddress.line1").value("34 Sunset Rd"))
                .andExpect(jsonPath("$.deliveryAddress.line2").value("Apt 4"))
                .andExpect(jsonPath("$.deliveryAddress.postalCode").value("02186"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"accessToken\":\"t\",\"tokenType\":\"Bearer\",\"expiresIn\":3600}"));

        assertThat(client.saveCustomer(ROW, "Pw123456!")).isEqualTo(Outcome.CREATED);
        authServer.verify();
    }

    @Test
    void existingCustomerLogsInAndUpdatesProfileWithTheirOwnToken() {
        authServer.expect(requestTo("http://auth.test/api/auth/register"))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        authServer.expect(requestTo("http://auth.test/api/auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andExpect(jsonPath("$.email").value("customer001@example.com"))
                .andRespond(withSuccess("{\"accessToken\":\"user-token\",\"tokenType\":\"Bearer\",\"expiresIn\":3600}",
                        MediaType.APPLICATION_JSON));
        customerServer.expect(requestTo("http://customer.test/api/customers/me"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
                .andExpect(jsonPath("$.name").value("Tessa Lockhart"))
                .andExpect(jsonPath("$.phone").value("+1 555 0352"))
                .andExpect(jsonPath("$.deliveryAddress.city").value("Milton"))
                .andRespond(withSuccess());

        assertThat(client.saveCustomer(ROW, "Pw123456!")).isEqualTo(Outcome.UPDATED);
        authServer.verify();
        customerServer.verify();
    }

    @Test
    void seedPasswordNoLongerWorkingIsAFailedRow() {
        authServer.expect(requestTo("http://auth.test/api/auth/register"))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        authServer.expect(requestTo("http://auth.test/api/auth/login"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.saveCustomer(ROW, "Pw123456!"))
                .isInstanceOf(ApiCallException.class)
                .hasMessageContaining("seed password no longer works");
        customerServer.verify();
    }
}
