// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.sifunity.requestsconnector.exception.ServiceUnavailableException;

import java.util.Optional;
import java.util.UUID;
import org.sifunity.broker.common.dto.ProtocolDTO;

/**
 * HTTP client for communicating with the Environment Service.
 * Provides methods for provider registry lookup operations.
 * 
 * <p>This client is responsible for querying the Environment Service to find providers
 * that match specific service, zone, and context criteria. The provider registry lookup
 * is a critical operation for request routing decisions.</p>
 * 
 * <p>Requirements:</p>
 * <ul>
 *   <li>2.1: Query provider registry with serviceName, zoneId, and contextId</li>
 *   <li>2.2: Extract provider endpoint and supported patterns from registry response</li>
 * </ul>
 */
@Component
@Slf4j
public class EnvironmentServiceClient {
    
    private final WebClient webClient;
    private final String baseUrl;

    @Value("${broker.internal-service-token:}")
    private String internalServiceToken;
    
    /**
     * Constructs an EnvironmentServiceClient with the configured WebClient and base URL.
     * 
     * @param webClientBuilder WebClient builder configured with timeouts
     * @param baseUrl Environment Service base URL from configuration
     */
    public EnvironmentServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${environment.service.url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.baseUrl = baseUrl;
    }
    
    /**
     * Looks up a provider registration by service name, zone ID, and context ID.
     * Delegates to the 4-param overload with serviceType=null (backward compatible).
     * 
     * @param serviceName the name of the service to look up (e.g., "StudentPersonal")
     * @param zoneId the zone ID for the lookup (e.g., "production")
     * @param contextId the context ID for the lookup (optional, can be null)
     * @return Optional containing the provider registration if found, empty otherwise
     * @throws org.springframework.web.reactive.function.client.WebClientResponseException 
     *         if Environment Service returns an error (e.g., 503 Service Unavailable)
     */
    public Optional<ProviderRegistrationDTO> lookupProvider(
            String domainId, String solutionId, String serviceName, String zoneId, String contextId) {
        return lookupProvider(domainId, solutionId, serviceName, zoneId, contextId, null);
    }
    
    /**
     * Looks up a provider registration by service name, zone ID, context ID, and service type.
     * 
     * <p>This method queries the Environment Service provider registry to find a provider
     * that matches the specified service, zone, context, and optionally service type. The
     * lookup is used during request routing to determine which provider endpoint should
     * handle the request.</p>
     * 
     * <p>When serviceType is provided, it is included as a query parameter to disambiguate
     * providers registered for the same serviceName but different service types (e.g.,
     * OBJECT vs XQUERYTEMPLATE).</p>
     * 
     * <p>The method returns an Optional to handle cases where no provider is registered
     * for the given criteria. The caller is responsible for handling the empty case
     * (typically by returning HTTP 404 Not Found).</p>
     * 
     * @param serviceName the name of the service to look up (e.g., "StudentPersonal")
     * @param zoneId the zone ID for the lookup (e.g., "production")
     * @param contextId the context ID for the lookup (optional, can be null)
     * @param serviceType the service type filter (e.g., "XQUERYTEMPLATE"), or null for default behavior
     * @return Optional containing the provider registration if found, empty otherwise
     * @throws org.springframework.web.reactive.function.client.WebClientResponseException 
     *         if Environment Service returns an error (e.g., 503 Service Unavailable)
     */
    public Optional<ProviderRegistrationDTO> lookupProvider(
            String domainId, String solutionId, String serviceName, String zoneId, String contextId, String serviceType) {

        log.debug("Looking up provider: domainId={}, solutionId={}, serviceName={}, zoneId={}, contextId={}, serviceType={}",
            domainId, solutionId, serviceName, zoneId, contextId, serviceType);

        try {
            Optional<ProviderRegistrationDTO> result = webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                        .path("/internal/provider-lookup")
                        .queryParam("serviceName", serviceName)
                        .queryParam("zoneId", zoneId)
                        .queryParam("domainId", domainId)
                        .queryParam("solutionId", solutionId);

                    if (contextId != null && !contextId.isEmpty()) {
                        builder.queryParam("contextId", contextId);
                    }

                    if (serviceType != null && !serviceType.isEmpty()) {
                        builder.queryParam("serviceType", serviceType);
                    }

                    return builder.build();
                })
                .header("X-Internal-Service-Token", internalServiceToken)
                .retrieve()
                .bodyToMono(ProviderRegistrationDTO.class)
                .blockOptional();
            
