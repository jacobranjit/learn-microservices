package com.mc.oletrex.auth.token;

import com.mc.oletrex.auth.dto.TokenResponse;
import com.mc.oletrex.auth.model.Role;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Signs RS256 access tokens for users and for service-to-service calls. */
@Component
public class JwtIssuer {

    private final JwtEncoder encoder;
    private final String keyId;
    private final AuthProperties properties;

    public JwtIssuer(SigningKeys signingKeys, AuthProperties properties) {
        this.keyId = signingKeys.current().getKeyID();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKeys.current())));
        this.properties = properties;
    }

    /** {@code customerId} is omitted from the token when null (admins have none). */
    public TokenResponse issueUserToken(long userId, String email, Role role, Long customerId) {
        JwtClaimsSet.Builder claims = baseClaims(String.valueOf(userId), role.name()).claim("email", email);
        if (customerId != null) {
            claims.claim("customerId", customerId);
        }
        return sign(claims);
    }

    public TokenResponse issueServiceToken(String clientId) {
        return sign(baseClaims(clientId, "SERVICE"));
    }

    private JwtClaimsSet.Builder baseClaims(String subject, String role) {
        Instant now = Instant.now();
        return JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(properties.jwtTtl()))
                .claim("roles", List.of(role));
    }

    private TokenResponse sign(JwtClaimsSet.Builder claims) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return TokenResponse.bearer(token, properties.jwtTtl().toSeconds());
    }
}
