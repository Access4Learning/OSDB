// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * HTTP client for communicating with the queue-service REST API.
 *
 * <p>Responsible for discovering and deleting queues owned by an environment
 * during environment deletion. All operations are best-effort: failures are
 * logged but never propagated to callers.</p>
 *
 * @see org.sifunity.broker.environment.service.EnvironmentService
 */
@Component
public class QueueServiceClient {

    private static final Logger log = LoggerFactory.getLogger(QueueServiceClient.class);
    private static final String BROKER_SERVICES_FINGERPRINT = "broker-services";
    private static final String HEADER_ENVIRONMENT_ID = "X-Environment-Id";
    private static final String HEADER_ENVIRONMENT_FINGERPRINT = "X-Environment-Fingerprint";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public QueueServiceClient(
            @Value("${broker.queue-service.base-url:}") String baseUrl,
            RestTemplateBuilder restTemplateBuilder,
            @Value("${broker.queue-service.connect-timeout-ms:5000}") int connectTimeout,
            @Value("${broker.queue-service.read-timeout-ms:10000}") int readTimeout,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(connectTimeout))
                .setReadTimeout(Duration.ofMillis(readTimeout))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Returns true if queue cleanup is enabled (base URL is configured).
     */
    public boolean isEnabled() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    /**
     * Best-effort deletion of all queues owned by the given environment.
     * Logs warnings on failure but never throws.
     *
     * @param environmentId the environment UUID being deleted
     * @param domainId      the human-readable domain ID
     * @param solutionId    the human-readable solution ID
     */
    public void deleteQueuesForEnvironment(UUID environmentId, String domainId, String solutionId) {
        if (!isEnabled()) {
            log.warn("Queue cleanup disabled: broker.queue-service.base-url not configured. "
                    + "Skipping queue cleanup for environment={}", environmentId);
            return;
        }

        List<UUID> ownedQueueIds;
        try {
            ownedQueueIds = listOwnedQueues(environmentId, domainId, solutionId);
        } catch (Exception e) {
            log.warn("Failed to list queues for environment={}. Skipping queue cleanup. Error: {}",
                    environmentId, e.getMessage());
            return;
        }

        if (ownedQueueIds.isEmpty()) {
            log.debug("No owned queues found for environment={}", environmentId);
            return;
        }

        log.info("Deleting {} owned queues for environment={}", ownedQueueIds.size(), environmentId);
        for (UUID queueId : ownedQueueIds) {
            deleteQueue(queueId, environmentId, domainId, solutionId);
        }
    }

    /**
     * Lists all queues in the solution and filters to those owned by environmentId.
     */
    private List<UUID> listOwnedQueues(UUID environmentId, String domainId, String solutionId) {
        String url = baseUrl + "/" + domainId + "/" + solutionId + "/queues";

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_ENVIRONMENT_ID, environmentId.toString());
        headers.set(HEADER_ENVIRONMENT_FINGERPRINT, BROKER_SERVICES_FINGERPRINT);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, requestEntity, String.class);

        return parseOwnedQueues(response.getBody(), environmentId);
    }

    /**
     * Parses the queue-service response and filters queues by ownerId.
     *
     * <p>Expected response format:
     * <pre>
     * {
     *   "queues": {
     *     "queue": [
     *       { "id": "uuid", "ownerId": "environment-uuid", ... }
     *     ]
     *   }
     * }
     * </pre>
     */
    private List<UUID> parseOwnedQueues(String responseBody, UUID environmentId) {
        if (responseBody == null || responseBody.isBlank()) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode queuesNode = root.path("queues").path("queue");

            if (queuesNode.isMissingNode() || !queuesNode.isArray()) {
                return Collections.emptyList();
            }

            List<UUID> ownedQueueIds = new ArrayList<>();
            String envIdStr = environmentId.toString();

            for (JsonNode queueNode : queuesNode) {
                String ownerId = queueNode.path("ownerId").asText("");
                if (envIdStr.equals(ownerId)) {
                    String queueId = queueNode.path("id").asText("");
                    if (!queueId.isEmpty()) {
                        ownedQueueIds.add(UUID.fromString(queueId));
                    }
                }
            }

            return ownedQueueIds;
        } catch (Exception e) {
            log.warn("Failed to parse queue list response for environment={}. Error: {}",
                    environmentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fires a DELETE request for a single queue. Logs and continues on any error.
     */
    private void deleteQueue(UUID queueId, UUID environmentId, String domainId, String solutionId) {
        String url = baseUrl + "/" + domainId + "/" + solutionId + "/queues/" + queueId;

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_ENVIRONMENT_ID, environmentId.toString());
        headers.set(HEADER_ENVIRONMENT_FINGERPRINT, BROKER_SERVICES_FINGERPRINT);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, Void.class);
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("Queue {} already deleted (404) for environment={}", queueId, environmentId);
        } catch (Exception e) {
            log.warn("Failed to delete queue={} for environment={}. Error: {}",
                    queueId, environmentId, e.getMessage());
        }
    }
}
