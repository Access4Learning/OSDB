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
 * Custom health indicator for the Subscription Service.
 * Performs a health check by calling the Subscription Service's health endpoint.
 * 
 * Requirements: 14 (Observability and Logging)
 */
@Component
@Slf4j
public class SubscriptionServiceHealthIndicator implements HealthIndicator {

    private final RestClient healthCheckRestClient;

    @Value("${subscription-service.url}")
    private String subscriptionServiceUrl;

    public SubscriptionServiceHealthIndicator(
            @Qualifier("healthCheckRestClient") RestClient healthCheckRestClient) {
        this.healthCheckRestClient = healthCheckRestClient;
    }

    @Override
    public Health health() {
        try {
            healthCheckRestClient.get()
                    .uri(subscriptionServiceUrl + "/actuator/health")
                    .retrieve()
                    .toBodilessEntity();
            return Health.up()
                    .withDetail("service", "subscription-service")
                    .withDetail("url", subscriptionServiceUrl)
                    .build();
        } catch (Exception e) {
            log.error("Subscription Service health check failed with exception", e);
            return Health.down()
                    .withDetail("service", "subscription-service")
                    .withDetail("url", subscriptionServiceUrl)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
