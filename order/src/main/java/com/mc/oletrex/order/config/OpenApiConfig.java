package com.mc.oletrex.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI:   http://localhost:8081/swagger-ui.html
 * OpenAPI JSON: http://localhost:8081/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Order Service API")
                .description("Places and manages customer orders.")
                .version("v1"));
    }
}
