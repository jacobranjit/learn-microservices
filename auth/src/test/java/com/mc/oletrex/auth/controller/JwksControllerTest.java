package com.mc.oletrex.auth.controller;

import com.mc.oletrex.auth.security.JwtRolesConverter;
import com.mc.oletrex.auth.security.ProblemDetailAuthHandlers;
import com.mc.oletrex.auth.security.SecurityConfig;
import com.mc.oletrex.auth.token.SigningKeys;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JwksController.class)
@Import({SecurityConfig.class, JwtRolesConverter.class, ProblemDetailAuthHandlers.class})
class JwksControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SigningKeys signingKeys;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void jwksIsPublicAndExposesOnlyPublicParts() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("k1").generate();
        when(signingKeys.publicJwkSet()).thenReturn(new JWKSet(key.toPublicJWK()));

        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kid").value("k1"))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.keys[0].p").doesNotExist())
                .andExpect(jsonPath("$.keys[0].q").doesNotExist());
    }
}
