package com.mc.oletrex.auth.service;

import com.mc.oletrex.auth.token.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Warns (never fails) when a secret still has its published dev default; a fresh clone must start. */
@Component
public class DefaultSecretsCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultSecretsCheck.class);

    static final String DEFAULT_ADMIN_PASSWORD = "Admin123!";
    static final Map<String, String> DEFAULT_CLIENT_SECRETS = Map.of(
            "order-service", "order-dev-secret",
            "inventory-service", "inventory-dev-secret");

    private final AuthProperties properties;

    public DefaultSecretsCheck(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        findDefaults(properties).forEach(log::warn);
    }

    /** Messages for every secret equal to its dev default. The secret values themselves are never included. */
    static List<String> findDefaults(AuthProperties properties) {
        List<String> found = new ArrayList<>();
        if (DEFAULT_ADMIN_PASSWORD.equals(properties.admin().password())) {
            found.add("The admin password is still the dev default; set ADMIN_PASSWORD (see .env.example)");
        }
        DEFAULT_CLIENT_SECRETS.forEach((client, devDefault) -> {
            if (devDefault.equals(properties.serviceClients().get(client))) {
                found.add("The secret of service client " + client + " is still the dev default; set its *_SERVICE_SECRET (see .env.example)");
            }
        });
        return found;
    }
}
