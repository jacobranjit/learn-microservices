package com.mc.oletrex.product.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI:   http://localhost:8082/swagger-ui.html
 * OpenAPI JSON: http://localhost:8082/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI productOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Product Service API")
                .description("Product catalog: core product data and extended details.")
                .version("v1"));
    }
}
