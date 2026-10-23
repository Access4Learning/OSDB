package org.sifunity.broker.subscriptionservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
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
                .description(description)
                .contact(new Contact()
                    .name("SIF Unity Broker")
                    .url("https://github.com/Access4Learning/OSDB"))
                .license(new License()
                    .name("MIT")
                    .url("https://opensource.org/licenses/MIT")));
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
