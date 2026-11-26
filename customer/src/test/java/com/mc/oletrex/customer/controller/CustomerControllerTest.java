package com.mc.oletrex.customer.controller;

import com.mc.oletrex.customer.dto.AddressDto;
import com.mc.oletrex.customer.dto.CursorPage;
import com.mc.oletrex.customer.dto.CustomerResponse;
import com.mc.oletrex.customer.dto.CustomerUpdateRequest;
import com.mc.oletrex.customer.exception.CustomerAlreadyExistsException;
import com.mc.oletrex.customer.security.JwtRolesConverter;
import com.mc.oletrex.customer.security.ProblemDetailAuthHandlers;
import com.mc.oletrex.customer.security.SecurityConfig;
import com.mc.oletrex.customer.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Arrays;

import static com.mc.oletrex.customer.security.TestTokens.admin;
import static com.mc.oletrex.customer.security.TestTokens.noRoles;
import static com.mc.oletrex.customer.security.TestTokens.service;
import static com.mc.oletrex.customer.security.TestTokens.user;
import static com.mc.oletrex.customer.security.TestTokens.userWithoutCustomerId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@Import({SecurityConfig.class, JwtRolesConverter.class, ProblemDetailAuthHandlers.class})
class CustomerControllerTest {

    private static final String VALID_BODY = """
            {"authUserId": 5, "name": "Alice", "email": "alice@example.com", "phone": "+1 555 123 4567",
             "deliveryAddress": {"line1": "1 Main St", "city": "Springfield", "postalCode": "12345", "country": "US"}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    private static CustomerResponse response(long id) {
        return new CustomerResponse(id, 5L, "Alice", "alice@example.com", "+1 555 123 4567",
                new AddressDto("1 Main St", null, "Springfield", null, "12345", "US"),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void createAsServiceReturns201() throws Exception {
        when(customerService.create(any())).thenReturn(response(7));

        mockMvc.perform(post("/api/customers").with(service())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/customers/7")))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void createAsUserIs403() throws Exception {
        mockMvc.perform(post("/api/customers").with(user(42))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/problem+json")))
                .andExpect(jsonPath("$.title").value("Forbidden"));
        verifyNoInteractions(customerService);
    }

    @Test
    void createWithoutTokenIs401() throws Exception {
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void createInvalidBodyIs400() throws Exception {
        String body = """
                {"authUserId": 5, "name": "Alice", "email": "alice@example.com", "phone": "abc",
                 "deliveryAddress": {"line1": "1 Main St", "city": "Springfield", "postalCode": "12345", "country": "usa"}}
                """;
        mockMvc.perform(post("/api/customers").with(service())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(customerService);
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        when(customerService.create(any())).thenThrow(new CustomerAlreadyExistsException("alice@example.com"));

        mockMvc.perform(post("/api/customers").with(service())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void meReturnsOwnProfile() throws Exception {
        when(customerService.get(42L)).thenReturn(response(42));

        mockMvc.perform(get("/api/customers/me").with(user(42)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));
        verify(customerService).get(42L);
    }

    @Test
    void meWithoutCustomerIdClaimIs403() throws Exception {
        for (var token : Arrays.asList(admin(), noRoles(), userWithoutCustomerId())) {
            mockMvc.perform(get("/api/customers/me").with(token)).andExpect(status().isForbidden());
        }
        // A USER token lacking customerId passes the role rule, so this proves the controller maps it to 403, not 500.
        mockMvc.perform(put("/api/customers/me").with(userWithoutCustomerId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"phone\":\"+1 555 123 4567\",\"deliveryAddress\":{\"line1\":\"x\",\"city\":\"y\",\"postalCode\":\"1\",\"country\":\"US\"}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
        verifyNoInteractions(customerService);
    }

    @Test
    void updateMeIgnoresEmail() throws Exception {
        when(customerService.update(eq(42L), any())).thenReturn(response(42));
        String body = """
                {"name": "Alice B", "email": "x@y.com", "phone": "+1 555 123 4567",
                 "deliveryAddress": {"line1": "1 Main St", "city": "Springfield", "postalCode": "12345", "country": "US"}}
                """;

        mockMvc.perform(put("/api/customers/me").with(user(42))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        verify(customerService).update(eq(42L), argThat((CustomerUpdateRequest r) -> r.name().equals("Alice B")));
        assertThat(CustomerUpdateRequest.class.getRecordComponents())
                .extracting(c -> c.getName()).doesNotContain("email");
    }

    @Test
    void listAsAdmin200AsUser403() throws Exception {
        when(customerService.list(any(), anyInt())).thenReturn(new CursorPage<>(List.of(response(1)), null, false));

        mockMvc.perform(get("/api/customers").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(1));
        mockMvc.perform(get("/api/customers").with(user(42))).andExpect(status().isForbidden());
    }

    @Test
    void getByIdAsAdmin200AsUser403() throws Exception {
        when(customerService.get(9L)).thenReturn(response(9));

        mockMvc.perform(get("/api/customers/9").with(admin())).andExpect(status().isOk());
        mockMvc.perform(get("/api/customers/9").with(user(9))).andExpect(status().isForbidden());
        verify(customerService, never()).get(eq(10L));
    }

    @Test
    void deleteAsService204AsAdmin403() throws Exception {
        mockMvc.perform(delete("/api/customers/9").with(service())).andExpect(status().isNoContent());
        verify(customerService).delete(9L);
        mockMvc.perform(delete("/api/customers/9").with(admin())).andExpect(status().isForbidden());
    }

    @Test
    void healthAndSwaggerArePublic() throws Exception {
        for (String path : List.of("/v3/api-docs", "/actuator/health")) {
            MvcResult result = mockMvc.perform(get(path)).andReturn();
            // 404 is expected in the MVC slice; the point is that security lets the request through.
            assertThat(result.getResponse().getStatus()).as(path).isNotEqualTo(401);
        }
    }
}
