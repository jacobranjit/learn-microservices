package com.mc.oletrex.auth.controller;

import com.mc.oletrex.auth.client.CustomerServiceUnavailableException;
import com.mc.oletrex.auth.dto.TokenResponse;
import com.mc.oletrex.auth.exception.EmailAlreadyRegisteredException;
import com.mc.oletrex.auth.exception.InvalidCredentialsException;
import com.mc.oletrex.auth.security.JwtRolesConverter;
import com.mc.oletrex.auth.security.ProblemDetailAuthHandlers;
import com.mc.oletrex.auth.security.SecurityConfig;
import com.mc.oletrex.auth.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtRolesConverter.class, ProblemDetailAuthHandlers.class})
class AuthControllerTest {

    private static final String VALID_REGISTER = """
            {"email":"alice@example.com","password":"password1","name":"Alice","phone":"+1 555 0100",
             "deliveryAddress":{"line1":"1 Main St","city":"Springfield","postalCode":"12345","country":"US"}}""";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AccountService accountService;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private org.springframework.test.web.servlet.ResultActions register(String body) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void registerReturns201WithTokenAndNoAuthHeader() throws Exception {
        when(accountService.register(any())).thenReturn(TokenResponse.bearer("tok", 3600));

        register(VALID_REGISTER)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("tok"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void registerWithPaddedMixedCaseEmail201() throws Exception {
        when(accountService.register(any())).thenReturn(TokenResponse.bearer("tok", 3600));

        register(VALID_REGISTER.replace("alice@example.com", " Alice@Example.COM "))
                .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<com.mc.oletrex.auth.dto.RegisterRequest> captor =
                org.mockito.ArgumentCaptor.forClass(com.mc.oletrex.auth.dto.RegisterRequest.class);
        org.mockito.Mockito.verify(accountService).register(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().email()).isEqualTo("Alice@Example.COM");
    }

    @Test
    void registerShortPasswordIs400() throws Exception {
        register(VALID_REGISTER.replace("password1", "short"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(accountService);
    }

    @Test
    void registerMissingAddressIs400() throws Exception {
        register("{\"email\":\"alice@example.com\",\"password\":\"password1\",\"name\":\"Alice\",\"phone\":\"+1 555 0100\"}")
                .andExpect(status().isBadRequest());
        verifyNoInteractions(accountService);
    }

    @Test
    void registerDuplicateIs409() throws Exception {
        when(accountService.register(any())).thenThrow(new EmailAlreadyRegisteredException("Email already registered"));

        register(VALID_REGISTER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email already registered"));
    }

    @Test
    void registerCustomerServiceDownIs503() throws Exception {
        when(accountService.register(any())).thenThrow(new CustomerServiceUnavailableException("down", null));

        register(VALID_REGISTER)
                .andExpect(status().isServiceUnavailable())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Service Unavailable"));
    }

    @Test
    void loginReturns200() throws Exception {
        when(accountService.login(any())).thenReturn(TokenResponse.bearer("tok", 3600));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("tok"));
    }

    @Test
    void loginBadCredentialsIs401() throws Exception {
        when(accountService.login(any())).thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void loginBlankFieldsIs400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
