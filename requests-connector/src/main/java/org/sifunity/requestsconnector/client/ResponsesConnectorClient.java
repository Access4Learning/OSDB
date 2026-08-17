// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.client;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for communicating with the Responses Connector.
 * Provides methods for routing provider responses to consumer queues.
 * 
 * <p>This client is responsible for routing responses from providers to consumers
 * in the DELAYED_to_IMMEDIATE pattern. When the background worker receives a response
 * from a provider, it uses this client to forward the response to the Responses Connector,
 * which then enqueues it to the consumer's queue.</p>
 * 
 * <p>Requirements:</p>
 * <ul>
 *   <li>9.1: Call Responses Connector API when provider returns response</li>
 *   <li>9.2: Include consumer's queueId in the request</li>
 *   <li>9.3: Include original requestId for correlation</li>
 *   <li>9.4: Include provider's response payload</li>
 * </ul>
 */
@Component
@Slf4j
public class ResponsesConnectorClient {
    
    private final WebClient webClient;
    private final String baseUrl;
    private final String internalServiceToken;
    
    /**
     * Constructs a ResponsesConnectorClient with the configured WebClient and base URL.
     * 
     * <p>Uses {@code WebClient.create()} directly instead of the injected builder to avoid
     * inheriting provider-facing exchange filters (e.g., CampusAPI Bearer token injection)
     * that should only apply to outbound provider calls, not internal service-to-service calls.</p>
     * 
     * @param baseUrl Responses Connector base URL from configuration
     * @param internalServiceToken internal service token for authenticating internal calls
     */
    public ResponsesConnectorClient(
            @Value("${responses.connector.url}") String baseUrl,
            @Value("${broker.internal-service-token:}") String internalServiceToken) {
        this.webClient = WebClient.create(baseUrl);
        this.baseUrl = baseUrl;
        this.internalServiceToken = internalServiceToken;
    }
    
    /**
     * Routes a successful provider response to the consumer's queue.
     * 
     * <p>This method calls the Responses Connector API to deliver a provider's response
     * to the consumer's queue. The response is correlated with the original request using
     * the requestId, and the consumer's queueId determines where the response is delivered.</p>
     * 
     * <p>This method is used in the DELAYED_to_IMMEDIATE pattern when the background worker
     * receives a successful response from a provider after making a synchronous HTTP call.</p>
     * 
     * @param environmentId the environment ID from the request context
     * @param environmentName the environment name from the request context
     * @param consumerQueueId the UUID of the consumer's queue where the response should be delivered
     * @param requestId the UUID of the original request for correlation
     * @param response the SIF message envelope containing the provider's response
     * @throws org.springframework.web.reactive.function.client.WebClientResponseException 
     *         if Responses Connector returns an error (e.g., 503 unavailable)
     */
    public void routeResponse(UUID environmentId, String environmentName,
            String domainId, String solutionId,
            UUID consumerQueueId, String requestId, SifMessageEnvelope<?> response,
            String relativeServicePath) {
        log.debug("Routing response to consumer: queueId={}, requestId={}, messageId={}", 
            consumerQueueId, requestId, response.getMessageId());
        
        try {
            // Build ResponseDTO from SifMessageEnvelope
            java.util.HashMap<String, Object> responseDto = new java.util.HashMap<>();
            responseDto.put("serviceName", response.getServiceName());
            responseDto.put("serviceType", response.getServiceType().name());
            if (response.getPayload() != null) {
                responseDto.put("payload", response.getPayload());
            }
            if (response.getHeaders() != null && !response.getHeaders().isEmpty()) {
                responseDto.put("headers", response.getHeaders());
            }
            
            var requestSpec = webClient.post()
                .uri("/internal/responses")
                .header("X-Internal-Service-Token", internalServiceToken)
                .header("X-Environment-Id", environmentId.toString())
                .header("X-Environment-Name", environmentName)
                .header("queueId", consumerQueueId.toString())
                .header("requestId", requestId.toString())
                .header("domainId", domainId)
                .header("solutionId", solutionId);
            
            if (relativeServicePath != null) {
                requestSpec = requestSpec.header("relativeServicePath", relativeServicePath);
            }
            
            requestSpec.bodyValue(responseDto)
                .retrieve()
                .toBodilessEntity()
                .block();
            
            log.info("Successfully routed response: queueId={}, requestId={}, messageId={}", 
                consumerQueueId, requestId, response.getMessageId());
            
        } catch (Exception e) {
            log.error("Failed to route response: queueId={}, requestId={}, messageId={}", 
                consumerQueueId, requestId, response.getMessageId(), e);
            throw e;
        }
    }
    
    /**
     * Routes an error response to the consumer's queue.
     * 
     * <p>This method calls the Responses Connector API to deliver an error message
     * to the consumer's queue. This is used when the provider call fails after all
     * retry attempts have been exhausted in the DELAYED_to_IMMEDIATE pattern.</p>
     * 
     * <p>The error message describes why the request failed (e.g., "Provider call failed
     * after 3 retries: Connection timeout").</p>
     * 
     * @param environmentId the environment ID from the request context
     * @param environmentName the environment name from the request context
     * @param consumerQueueId the UUID of the consumer's queue where the error should be delivered
     * @param requestId the UUID of the original request for correlation
     * @param serviceName the name of the service (e.g., "StudentPersonals")
     * @param errorMessage a descriptive error message explaining the failure
     * @throws org.springframework.web.reactive.function.client.WebClientResponseException 
     *         if Responses Connector returns an error (e.g., 503 unavailable)
     */
    public void routeErrorResponse(UUID environmentId, String environmentName,
            String domainId, String solutionId,
            UUID consumerQueueId, String requestId, String serviceName, String errorMessage) {
        log.debug("Routing error response to consumer: queueId={}, requestId={}, serviceName={}, error={}", 
            consumerQueueId, requestId, serviceName, errorMessage);
        
        try {
            // Build SIF-compliant error detail. Field names follow SifErrorResponse: "code" is the
            // HTTP status integer and "subCode" the internal identifier — sending the identifier as
            // "code" (as this previously did) leaves the receiver with no status and no subCode.
            Map<String, Object> sifError = Map.of(
                "code", HttpStatus.BAD_GATEWAY.value(),
                "subCode", "PROVIDER_ERROR",
                "message", errorMessage,
                "scope", "PROVIDER"
            );

            Map<String, Object> errorResponseDto = Map.of("error", sifError);
            
            webClient.post()
                .uri("/internal/responses/error")
                .header("X-Internal-Service-Token", internalServiceToken)
                .header("X-Environment-Id", environmentId.toString())
                .header("X-Environment-Name", environmentName)
                .header("queueId", consumerQueueId.toString())
                .header("requestId", requestId.toString())
                .header("domainId", domainId)
                .header("solutionId", solutionId)
                .bodyValue(errorResponseDto)
                .retrieve()
                .toBodilessEntity()
                .block();
            
            log.info("Successfully routed error response: queueId={}, requestId={}", 
                consumerQueueId, requestId);
            
        } catch (Exception e) {
            log.error("Failed to route error response: queueId={}, requestId={}", 
                consumerQueueId, requestId, e);
            throw e;
        }
    }
}
