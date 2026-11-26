package com.mc.oletrex.customer.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

public final class CurrentUser {

    private CurrentUser() {
    }

    /** The numeric "customerId" claim; empty if absent or not a number. */
    public static Optional<Long> customerId(Jwt jwt) {
        Object claim = jwt.getClaim("customerId");
        if (claim instanceof Number n) {
            return Optional.of(n.longValue());
        }
        return Optional.empty();
    }

    public static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
