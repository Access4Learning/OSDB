// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.service;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.common.util.TracingUtilsBase;
import org.sifunity.requestsconnector.cache.ProviderRegistryCache;
import org.sifunity.requestsconnector.dto.DelayedRequestDTO;
import org.sifunity.requestsconnector.dto.ImmediateRequestDTO;
import org.sifunity.requestsconnector.dto.RequestResponseDTO;
import org.sifunity.requestsconnector.exception.NoProviderException;
import org.sifunity.requestsconnector.exception.UnsupportedPatternException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import org.sifunity.broker.common.dto.ProtocolDTO;

/**
 * Service responsible for routing consumer requests to providers based on message exchange patterns.
 * 
 * <p>This service orchestrates the routing decision process:</p>
 * <ol>
 *   <li>Queries the provider registry (cached) to find the matching provider</li>
 *   <li>Validates that the provider supports the required message exchange pattern</li>
 *   <li>Delegates to the appropriate handler based on the pattern:
 *     <ul>
 *       <li>IMMEDIATE requests → ImmediateRequestHandler</li>
 *       <li>DELAYED requests → DelayedRequestHandler (with sub-pattern detection)</li>
 *     </ul>
 *   </li>
 * </ol>
 * 
 * <p><strong>IMMEDIATE Pattern Routing:</strong></p>
 * <ul>
 *   <li>Validates provider supports IMMEDIATE pattern</li>
 *   <li>Delegates to ImmediateRequestHandler for synchronous request/response</li>
 * </ul>
 * 
 * <p><strong>DELAYED Pattern Routing:</strong></p>
 * <ul>
 *   <li>Detects sub-pattern based on provider capabilities:
 *     <ul>
 *       <li>Provider supports DELAYED → DELAYED_to_DELAYED (enqueue to provider's queue)</li>
 *       <li>Provider supports only IMMEDIATE → DELAYED_to_IMMEDIATE (proxy via background worker)</li>
 *     </ul>
 *   </li>
 *   <li>Delegates to DelayedRequestHandler with appropriate sub-pattern</li>
 * </ul>
 * 
 * <p><strong>Requirements:</strong></p>
 * <ul>
 *   <li>1.1: Query provider registry for IMMEDIATE requests</li>
 *   <li>1.5: Return HTTP 404 when no provider found</li>
 *   <li>2.1: Call Environment Service provider registry API</li>
 *   <li>2.2: Extract provider endpoint and supported patterns</li>
 *   <li>3.1: Query provider registry to determine capabilities for DELAYED requests</li>
 *   <li>3.2: Use DELAYED_to_DELAYED when provider supports DELAYED</li>
 *   <li>3.3: Use DELAYED_to_IMMEDIATE when provider supports only IMMEDIATE</li>
 *   <li>3.4: Return HTTP 400 when provider supports neither pattern</li>
 *   <li>3.5: Determine sub-pattern based on provider's supportedPatterns field</li>
 * </ul>
 * 
 * @see ImmediateRequestHandler
 * @see DelayedRequestHandler
 * @see ProviderRegistryCache
 */
@Service
@Slf4j
public class RoutingService {
    
    private final ProviderRegistryCache providerCache;
    private final ImmediateRequestHandler immediateHandler;
    private final DelayedRequestHandler delayedHandler;
    private final TracingUtilsBase tracingUtils;
    private final ConsumerQueueOwnershipValidator queueOwnershipValidator;
    
    /**
     * Constructs a RoutingService with required dependencies.
     * 
     * @param providerCache cache for provider registry lookups
     * @param immediateHandler handler for IMMEDIATE request pattern
     * @param delayedHandler handler for DELAYED request pattern
     * @param tracingUtils utility for creating custom tracing spans
     * @param queueOwnershipValidator validates consumer queueId ownership
     */
    public RoutingService(
            ProviderRegistryCache providerCache,
            ImmediateRequestHandler immediateHandler,
            DelayedRequestHandler delayedHandler,
            TracingUtilsBase tracingUtils,
            ConsumerQueueOwnershipValidator queueOwnershipValidator) {
        this.providerCache = providerCache;
        this.immediateHandler = immediateHandler;
        this.delayedHandler = delayedHandler;
        this.tracingUtils = tracingUtils;
        this.queueOwnershipValidator = queueOwnershipValidator;
    }
    
