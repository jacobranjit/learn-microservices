package com.mc.oletrex.auth.token;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties("auth")
public record AuthProperties(String issuer, Duration jwtTtl, Admin admin, Map<String, String> serviceClients) {

    public record Admin(String email, String password, boolean resetPassword) {
    }
}
