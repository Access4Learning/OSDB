// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;
import java.util.UUID;

/**
 * HTTP client for communicating with the Environment Service.
 * Provides methods for provider registry lookup operations.
 * 
 * Requirements:
 * - 7.1: Query provider registry with serviceName, zoneId, and contextId
 */
@Component
@Slf4j
public class EnvironmentServiceClient {
    
    private final WebClient webClient;

    @Value("${broker.internal-service-token:}")
    private String internalServiceToken;
    
    /**
     * Constructs an EnvironmentServiceClient with the configured WebClient and base URL.
     * 
     * @param webClientBuilder WebClient builder configured for Environment Service (with timeouts)
     * @param baseUrl Environment Service base URL from configuration
     */
    public EnvironmentServiceClient(
            @Qualifier("environmentServiceWebClientBuilder") WebClient.Builder webClientBuilder,
            @Value("${environment.service.url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }
    
    /**
     * Looks up a provider registration by service name, zone ID, and context ID.
     * 
     * <p>This method queries the Environment Service provider registry to find a provider
     * that matches the specified service, zone, and context. The lookup is used during
     * job creation to determine which provider endpoint should handle the job.</p>
     * 
     * @param domainId the domain ID for tenant isolation (required)
     * @param solutionId the solution ID for tenant isolation (required)
     * @param serviceName the name of the service to look up (e.g., "DataExport")
     * @param zoneId the zone ID for the lookup (e.g., "production")
     * @param contextId the context ID for the lookup (optional, can be null)
     * @return Optional containing the provider registration if found, empty otherwise
     * @throws org.springframework.web.reactive.function.client.WebClientResponseException
     *         if Environment Service returns an error
     */
    public Optional<ProviderRegistrationDTO> lookupProvider(
            String domainId, String solutionId, String serviceName, String zoneId, String contextId) {

        try {
            return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                        .path("/internal/provider-lookup")
                        .queryParam("serviceName", serviceName)
                        .queryParam("zoneId", zoneId)
                        .queryParam("domainId", domainId)
                        .queryParam("solutionId", solutionId);

                    if (contextId != null) {
                        builder.queryParam("contextId", contextId);
                    }
                    
                    return builder.build();
                })
                .header("X-Internal-Service-Token", internalServiceToken)
                .retrieve()
                .bodyToMono(ProviderRegistrationDTO.class)
                .blockOptional();
        } catch (WebClientResponseException.NotFound e) {
            // Environment service returns 404 when no provider is registered — this is expected
            log.debug("No provider found for serviceName={}, zoneId={}, contextId={}", serviceName, zoneId, contextId);
            return Optional.empty();
        }
    }

    /**
     * Checks whether a PROVIDE ACL (accessType=PROVIDE, accessLevel=APPROVED) exists
     * for the given service, zone, and context, and returns the provider's environment ID.
     *
     * <p>This is the fallback check used by the DELAYED routing flow when no
     * ProviderRegistration entity is found. Per SIF spec, DELAYED requests only
     * require that a PROVIDE ACL exists — the provider may not have registered yet.</p>
     *
     * @param domainId the domain ID for tenant isolation (required)
     * @param solutionId the solution ID for tenant isolation (required)
     * @param serviceName the functional service name (e.g., "DataExport")
     * @param zoneId the zone ID for the lookup
     * @param contextId the context ID for the lookup (may be null)
     * @return true if a PROVIDE ACL exists, false otherwise (including on error)
     */
    public boolean hasProvideAcl(String domainId, String solutionId, String serviceName, String zoneId, String contextId) {
        return getProvideAclEnvironmentId(domainId, solutionId, serviceName, zoneId, contextId) != null;
    }

    /**
     * Returns the provider environment ID that holds the PROVIDE ACL for the given
     * service/zone/context, or null if no PROVIDE ACL exists.
     *
     * <p>Used by the queue-only routing flow to find the provider's queue for message delivery.</p>
     *
     * @param domainId the domain ID for tenant isolation (required)
     * @param solutionId the solution ID for tenant isolation (required)
     * @param serviceName the functional service name (e.g., "DataExport")
     * @param zoneId the zone ID for the lookup
     * @param contextId the context ID for the lookup (may be null)
     * @return the provider's environment ID, or null if no PROVIDE ACL exists
     */
    public UUID getProvideAclEnvironmentId(String domainId, String solutionId, String serviceName, String zoneId, String contextId) {
        try {
            JsonNode response = webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                        .path("/internal/provide-acl-check")
                        .queryParam("serviceName", serviceName)
                        .queryParam("zoneId", zoneId)
                        .queryParam("domainId", domainId)
                        .queryParam("solutionId", solutionId);

                    if (contextId != null) {
                        builder.queryParam("contextId", contextId);
                    }

                    return builder.build();
                })
                .header("X-Internal-Service-Token", internalServiceToken)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

            if (response != null && response.has("exists") && response.get("exists").asBoolean(false)) {
                if (response.has("providerEnvironmentId")) {
                    return UUID.fromString(response.get("providerEnvironmentId").asText());
                }
                return null; // exists but no environmentId in response (shouldn't happen)
            }

            return null;

        } catch (WebClientResponseException e) {
            log.error("Environment service returned error checking PROVIDE ACL: status={}, serviceName={}, zoneId={}, contextId={}",
                    e.getStatusCode(), serviceName, zoneId, contextId);
            return null;
        } catch (Exception e) {
            log.error("Failed to check PROVIDE ACL: serviceName={}, zoneId={}, contextId={}",
                    serviceName, zoneId, contextId, e);
            return null;
        }
    }

    /**
     * Resolves a zone ID and context ID string pair to a context UUID.
     *
     * <p>Calls the environment-service's context listing endpoint for the given zone
     * and searches for a context matching the provided contextId string.</p>
     *
     * @param domainId   the domain ID for the request path
     * @param solutionId the solution ID for the request path
     * @param zoneId     the SIF zone identifier string
     * @param contextId  the SIF context identifier string; null/blank defaults to DEFAULT
     * @return the context UUID, or null if the context cannot be resolved (e.g., zone not found)
     */
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
            JsonNode response = webClient.get()
                    .uri("/internal/contexts/resolve?domainId={domainId}&solutionId={solutionId}&zoneId={zoneId}&contextId={contextId}",
                        domainId, solutionId, zoneId, contextId)
                    .header("X-Internal-Service-Token", internalServiceToken)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("contextUuid")) {
                log.warn("Empty or invalid response from environment-service for zone={}, context={}", zoneId, contextId);
                return null;
            }

            UUID resolved = UUID.fromString(response.get("contextUuid").asText());
            log.debug("Resolved contextUuid={} for zoneId={}, contextId={}", resolved, zoneId, contextId);
            return resolved;

        } catch (WebClientResponseException e) {
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
