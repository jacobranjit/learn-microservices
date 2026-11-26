package com.mc.oletrex.auth.service;

import com.mc.oletrex.auth.model.Role;
import com.mc.oletrex.auth.model.UserAccount;
import com.mc.oletrex.auth.repository.UserAccountRepository;
import com.mc.oletrex.auth.token.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Creates the first admin from auth.admin.* on startup. Never touches an existing account. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;

    public AdminBootstrap(UserAccountRepository repository, PasswordEncoder passwordEncoder, AuthProperties properties) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = properties.admin().email().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmail(email)) {
            return;
        }
        repository.save(new UserAccount(email, passwordEncoder.encode(properties.admin().password()), Role.ADMIN, true, null));
        log.info("Created first admin account {}", email);
    }
}
