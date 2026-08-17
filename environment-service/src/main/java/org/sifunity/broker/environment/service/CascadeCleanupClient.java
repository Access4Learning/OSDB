// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HTTP client for triggering cascade cleanup in queue-service when a domain
 * or solution is deleted.
 *
 * <p>Fire-and-forget: all exceptions are caught and logged as warnings.
 * If the queue-service base URL is not configured, calls are no-ops.</p>
 *
 * @see org.sifunity.broker.environment.service.QueueServiceClient
 */
@Component
public class CascadeCleanupClient {

    private static final Logger log = LoggerFactory.getLogger(CascadeCleanupClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    @Value("${broker.internal-service-token:}")
    private String internalServiceToken;

    public CascadeCleanupClient(
            @Value("${broker.queue-service.base-url:}") String baseUrl,
            RestTemplateBuilder restTemplateBuilder) {
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(5000))
                .setReadTimeout(Duration.ofMillis(5000))
                .build();
    }

    /**
     * Triggers cascade cleanup for all resources belonging to a domain.
     * Fire-and-forget: never throws.
     *
     * @param domainId the domain being deleted
     */
    public void triggerDomainCleanup(String domainId) {
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("Cascade cleanup disabled: queue-service base-url not configured");
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Token", internalServiceToken);
            HttpEntity<CascadeCleanupRequest> entity =
                    new HttpEntity<>(new CascadeCleanupRequest(domainId, null), headers);
            restTemplate.postForEntity(
                    baseUrl + "/internal/cascade-cleanup",
                    entity,
                    Void.class);
        } catch (Exception e) {
            log.warn("Failed to trigger cascade cleanup for domain={}: {}", domainId, e.getMessage());
        }
    }

    /**
     * Triggers cascade cleanup for all resources belonging to a solution.
     * Fire-and-forget: never throws.
     *
     * @param domainId   the parent domain ID
     * @param solutionId the solution being deleted
     */
    public void triggerSolutionCleanup(String domainId, String solutionId) {
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("Cascade cleanup disabled: queue-service base-url not configured");
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Token", internalServiceToken);
            HttpEntity<CascadeCleanupRequest> entity =
                    new HttpEntity<>(new CascadeCleanupRequest(domainId, solutionId), headers);
            restTemplate.postForEntity(
                    baseUrl + "/internal/cascade-cleanup",
                    entity,
                    Void.class);
        } catch (Exception e) {
            log.warn("Failed to trigger cascade cleanup for domain={}, solution={}: {}",
                    domainId, solutionId, e.getMessage());
        }
    }

    private record CascadeCleanupRequest(String domainId, String solutionId) {}
}
