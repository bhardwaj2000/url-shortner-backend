package com.mks.open.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for API documentation.
 * <p>
 * Configures the OpenAPI documentation for the URL Shortener API.
 *
 * @author DevTeam
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("URL Shortener API")
                        .version("1.0.0")
                        .description("Production-ready URL shortener backend API using Spring Boot and MongoDB")
                        .termsOfService("https://example.com/terms")
                        .contact(new Contact()
                                .name("API Support")
                                .url("https://example.com/contact")
                                .email("support@example.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .tags(List.of(
                        new Tag()
                                .name("url-shortener")
                                .description("URL Shortener operations"),
                        new Tag()
                                .name("analytics")
                                .description("URL Analytics operations"),
                        new Tag()
                                .name("health")
                                .description("Health check operations")))
                .externalDocs(new io.swagger.v3.oas.models.ExternalDocumentation()
                        .description("URL Shortener Documentation")
                        .url("https://example.com/docs"));
    }
}
