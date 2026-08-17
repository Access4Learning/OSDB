// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Custom health indicator for the Queue Service.
 * Performs a health check by calling the Queue Service's health endpoint.
 * 
 * Requirements: 14 (Observability and Logging)
 */
@Component
@Slf4j
public class QueueServiceHealthIndicator implements HealthIndicator {

    private final RestClient healthCheckRestClient;

    @Value("${queue-service.url}")
    private String queueServiceUrl;

    public QueueServiceHealthIndicator(
            @Qualifier("healthCheckRestClient") RestClient healthCheckRestClient) {
        this.healthCheckRestClient = healthCheckRestClient;
    }

    @Override
    public Health health() {
        try {
            healthCheckRestClient.get()
                    .uri(queueServiceUrl + "/actuator/health")
                    .retrieve()
                    .toBodilessEntity();
            return Health.up()
                    .withDetail("service", "queue-service")
                    .withDetail("url", queueServiceUrl)
                    .build();
        } catch (Exception e) {
            log.error("Queue Service health check failed with exception", e);
            return Health.down()
                    .withDetail("service", "queue-service")
                    .withDetail("url", queueServiceUrl)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
