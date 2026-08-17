// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.service;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.extension.ProviderRequestContext;
import org.sifunity.broker.common.extension.ProviderRequestInterceptor;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.HeaderSanitizer;
import org.sifunity.broker.common.util.TracingUtilsBase;
import org.sifunity.requestsconnector.client.ProviderHttpClient;
import org.sifunity.requestsconnector.dto.ImmediateRequestDTO;
import org.sifunity.requestsconnector.dto.RequestResponseDTO;
import org.sifunity.requestsconnector.util.MessageBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.sifunity.broker.common.dto.ProtocolDTO;

/**
 * Handler for IMMEDIATE (synchronous) request pattern.
 * 
 * <p>This service handles the IMMEDIATE request pattern where consumers send requests
 * and expect immediate synchronous responses. The handler builds a SIF message envelope,
 * forwards it to the provider's endpoint, and returns the provider's response to the
 * consumer in the same HTTP connection.</p>
 * 
 * <p>The IMMEDIATE pattern flow:</p>
 * <ol>
 *   <li>Receive IMMEDIATE request from consumer (via RoutingService)</li>
 *   <li>Build SIF message envelope with all required headers</li>
 *   <li>Forward request to provider's endpoint using HTTP</li>
 *   <li>Wait for provider's synchronous response</li>
 *   <li>Return response to consumer in same HTTP connection</li>
 * </ol>
 * 
 * <p>Requirements:</p>
 * <ul>
 *   <li>1.2: Forward request to provider's endpoint using HTTP</li>
 *   <li>1.3: Return provider's response to consumer in same HTTP connection</li>
 *   <li>6.1: Create SIF message envelope with all required headers</li>
 *   <li>6.2: Include messageId, timestamp, messageType, serviceName, serviceType</li>
 *   <li>6.5: Preserve original request payload</li>
 * </ul>
 */
@Service
@Slf4j
public class ImmediateRequestHandler {
    
    private final ProviderHttpClient providerClient;
    private final MessageBuilder messageBuilder;
    private final HeaderSanitizer headerSanitizer;
    private final TracingUtilsBase tracingUtils;
    private final List<ProviderRequestInterceptor> providerInterceptors;
    
    @Autowired
    public ImmediateRequestHandler(
            ProviderHttpClient providerClient,
            MessageBuilder messageBuilder,
            HeaderSanitizer headerSanitizer,
            TracingUtilsBase tracingUtils,
            @Autowired(required = false) List<ProviderRequestInterceptor> providerInterceptors) {
        this.providerClient = providerClient;
        this.messageBuilder = messageBuilder;
        this.headerSanitizer = headerSanitizer;
        this.tracingUtils = tracingUtils;
        this.providerInterceptors = providerInterceptors != null ? providerInterceptors : List.of();
        
        if (!this.providerInterceptors.isEmpty()) {
            log.info("ImmediateRequestHandler: {} provider request interceptor(s) registered", this.providerInterceptors.size());
        }
    }
    
    /**
     * Handles an IMMEDIATE request by forwarding it to the provider and returning the response.
     * 
     * <p>This method orchestrates the IMMEDIATE request flow:</p>
     * <ol>
     *   <li>Builds a SIF message envelope from the request DTO</li>
     *   <li>Forwards the envelope to the provider's endpoint</li>
     *   <li>Receives the provider's response envelope</li>
     *   <li>Constructs a response DTO with the provider's response data</li>
     * </ol>
     * 
     * <p>The method preserves the original request payload and includes all required
     * SIF headers in the message envelope. The provider's response is returned
     * synchronously to the consumer.</p>
     * 
     * <p>Error handling:</p>
     * <ul>
     *   <li>Provider unavailable: ProviderHttpClient throws exception (handled by caller)</li>
     *   <li>Provider timeout: ProviderHttpClient throws timeout exception (handled by caller)</li>
     *   <li>Provider error response: Response is forwarded to consumer unchanged</li>
     * </ul>
     * 
     * @param environmentId the environment ID from the request context
     * @param environmentName the environment name from the request context
     * @param provider the provider registration containing endpoint and capabilities
     * @param request the IMMEDIATE request DTO from the consumer
     * @return a response DTO containing the provider's response data
     * @throws org.springframework.web.reactive.function.client.WebClientRequestException
     *         if the provider endpoint is unreachable or times out
     * @throws org.springframework.web.reactive.function.client.WebClientResponseException
     *         if the provider returns an error response (forwarded to consumer)
     */
    public RequestResponseDTO handleRequest(
            UUID environmentId,
            String environmentName,
            String domainId,
            String solutionId,
            ProviderRegistrationDTO provider,
            ImmediateRequestDTO request,
            Map<String, String> passthroughHeaders) {
        
        log.info("Handling IMMEDIATE request: serviceName={}, environmentId={}, providerEndpoint={}", 
            request.getServiceName(), environmentId, ProtocolDTO.locationOf(provider.endPoint()));
        
        // Build SIF message envelope with all required headers
        SifMessageEnvelope<Object> envelope = tracingUtils.traceOperation(
            "sif.message.build",
            Map.of(
                "sif.service_name", request.getServiceName(),
                "sif.provider_endpoint", ProtocolDTO.locationOf(provider.endPoint())
            ),
            () -> {
                return messageBuilder.buildRequestEnvelope(
                    environmentId, 
                    environmentName, 
                    request,
                    passthroughHeaders);
            }
        );
        
        log.debug("Built request envelope: messageId={}, serviceName={}, serviceType={}", 
            envelope.getMessageId(), envelope.getServiceName(), envelope.getServiceType());
        
        // Invoke provider request interceptors before the outbound call
        ProviderRequestContext interceptorContext = new ProviderRequestContext(
                domainId, solutionId, provider.environmentId(), ProtocolDTO.locationOf(provider.endPoint()), passthroughHeaders, true);
        providerInterceptors.forEach(i -> i.beforeProviderRequest(interceptorContext));
        
        try {
            // Forward request to provider endpoint with passthrough headers
            SifMessageEnvelope<?> response = tracingUtils.traceOperation(
                "sif.provider.call",
                Map.of(
                    "sif.service_name", request.getServiceName(),
                    "sif.provider_endpoint", ProtocolDTO.locationOf(provider.endPoint()),
                    "sif.message_id", envelope.getMessageId() != null ? envelope.getMessageId().toString() : "unknown",
                    "sif.provider_name", provider.providerName() != null ? provider.providerName() : "unknown"
                ),
                () -> {
                    return providerClient.sendRequest(
                        ProtocolDTO.locationOf(provider.endPoint()), 
                        envelope,
                        passthroughHeaders);
                }
            );
            
            log.info("Received response from provider: messageId={}, responseMessageId={}", 
                envelope.getMessageId(), response.getMessageId());
            
            // Sanitize provider response headers before returning to consumer
            Map<String, String> sanitizedResponseHeaders = headerSanitizer.sanitize(response.getHeaders());
            
            log.debug("Sanitized provider response headers: count={}", sanitizedResponseHeaders.size());
            
            // Build response DTO with provider's response data and sanitized headers
            return RequestResponseDTO.builder()
                .messageId(response.getMessageId())
                .timestamp(response.getTimestamp())
                .payload(response.getPayload())
                .headers(sanitizedResponseHeaders)
                .build();
        } finally {
            // Clean up interceptor state to prevent ThreadLocal leaks
            providerInterceptors.forEach(i -> i.afterProviderRequest(interceptorContext));
        }
    }
}
