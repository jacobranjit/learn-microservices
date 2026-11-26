package com.mc.oletrex.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Registration calls the customer service. A cold customer container on a small CPU limit took more than
 * 5 s for the first concurrent requests (seen when master-data seeds 300 customers), which timed out the
 * call and left orphan customers. The read timeout must tolerate that but stay under master-data's 10 s.
 */
class OutboundTimeoutTest {

    @Test
    void customerReadTimeoutToleratesColdStartButStaysBelowCallersTimeout() throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        new PropertiesPropertySourceLoader().load("application", new ClassPathResource("application.properties"))
                .forEach(ps -> env.getPropertySources().addLast(ps));
        Duration read = Binder.get(env).bind("services.http.read-timeout", Duration.class).get();
        assertThat(read).isGreaterThanOrEqualTo(Duration.ofSeconds(8)).isLessThan(Duration.ofSeconds(10));
    }
}
