// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * HTTP client for Environment Service communication.
 * Used to resolve zone/context string identifiers to context UUIDs.
 *
 * <p>The events-connector receives SIF headers with string-based zone and context
 * identifiers. Before calling the subscription-service (which expects a context UUID),
 * this client resolves the string identifiers to the internal UUID by querying the
 * environment-service's context listing endpoint.</p>
 */
@Component
@Slf4j
public class EnvironmentServiceClient {

    private final RestClient restClient;

    @Value("${broker.internal-service-token:}")
    private String internalServiceToken;

    public EnvironmentServiceClient(
        @Qualifier("environmentServiceRestClient") RestClient restClient,
        @Value("${environment-service.url}") String environmentServiceUrl
    ) {
        this.restClient = restClient;
    }

    /**
     * Resolves a zone ID and context ID string pair to a context UUID.
     *
     * @param domainId   the domain ID for the request path
     * @param solutionId the solution ID for the request path
     * @param zoneId     the SIF zone identifier string
     * @param contextId  the SIF context identifier string; null/blank defaults to DEFAULT
     * @return the context UUID, or null if the context cannot be resolved (e.g., zone not found)
     */
    @Cacheable(value = "context-uuid", key = "#domainId + ':' + #solutionId + ':' + #zoneId + ':' + (#contextId != null && !#contextId.isBlank() ? #contextId : 'DEFAULT')", unless = "#result == null")
    public UUID resolveContextUuid(String domainId, String solutionId, String zoneId, String contextId) {
        if (contextId == null || contextId.isBlank()) {
            // Broker invariant: a missing context is not a wildcard. Every zone has a required
            // DEFAULT context, and any caller that omits a context is attached to DEFAULT.
            log.debug("Context is null/blank; defaulting to DEFAULT context");
            contextId = "DEFAULT";
        }

        log.debug("Resolving contextUuid: domainId={}, solutionId={}, zoneId={}, contextId={}",
            domainId, solutionId, zoneId, contextId);

        try {
            JsonNode response = restClient.get()
                .uri("/internal/contexts/resolve?domainId={domainId}&solutionId={solutionId}&zoneId={zoneId}&contextId={contextId}",
                    domainId, solutionId, zoneId, contextId)
                .header("X-Internal-Service-Token", internalServiceToken)
                .retrieve()
                .body(JsonNode.class);

            if (response == null || !response.has("contextUuid")) {
                log.warn("Empty or invalid response from environment-service for zone={}, context={}", zoneId, contextId);
                return null;
            }

            UUID resolved = UUID.fromString(response.get("contextUuid").asText());
            log.debug("Resolved contextUuid={} for zoneId={}, contextId={}", resolved, zoneId, contextId);
            return resolved;

        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                log.debug("Context not found via internal resolve: zoneId={}, contextId={}", zoneId, contextId);
            } else {
                log.error("Environment service returned error resolving context: status={}, zoneId={}, contextId={}",
                    e.getStatusCode(), zoneId, contextId);
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to resolve contextUuid: zoneId={}, contextId={}", zoneId, contextId, e);
            return null;
        }
    }
}
