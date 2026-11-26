package com.mc.oletrex.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** An RSA key pair used to sign JWTs, stored as PEM text. The newest row is the active key. */
@Entity
@Table(name = "signing_keys")
public class SigningKey {

    @Id
    private String kid;

    @Column(name = "public_key_pem", columnDefinition = "text", nullable = false)
    private String publicKeyPem;

    @Column(name = "private_key_pem", columnDefinition = "text", nullable = false)
    private String privateKeyPem;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SigningKey() {
    }

    public SigningKey(String kid, String publicKeyPem, String privateKeyPem, Instant createdAt) {
        this.kid = kid;
        this.publicKeyPem = publicKeyPem;
        this.privateKeyPem = privateKeyPem;
        this.createdAt = createdAt;
    }

    public String getKid() { return kid; }
    public String getPublicKeyPem() { return publicKeyPem; }
    public String getPrivateKeyPem() { return privateKeyPem; }
    public Instant getCreatedAt() { return createdAt; }
}