            if (result.isPresent()) {
                log.info("Found provider: serviceName={}, zoneId={}, contextId={}, serviceType={}, endpoint={}", 
                    serviceName, zoneId, contextId, serviceType, ProtocolDTO.locationOf(result.get().endPoint()));
            } else {
                log.info("No provider found: serviceName={}, zoneId={}, contextId={}, serviceType={}", 
                    serviceName, zoneId, contextId, serviceType);
            }
            
            return result;
            
        } catch (WebClientResponseException.NotFound e) {
            // Environment-service returns 404 when no provider is registered — this is normal
            log.info("No provider found (404): serviceName={}, zoneId={}, contextId={}, serviceType={}", 
                serviceName, zoneId, contextId, serviceType);
            return Optional.empty();
        } catch (WebClientRequestException e) {
            // Transport-level failure: the environment-service could not be reached at all.
            // Surface as a clear 503 rather than a generic 500.
            log.error("environment-service unreachable during provider lookup: "
                + "serviceName={}, zoneId={}, contextId={}, serviceType={}",
                serviceName, zoneId, contextId, serviceType, e);
            throw new ServiceUnavailableException("environment-service", e);
        } catch (Exception e) {
            log.error("Failed to lookup provider: serviceName={}, zoneId={}, contextId={}, serviceType={}", 
                serviceName, zoneId, contextId, serviceType, e);
            throw e;
        }
    }
    
    /**
     * Looks up an entity tag value from the Environment Service internal API.
     *
     * <p>Calls the internal entity tag endpoint to retrieve a tag value for a given
     * entity type, entity ID, and tag key. Used by {@code LegacyTagLookupService} to
     * check the {@code SIF_LEGACY_MATRIX_PARAMS} feature toggle per environment.</p>
     *
     * @param domainId   the domain ID for the request path
     * @param solutionId the solution ID for the request path
     * @param entityType the entity type (e.g., "ENVIRONMENT")
     * @param entityId   the entity UUID
     * @param tagKey     the tag key to look up (e.g., "SIF_LEGACY_MATRIX_PARAMS")
     * @return Optional containing the tag value if found, empty if tag does not exist
     */
    public Optional<String> getEntityTagValue(String domainId, String solutionId,
            String entityType, UUID entityId, String tagKey) {

        log.debug("Looking up entity tag: domainId={}, solutionId={}, entityType={}, entityId={}, tagKey={}",
            domainId, solutionId, entityType, entityId, tagKey);

        try {
            String tagValue = webClient.get()
                .uri("/internal/{domainId}/{solutionId}/entity-tags?entityType={entityType}&entityId={entityId}&tagKey={tagKey}",
                    domainId, solutionId, entityType, entityId, tagKey)
                .header("X-Internal-Service-Token", internalServiceToken)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            log.debug("Entity tag found: tagKey={}, tagValue={}", tagKey, tagValue);
            return Optional.ofNullable(tagValue);

        } catch (WebClientResponseException.NotFound e) {
            log.debug("Entity tag not found: entityType={}, entityId={}, tagKey={}", entityType, entityId, tagKey);
            return Optional.empty();
        } catch (WebClientResponseException e) {
            log.error("Environment service returned error looking up entity tag: status={}, entityType={}, entityId={}, tagKey={}",
                e.getStatusCode(), entityType, entityId, tagKey);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to look up entity tag: entityType={}, entityId={}, tagKey={}", entityType, entityId, tagKey, e);
            return Optional.empty();
        }
    }

    /**
     * Resolves a zone ID and context ID string pair to a context UUID.
     *
     * <p>Calls the environment-service's context listing endpoint for the given zone
     * and searches for a context matching the provided contextId string. Returns the
     * internal UUID of the matching context.</p>
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
