package com.mc.oletrex.masterdata.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI:   http://localhost:8084/swagger-ui.html
 * OpenAPI JSON: http://localhost:8084/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI masterDataOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Generate Master Data API")
                .description("Loads initial test data (locations, products, stock) from CSV files "
                        + "into the inventory and product services through their APIs.")
                .version("v1"));
    }
}
