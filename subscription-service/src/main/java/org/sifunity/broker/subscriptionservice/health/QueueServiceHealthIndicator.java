// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Health indicator that checks connectivity to the Queue Service.
 * This is used by the Subscription Service's health endpoint to report overall system health.
 */
@Component
public class QueueServiceHealthIndicator implements HealthIndicator {
    
    private static final Logger log = LoggerFactory.getLogger(QueueServiceHealthIndicator.class);
    
    private final RestTemplate restTemplate;
    private final String queueServiceUrl;
    
    public QueueServiceHealthIndicator(
            RestTemplate restTemplate,
            @Value("${queue.service.url}") String queueServiceUrl) {
        this.restTemplate = restTemplate;
        this.queueServiceUrl = queueServiceUrl;
    }
    
    @Override
    public Health health() {
        try {
            boolean isHealthy = checkQueueServiceHealth();
            
            if (isHealthy) {
                return Health.up()
                        .withDetail("queue-service", "Available")
                        .withDetail("url", queueServiceUrl)
                        .build();
            } else {
                return Health.down()
                        .withDetail("queue-service", "Unavailable")
                        .withDetail("url", queueServiceUrl)
                        .build();
            }
        } catch (Exception e) {
            log.error("Health check failed for Queue Service: {}", e.getMessage());
            return Health.down()
                    .withDetail("queue-service", "Unavailable")
                    .withDetail("url", queueServiceUrl)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
    
    private boolean checkQueueServiceHealth() {
        try {
            String healthUrl = queueServiceUrl + "/actuator/health";
            restTemplate.getForEntity(healthUrl, String.class);
            return true;
        } catch (Exception e) {
            log.debug("Queue Service health check failed: {}", e.getMessage());
            throw e; // Re-throw to be caught by the health() method
        }
    }
}
