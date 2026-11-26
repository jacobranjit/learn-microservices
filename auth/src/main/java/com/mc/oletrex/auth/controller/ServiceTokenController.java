package com.mc.oletrex.auth.controller;

import com.mc.oletrex.auth.dto.ServiceTokenRequest;
import com.mc.oletrex.auth.dto.TokenResponse;
import com.mc.oletrex.auth.exception.InvalidCredentialsException;
import com.mc.oletrex.auth.token.AuthProperties;
import com.mc.oletrex.auth.token.JwtIssuer;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/auth")
public class ServiceTokenController {

    private final AuthProperties properties;
    private final JwtIssuer jwtIssuer;

    public ServiceTokenController(AuthProperties properties, JwtIssuer jwtIssuer) {
        this.properties = properties;
        this.jwtIssuer = jwtIssuer;
    }

    /** Client-credentials style token for service-to-service calls. */
    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody ServiceTokenRequest request) {
        String expected = properties.serviceClients() == null ? null : properties.serviceClients().get(request.clientId());
        // Compare even for an unknown client so both failures cost the same.
        String reference = expected != null ? expected : "";
        boolean secretMatches = MessageDigest.isEqual(
                reference.getBytes(StandardCharsets.UTF_8), request.clientSecret().getBytes(StandardCharsets.UTF_8));
        if (expected == null || !secretMatches) {
            throw new InvalidCredentialsException("Invalid client credentials");
        }
        return jwtIssuer.issueServiceToken(request.clientId());
    }
}
