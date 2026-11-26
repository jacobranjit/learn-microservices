package com.mc.oletrex.auth.controller;

import com.mc.oletrex.auth.token.SigningKeys;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class JwksController {

    private final SigningKeys signingKeys;

    public JwksController(SigningKeys signingKeys) {
        this.signingKeys = signingKeys;
    }

    /** Public keys only; other services use this to verify token signatures. */
    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return signingKeys.publicJwkSet().toJSONObject();
    }
}
