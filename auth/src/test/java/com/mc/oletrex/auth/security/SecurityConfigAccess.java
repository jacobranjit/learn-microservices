package com.mc.oletrex.auth.security;

import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/** Test-only bridge to the package-private validator in {@link SecurityConfig}. */
public final class SecurityConfigAccess {

    private SecurityConfigAccess() {
    }

    public static OAuth2TokenValidator<Jwt> validator(String issuer) {
        return SecurityConfig.validator(issuer);
    }
}
