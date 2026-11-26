package com.mc.oletrex.auth.token;

import com.mc.oletrex.auth.model.SigningKey;
import com.mc.oletrex.auth.repository.SigningKeyRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Holds the active RSA signing key. On startup the newest stored key is loaded; if there is none,
 * a new 2048-bit pair is generated and saved, so tokens stay valid across restarts.
 */
@Component
public class SigningKeys {

    private final RSAKey current;

    public SigningKeys(SigningKeyRepository repository) {
        SigningKey stored = repository.findFirstByOrderByCreatedAtDesc()
                .orElseGet(() -> repository.save(generate()));
        this.current = toRsaKey(stored);
    }

    /** The active key pair, with its kid. Contains the private part: never serialise it. */
    public RSAKey current() {
        return current;
    }

    /** The public half only, as published at /.well-known/jwks.json. */
    public JWKSet publicJwkSet() {
        return new JWKSet(current.toPublicJWK());
    }

    private static SigningKey generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new SigningKey(UUID.randomUUID().toString(),
                    pem("PUBLIC KEY", pair.getPublic().getEncoded()),
                    pem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                    Instant.now());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA is not available", e);
        }
    }

    private static RSAKey toRsaKey(SigningKey key) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                    new X509EncodedKeySpec(unpem(key.getPublicKeyPem())));
            RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(
                    new PKCS8EncodedKeySpec(unpem(key.getPrivateKeyPem())));
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(key.getKid())
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load signing key " + key.getKid(), e);
        }
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }

    private static byte[] unpem(String pem) {
        return Base64.getMimeDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", ""));
    }
}
