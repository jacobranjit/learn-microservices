package com.mc.oletrex.order.service;

import com.mc.oletrex.order.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Who is asking. Admins see every order (customerId is null); everyone else sees only the
 * orders of their own customerId.
 */
public record Caller(Long customerId, boolean admin) {

    public static Caller from(Authentication authentication) {
        if (CurrentUser.isAdmin(authentication)) {
            return new Caller(null, true);
        }
        Long customerId = authentication != null && authentication.getPrincipal() instanceof Jwt jwt
                ? CurrentUser.customerId(jwt).orElse(null)
                : null;
        return new Caller(customerId, false);
    }
}
