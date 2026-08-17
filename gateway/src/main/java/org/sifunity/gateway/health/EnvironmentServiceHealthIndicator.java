// SPDX-License-Identifier: MIT
package org.sifunity.gateway.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Health indicator that checks connectivity to the Environment Service.
 * This is used by the Gateway's health endpoint to report overall system health.
 * 
 * Can be disabled by setting: sif.broker.gateway.health.environment-service.enabled=false
 */
@Component
@ConditionalOnProperty(
    name = "sif.broker.gateway.health.environment-service.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class EnvironmentServiceHealthIndicator implements ReactiveHealthIndicator {
    
    private static final Logger log = LoggerFactory.getLogger(EnvironmentServiceHealthIndicator.class);
    
    private final WebClient.Builder webClientBuilder;
    private final String environmentServiceUrl;
    
    public EnvironmentServiceHealthIndicator(
            WebClient.Builder webClientBuilder,
            @Value("${environment-service.url}") String environmentServiceUrl) {
        this.webClientBuilder = webClientBuilder;
        this.environmentServiceUrl = environmentServiceUrl;
    }
    
    @Override
    public Mono<Health> health() {
        return checkEnvironmentServiceHealth()
                .map(isHealthy -> isHealthy ? 
                        Health.up()
                                .withDetail("environment-service", "Available")
                                .withDetail("url", environmentServiceUrl)
                                .build() :
                        Health.down()
                                .withDetail("environment-service", "Unavailable")
                                .withDetail("url", environmentServiceUrl)
                                .build())
                .onErrorResume(error -> {
                    log.error("Health check failed for Environment Service: {}", error.getMessage());
                    return Mono.just(Health.down()
                            .withDetail("environment-service", "Unavailable")
                            .withDetail("url", environmentServiceUrl)
                            .withDetail("error", error.getMessage())
                            .build());
                });
    }
    
    private Mono<Boolean> checkEnvironmentServiceHealth() {
        WebClient webClient = webClientBuilder
                .baseUrl(environmentServiceUrl)
                .build();
        
        return webClient.get()
                .uri("/actuator/health")
                .retrieve()
                .toBodilessEntity()
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .timeout(Duration.ofSeconds(3))
                .onErrorReturn(false);
    }
}
