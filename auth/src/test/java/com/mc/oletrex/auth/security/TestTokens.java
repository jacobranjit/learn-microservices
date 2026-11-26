package com.mc.oletrex.auth.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.util.List;
import java.util.function.Consumer;

/** Builds authenticated requests for tests, without needing a running auth service. */
public final class TestTokens {

    private TestTokens() {
    }

    public static RequestPostProcessor user(long customerId) {
        return token(j -> j.claim("roles", List.of("USER")).claim("customerId", customerId).subject("user-" + customerId));
    }

    /** A USER token that lacks the customerId claim (malformed or not issued for a customer). */
    public static RequestPostProcessor userWithoutCustomerId() {
        return token(j -> j.claim("roles", List.of("USER")).subject("user-x"));
    }

    public static RequestPostProcessor admin() {
        return token(j -> j.claim("roles", List.of("ADMIN")).subject("admin"));
    }

    public static RequestPostProcessor service() {
        return token(j -> j.claim("roles", List.of("SERVICE")).subject("order-service"));
    }

    public static RequestPostProcessor noRoles() {
        return token(j -> j.subject("nobody"));
    }

    private static RequestPostProcessor token(Consumer<Jwt.Builder> customizer) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(customizer)
                .authorities(j -> new JwtRolesConverter().convert(j).getAuthorities());
    }
}
