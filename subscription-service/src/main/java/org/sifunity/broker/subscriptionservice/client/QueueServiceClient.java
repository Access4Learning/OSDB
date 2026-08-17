// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.client;

import org.sifunity.broker.common.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Client for validating queue existence.
 * Checks the shared database directly since both services use the same PostgreSQL instance.
 */
@Component
public class QueueServiceClient {

    private final RestTemplate restTemplate;
    private final String queueServiceUrl;
    private final JdbcTemplate jdbcTemplate;

    public QueueServiceClient(RestTemplate restTemplate,
                              JdbcTemplate jdbcTemplate,
                              @Value("${queue.service.url}") String queueServiceUrl) {
        this.restTemplate = restTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.queueServiceUrl = queueServiceUrl;
    }

    /**
     * Checks if a queue exists by querying the shared database directly.
     *
     * @param queueId the UUID of the queue to check
     * @return true if the queue exists, false otherwise
     */
    public boolean queueExists(UUID queueId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM queues WHERE id = ?", Integer.class, queueId);
            return count != null && count > 0;
        } catch (Exception e) {
            throw new ServiceUnavailableException("Queue Service",
                    "Failed to validate queue existence: " + e.getMessage());
        }
    }
}
