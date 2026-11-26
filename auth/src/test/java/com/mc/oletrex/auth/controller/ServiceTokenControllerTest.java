package com.mc.oletrex.auth.controller;

import com.mc.oletrex.auth.dto.TokenResponse;
import com.mc.oletrex.auth.security.JwtRolesConverter;
import com.mc.oletrex.auth.security.ProblemDetailAuthHandlers;
import com.mc.oletrex.auth.security.SecurityConfig;
import com.mc.oletrex.auth.token.JwtIssuer;
import com.mc.oletrex.auth.token.AuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Uses the dev client secrets from application.properties (order-service / order-dev-secret).
@WebMvcTest(ServiceTokenController.class)
@EnableConfigurationProperties(AuthProperties.class)
@Import({SecurityConfig.class, JwtRolesConverter.class, ProblemDetailAuthHandlers.class})
class ServiceTokenControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtIssuer jwtIssuer;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static String body(String clientId, String secret) {
        return "{\"clientId\":\"" + clientId + "\",\"clientSecret\":\"" + secret + "\"}";
    }

    @Test
    void validClientGetsToken() throws Exception {
        when(jwtIssuer.issueServiceToken("order-service")).thenReturn(TokenResponse.bearer("tok", 3600));

        mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content(body("order-service", "order-dev-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("tok"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void wrongSecretIs401() throws Exception {
        mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content(body("order-service", "nope")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Invalid client credentials"));
        verifyNoInteractions(jwtIssuer);
    }

    @Test
    void unknownClientIs401() throws Exception {
        mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content(body("ghost-service", "order-dev-secret")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid client credentials"));
        verifyNoInteractions(jwtIssuer);
    }

    @Test
    void blankFieldsAre400() throws Exception {
        mvc.perform(post("/api/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(jwtIssuer);
    }
}