    /**
     * Routes an IMMEDIATE request to the appropriate provider.
     * 
     * <p>This method orchestrates IMMEDIATE request routing:</p>
     * <ol>
     *   <li>Queries provider registry (cached) with serviceName, zoneId, contextId</li>
     *   <li>Throws NoProviderException if no provider found (HTTP 404)</li>
     *   <li>Validates provider supports IMMEDIATE pattern</li>
     *   <li>Throws UnsupportedPatternException if IMMEDIATE not supported (HTTP 400)</li>
     *   <li>Delegates to ImmediateRequestHandler for synchronous processing</li>
     * </ol>
     * 
     * <p><strong>Error Handling:</strong></p>
     * <ul>
     *   <li>No provider found: Throws NoProviderException (HTTP 404)</li>
     *   <li>Provider doesn't support IMMEDIATE: Throws UnsupportedPatternException (HTTP 400)</li>
     *   <li>Provider unavailable: Exception propagated from ImmediateRequestHandler (HTTP 503)</li>
     *   <li>Provider timeout: Exception propagated from ImmediateRequestHandler (HTTP 504)</li>
     * </ul>
     * 
     * <p><strong>Requirements:</strong> 1.1, 1.5, 2.1, 2.2</p>
     * 
     * @param environmentId the environment ID from the request context
     * @param environmentName the environment name from the request context
     * @param domainId the domain ID for interceptor context
     * @param solutionId the solution ID for interceptor context
     * @param zoneId the zone ID for routing
     * @param contextId the context ID for routing (may be null)
     * @param request the IMMEDIATE request DTO from the consumer
     * @return a response DTO containing the provider's response
     * @throws NoProviderException if no provider is registered for the service/zone/context
     * @throws UnsupportedPatternException if the provider doesn't support IMMEDIATE pattern
     */
    public RequestResponseDTO routeImmediateRequest(
            UUID environmentId,
            String environmentName,
            String domainId,
            String solutionId,
            String zoneId,
            String contextId,
            ImmediateRequestDTO request,
            Map<String, String> passthroughHeaders) {
        
        // Create custom span for SIF request processing with metadata
        return tracingUtils.traceOperation(
            "sif.request.immediate",
            Map.of(
                "sif.message_type", "REQUEST",
                "sif.service_name", request.getServiceName(),
                "sif.zone_id", zoneId,
                "sif.context_id", contextId != null ? contextId : "DEFAULT",
                "sif.pattern", "IMMEDIATE"
            ),
            () -> {
                log.info("Routing IMMEDIATE request: serviceName={}, zoneId={}, contextId={}, environmentId={}", 
                    request.getServiceName(), zoneId, contextId, environmentId);
                
                // Query provider registry (cached)
                ProviderRegistrationDTO provider = tracingUtils.traceOperation(
                    "sif.provider.lookup",
                    Map.of(
                        "sif.service_name", request.getServiceName(),
                        "sif.zone_id", zoneId,
                        "sif.context_id", contextId != null ? contextId : "DEFAULT"
                    ),
                    () -> providerCache.getProvider(
                            domainId, solutionId, request.getServiceName(), zoneId, contextId)
                        .orElseThrow(() -> new NoProviderException(
                            request.getServiceName(), zoneId, contextId))
                );
                
                log.debug("Found provider: endpoint={}, supportedRequestTypes={}", 
                    ProtocolDTO.locationOf(provider.endPoint()), provider.supportedRequestTypes());
                
                // Validate provider supports IMMEDIATE pattern
                if (provider.supportedRequestTypes().supportedRequestType().stream()
                        .noneMatch(srt -> srt.requestType() == RequestType.IMMEDIATE)) {
                    log.error("Provider does not support IMMEDIATE pattern: serviceName={}, endpoint={}, supportedRequestTypes={}", 
                        request.getServiceName(), ProtocolDTO.locationOf(provider.endPoint()), provider.supportedRequestTypes());
                    throw new UnsupportedPatternException(
                        "Provider does not support IMMEDIATE pattern");
                }
                
                log.info("Routing to IMMEDIATE handler: serviceName={}, endpoint={}", 
                    request.getServiceName(), ProtocolDTO.locationOf(provider.endPoint()));
                
                // Delegate to IMMEDIATE handler
                return immediateHandler.handleRequest(
                    environmentId, environmentName, domainId, solutionId, provider, request, passthroughHeaders);
            }
        );
    }
    
