package com.mc.oletrex.masterdata.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.function.Consumer;

/** Builds authenticated requests for tests, without needing a running auth service. */
public final class TestTokens {

    private TestTokens() {
    }

    public static RequestPostProcessor user(long customerId) {
        return token(j -> j.claim("roles", List.of("USER")).claim("customerId", customerId).subject("user-" + customerId));
    }

    public static RequestPostProcessor admin() {
        return token(j -> j.claim("roles", List.of("ADMIN")).subject("admin"));
    }

    private static RequestPostProcessor token(Consumer<Jwt.Builder> customizer) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(customizer)
                .authorities(j -> new JwtRolesConverter().convert(j).getAuthorities());
    }
}
