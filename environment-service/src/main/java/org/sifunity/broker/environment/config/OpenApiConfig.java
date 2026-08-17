package org.sifunity.broker.environment.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${springdoc.info.description:}")
    private String description;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title(applicationName)
                .version("1.0.0-SNAPSHOT")
                .description(description + "\n\n**Authentication:** Click Authorize and enter your "
                    + "sessionToken as username and sharedSecret as password. "
                    + "The Swagger UI will automatically compute the SIF HMAC-SHA256 signature for each request.")
                .contact(new Contact()
                    .name("SIF Unity Broker")
                    .url("https://gitlab.com/sif-unity-broker"))
                .license(new License()
                    .name("MIT")
                    .url("https://opensource.org/licenses/MIT")))
            .addServersItem(new io.swagger.v3.oas.models.servers.Server()
                .url("/")
                .description("Current server"))
            .components(new Components()
                .addSecuritySchemes("SIF_HMACSHA256", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("basic")
                    .description("Enter sessionToken as username and sharedSecret as password. "
                        + "The request interceptor will compute the SIF HMAC-SHA256 signature automatically.")))
            .addSecurityItem(new SecurityRequirement().addList("SIF_HMACSHA256"));
    }

    @Bean
    public GroupedOpenApi sifApi() {
        return GroupedOpenApi.builder()
            .group("sif-api")
            .displayName("SIF API")
            .pathsToMatch("/**")
            .addOpenApiMethodFilter(method ->
                method.getDeclaringClass().isAnnotationPresent(io.swagger.v3.oas.annotations.tags.Tag.class) &&
                java.util.Arrays.stream(method.getDeclaringClass().getAnnotationsByType(io.swagger.v3.oas.annotations.tags.Tag.class))
                    .anyMatch(tag -> "SIF API".equals(tag.name())))
            .build();
    }

    @Bean
    public GroupedOpenApi internalApi() {
        return GroupedOpenApi.builder()
            .group("internal-api")
            .displayName("Internal API")
            .pathsToMatch("/**")
            .addOpenApiMethodFilter(method ->
                method.getDeclaringClass().isAnnotationPresent(io.swagger.v3.oas.annotations.tags.Tag.class) &&
                java.util.Arrays.stream(method.getDeclaringClass().getAnnotationsByType(io.swagger.v3.oas.annotations.tags.Tag.class))
                    .anyMatch(tag -> "Internal API".equals(tag.name())))
            .build();
    }
}