    /**
     * Routes a DELAYED request to the appropriate provider with sub-pattern detection.
     * 
     * <p>This method orchestrates DELAYED request routing with intelligent sub-pattern selection:</p>
     * <ol>
     *   <li>Queries provider registry (cached) with serviceName, zoneId, contextId</li>
     *   <li>Throws NoProviderException if no provider found (HTTP 404)</li>
     *   <li>Determines sub-pattern based on provider capabilities:
     *     <ul>
     *       <li>Provider supports DELAYED → DELAYED_to_DELAYED sub-pattern</li>
     *       <li>Provider supports only IMMEDIATE → DELAYED_to_IMMEDIATE sub-pattern</li>
     *       <li>Provider supports neither → Throws UnsupportedPatternException</li>
     *     </ul>
     *   </li>
     *   <li>Delegates to DelayedRequestHandler with appropriate sub-pattern method</li>
     * </ol>
     * 
     * <p><strong>Sub-Pattern Selection Logic:</strong></p>
     * <ul>
     *   <li><b>DELAYED_to_DELAYED</b>: Used when provider supports DELAYED pattern.
     *       Request is enqueued to provider's request subscription queue.</li>
     *   <li><b>DELAYED_to_IMMEDIATE</b>: Used when provider supports only IMMEDIATE pattern.
     *       Request metadata is stored for background proxy processing.</li>
     * </ul>
     * 
     * <p><strong>Error Handling:</strong></p>
     * <ul>
     *   <li>No provider found: Throws NoProviderException (HTTP 404)</li>
     *   <li>Provider supports neither IMMEDIATE nor DELAYED: Throws UnsupportedPatternException (HTTP 400)</li>
     *   <li>No subscription found (DELAYED_to_DELAYED): Exception propagated from DelayedRequestHandler (HTTP 404)</li>
     *   <li>Queue Service unavailable: Exception propagated from DelayedRequestHandler (HTTP 503)</li>
     *   <li>Redis unavailable: Exception propagated from DelayedRequestHandler (HTTP 503)</li>
     * </ul>
     * 
     * <p><strong>Requirements:</strong> 3.1, 3.2, 3.3, 3.4, 3.5</p>
     * 
     * @param environmentId the environment ID from the request context
     * @param environmentName the environment name from the request context
     * @param zoneId the zone ID for routing
     * @param contextId the context ID for routing (may be null)
     * @param queueId the consumer's queue ID for receiving the response
     * @param request the DELAYED request DTO from the consumer
     * @return a response DTO with HTTP 202 status, messageId, and queueId
     * @throws NoProviderException if no provider is registered for the service/zone/context
     * @throws UnsupportedPatternException if the provider supports neither IMMEDIATE nor DELAYED patterns
     */
    public RequestResponseDTO routeDelayedRequest(
            UUID environmentId,
            String environmentName,
            String domainId,
            String solutionId,
            String zoneId,
            String contextId,
            UUID queueId,
            DelayedRequestDTO request,
            Map<String, String> passthroughHeaders,
            String httpMethod,
            String relativeServicePath,
            String requestId) {
        
        // Create custom span for SIF request processing with metadata
        return tracingUtils.traceOperation(
            "sif.request.delayed",
            Map.of(
                "sif.message_type", "REQUEST",
                "sif.service_name", request.getServiceName(),
                "sif.zone_id", zoneId,
                "sif.context_id", contextId != null ? contextId : "DEFAULT",
                "sif.pattern", "DELAYED",
                "sif.queue_id", queueId.toString()
            ),
            () -> {
                log.info("Routing DELAYED request: serviceName={}, zoneId={}, contextId={}, environmentId={}, consumerQueueId={}", 
                    request.getServiceName(), zoneId, contextId, environmentId, queueId);
                
                // Validate queue ownership FIRST — reject unauthorized before revealing provider info
                queueOwnershipValidator.requireOwnership(domainId, solutionId, queueId, environmentId);
                
                // Query provider registry (cached)
                ProviderRegistrationDTO provider = providerCache.getProvider(
                        domainId, solutionId, request.getServiceName(), zoneId, contextId)
                    .orElseThrow(() -> new NoProviderException(
                        request.getServiceName(), zoneId, contextId));
                
                log.debug("Found provider: endpoint={}, supportedRequestTypes={}", 
                    ProtocolDTO.locationOf(provider.endPoint()), provider.supportedRequestTypes());
                
                // Determine sub-pattern based on provider capabilities
                boolean supportsDelayed = provider.supportedRequestTypes().supportedRequestType().stream()
                        .anyMatch(srt -> srt.requestType() == RequestType.DELAYED);
                boolean supportsImmediate = provider.supportedRequestTypes().supportedRequestType().stream()
                        .anyMatch(srt -> srt.requestType() == RequestType.IMMEDIATE);
                
                if (supportsDelayed) {
                    // DELAYED to DELAYED: Provider supports DELAYED pattern
                    // Send request to provider's request subscription queue
                    log.info("Using DELAYED_to_DELAYED sub-pattern: serviceName={}, providerEndpoint={}", 
                        request.getServiceName(), ProtocolDTO.locationOf(provider.endPoint()));
                    
                    return delayedHandler.handleDelayedToDelayed(
                        environmentId, environmentName, domainId, solutionId, provider, queueId, request, passthroughHeaders, relativeServicePath, requestId);
                    
                } else if (supportsImmediate) {
                    // DELAYED to IMMEDIATE: Provider supports only IMMEDIATE pattern
                    // Store request metadata for background proxy processing
                    log.info("Using DELAYED_to_IMMEDIATE sub-pattern: serviceName={}, providerEndpoint={}", 
                        request.getServiceName(), ProtocolDTO.locationOf(provider.endPoint()));
                    
                    return delayedHandler.handleDelayedToImmediate(
                        environmentId, environmentName, domainId, solutionId, provider, queueId, request, passthroughHeaders, httpMethod, relativeServicePath, requestId);
                    
                } else {
                    // Provider supports neither IMMEDIATE nor DELAYED patterns
                    log.error("Provider does not support request patterns: serviceName={}, endpoint={}, supportedRequestTypes={}", 
                        request.getServiceName(), ProtocolDTO.locationOf(provider.endPoint()), provider.supportedRequestTypes());
                    
                    throw new UnsupportedPatternException(
                        "Provider does not support request patterns");
                }
            }
        );
    }
}
