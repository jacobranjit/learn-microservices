package com.mc.oletrex.auth.token;

import com.mc.oletrex.auth.dto.TokenResponse;
import com.mc.oletrex.auth.model.Role;
import com.mc.oletrex.auth.model.SigningKey;
import com.mc.oletrex.auth.repository.SigningKeyRepository;
import com.mc.oletrex.auth.security.SecurityConfigAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtIssuerTest {

    private static final String ISSUER = "oletrex-auth";

    private SigningKeys signingKeys;
    private JwtIssuer issuer;
    private NimbusJwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        SigningKeyRepository repository = mock(SigningKeyRepository.class);
        when(repository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.empty());
        when(repository.save(any(SigningKey.class))).thenAnswer(i -> i.getArgument(0));
        signingKeys = new SigningKeys(repository);
        AuthProperties properties = new AuthProperties(ISSUER, Duration.ofHours(1),
                new AuthProperties.Admin("admin@oletrex.local", "x", false), Map.of());
        issuer = new JwtIssuer(signingKeys, properties);
        decoder = NimbusJwtDecoder.withPublicKey(signingKeys.current().toRSAPublicKey()).build();
        decoder.setJwtValidator(SecurityConfigAccess.validator(ISSUER));
    }

    @Test
    void userTokenVerifiesWithPublishedKey() {
        TokenResponse response = issuer.issueUserToken(5, "a@b.c", Role.USER, 42L);

        Jwt jwt = decoder.decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("5");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("a@b.c");
        assertThat(((Number) jwt.getClaim("customerId")).longValue()).isEqualTo(42L);
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(ISSUER);
        assertThat(jwt.getHeaders().get("kid")).isEqualTo(signingKeys.current().getKeyID());
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("RS256");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
    }

    @Test
    void adminTokenHasNoCustomerId() {
        Jwt jwt = decoder.decode(issuer.issueUserToken(1, "admin@oletrex.local", Role.ADMIN, null).accessToken());

        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(jwt.hasClaim("customerId")).isFalse();
    }

    @Test
    void serviceToken() {
        Jwt jwt = decoder.decode(issuer.issueServiceToken("order-service").accessToken());

        assertThat(jwt.getSubject()).isEqualTo("order-service");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SERVICE");
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        JwtIssuer other = new JwtIssuer(signingKeys, new AuthProperties("someone-else", Duration.ofHours(1),
                new AuthProperties.Admin("a", "b", false), Map.of()));
        String token = other.issueServiceToken("order-service").accessToken();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void jwksContainsNoPrivateParts() {
        String json = signingKeys.publicJwkSet().toString();

        assertThat(json).contains("\"kty\"", "\"n\"", "\"e\"", signingKeys.current().getKeyID());
        assertThat(signingKeys.publicJwkSet().toJSONObject().toString()).doesNotContain("\"d\"", "\"p\"", "\"q\"");
        assertThat(signingKeys.publicJwkSet().getKeys()).allMatch(k -> !k.isPrivate());
    }

    @Test
    void storedKeyIsReloadedInsteadOfRegenerated() throws Exception {
        SigningKeyRepository first = mock(SigningKeyRepository.class);
        when(first.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.empty());
        org.mockito.ArgumentCaptor<SigningKey> saved = org.mockito.ArgumentCaptor.forClass(SigningKey.class);
        when(first.save(saved.capture())).thenAnswer(i -> i.getArgument(0));
        SigningKeys generated = new SigningKeys(first);

        SigningKeyRepository second = mock(SigningKeyRepository.class);
        when(second.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.of(saved.getValue()));
        SigningKeys reloaded = new SigningKeys(second);

        verify(second, never()).save(any());
        assertThat(reloaded.current().getKeyID()).isEqualTo(generated.current().getKeyID());
        assertThat(reloaded.current().toRSAPublicKey()).isEqualTo(generated.current().toRSAPublicKey());
        assertThat(reloaded.current().toRSAPrivateKey()).isEqualTo(generated.current().toRSAPrivateKey());
    }
}
