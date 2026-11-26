package com.mc.oletrex.auth.service;

import com.mc.oletrex.auth.token.AuthProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSecretsCheckTest {

    private static AuthProperties props(String adminPassword, String order, String inventory) {
        return new AuthProperties("iss", Duration.ofHours(1),
                new AuthProperties.Admin("admin@oletrex.local", adminPassword, false),
                Map.of("order-service", order, "inventory-service", inventory));
    }

    @Test
    void reportsEveryDevDefaultStillInUse() {
        assertThat(DefaultSecretsCheck.findDefaults(props("Admin123!", "order-dev-secret", "inventory-dev-secret")))
                .hasSize(3)
                .anyMatch(m -> m.contains("admin password"))
                .anyMatch(m -> m.contains("order-service"))
                .anyMatch(m -> m.contains("inventory-service"));
    }

    @Test
    void reportsOnlyTheOnesLeftAtDefault() {
        assertThat(DefaultSecretsCheck.findDefaults(props("Str0ng-and-new", "order-dev-secret", "another-strong-one")))
                .hasSize(1).first().asString().contains("order-service");
    }

    @Test
    void reportsNothingWhenAllChanged() {
        assertThat(DefaultSecretsCheck.findDefaults(props("a", "b", "c"))).isEmpty();
    }

    @Test
    void secretValuesAreNeverPartOfTheMessage() {
        assertThat(DefaultSecretsCheck.findDefaults(props("Admin123!", "order-dev-secret", "x")))
                .noneMatch(m -> m.contains("Admin123!") || m.contains("order-dev-secret"));
    }
}
