package com.mc.oletrex.inventory.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI:   http://localhost:8083/swagger-ui.html
 * OpenAPI JSON: http://localhost:8083/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI inventoryOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Inventory Service API")
                .description("Stock levels per SKU.")
                .version("v1"));
    }
}
