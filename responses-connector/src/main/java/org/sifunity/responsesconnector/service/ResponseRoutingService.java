// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.service;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.RelativeServicePathFormatter;
import org.sifunity.broker.common.util.TracingUtilsBase;
import org.sifunity.responsesconnector.client.QueueServiceClient;
import org.sifunity.responsesconnector.dto.ResponseDTO;
import org.sifunity.responsesconnector.dto.ResponseResultDTO;
import org.sifunity.responsesconnector.util.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Service for routing provider responses to consumer queues.
 * <p>
 * This service is responsible for receiving responses from providers and the Requests Connector,
 * constructing SIF message envelopes, and enqueuing them to consumer queues via the Queue Service.
 * It handles both success and error responses, maintains request correlation, and provides
 * comprehensive logging for all routing operations.
 * </p>
 * 
 * <p><b>Key Responsibilities:</b></p>
 * <ul>
 *   <li>Route provider responses to consumer queues</li>
 *   <li>Route error responses to consumer queues</li>
 *   <li>Route internal responses from Requests Connector</li>
 *   <li>Construct SIF-compliant message envelopes</li>
 *   <li>Maintain request correlation (requestId)</li>
 *   <li>Format relativeServicePath based on consumer's legacy preference</li>
 *   <li>Always include zoneId and contextId headers on delivered messages</li>
 *   <li>Log all routing operations with context</li>
 * </ul>
 * 
 * <p><b>Response Flow:</b></p>
 * <ol>
 *   <li>Receive response with queueId and requestId</li>
 *   <li>Look up consumer environment's legacy preference</li>
 *   <li>Format relativeServicePath using RelativeServicePathFormatter</li>
 *   <li>Build SIF message envelope using MessageBuilder</li>
 *   <li>Ensure zoneId and contextId are always in envelope headers</li>
 *   <li>Enqueue message to consumer's queue via QueueServiceClient</li>
 *   <li>Return ResponseResultDTO with messageId and status</li>
 * </ol>
 * 
 * <p><b>Error Handling:</b></p>
 * <ul>
 *   <li>QueueNotFoundException → propagated to controller (404)</li>
 *   <li>QueueServiceUnavailableException → propagated to controller (503)</li>
 *   <li>All errors are logged with full context</li>
 * </ul>
 * 
 * <p><b>Requirements:</b> 3.1, 3.2, 3.3, 3.4, 5.1, 5.2, 5.3, 5.4, 5.5</p>
 * 
 * @see QueueServiceClient
 * @see MessageBuilder
 * @see LegacyTagLookupService
 * @see RelativeServicePathFormatter
 * @see ResponseDTO
 * @see ResponseResultDTO
 */
@Service
@Slf4j
public class ResponseRoutingService {
    
    private final QueueServiceClient queueClient;
    private final MessageBuilder messageBuilder;
    private final TracingUtilsBase tracingUtils;
    private final LegacyTagLookupService legacyTagLookupService;
    private final ResponseDestinationValidator destinationValidator;
    
    /**
     * Constructs a ResponseRoutingService with the required dependencies.
     * 
     * @param queueClient Client for communicating with the Queue Service
     * @param messageBuilder Utility for building SIF message envelopes
     * @param tracingUtils Utility for creating custom tracing spans
     * @param legacyTagLookupService Service for checking legacy matrix param preference
     * @param destinationValidator Gate asserting the destination queue's owner is entitled to the
     *                             object being delivered; applied to the PUBLIC ingress methods
     *                             only ({@link #routeResponse}), never to the internal ones
     */
    public ResponseRoutingService(QueueServiceClient queueClient,
                                 MessageBuilder messageBuilder,
                                 TracingUtilsBase tracingUtils,
                                 LegacyTagLookupService legacyTagLookupService,
                                 ResponseDestinationValidator destinationValidator) {
        this.queueClient = queueClient;
        this.messageBuilder = messageBuilder;
        this.tracingUtils = tracingUtils;
        this.legacyTagLookupService = legacyTagLookupService;
        this.destinationValidator = destinationValidator;
        log.info("ResponseRoutingService initialized");
    }
    
    /**
     * Routes a provider response to the consumer's queue.
     * <p>
     * This method handles responses from provider applications that come through the Gateway
     * with full environment context. It constructs a SIF message envelope with all required
     * headers and enqueues it to the consumer's queue via the Queue Service.
     * </p>
     * 
     * <p>Before enqueuing, the service:</p>
     * <ul>
     *   <li>Looks up the consumer environment's legacy preference via the queue owner</li>
     *   <li>Formats relativeServicePath using RelativeServicePathFormatter</li>
     *   <li>Ensures zoneId and contextId are always present as headers (Req 5.3)</li>
     * </ul>
     * 
     * @param domainId The domain ID from the request path
     * @param solutionId The solution ID from the request path
     * @param environmentId The environment ID from the request context (provider)
     * @param environmentName The environment name from the request context
     * @param queueId The UUID of the consumer's queue
     * @param requestId The original request ID for correlation
     * @param response The response DTO containing service details and payload
     * @param passthroughHeaders Sanitized headers from the inbound request
     * @param zoneId The resolved zone ID from gateway headers
     * @param contextId The resolved context ID from gateway headers
     * @param relativeServicePath The original relativeServicePath header
     * @return ResponseResultDTO with messageId, queueId, status, and timestamp
     * @throws org.sifunity.responsesconnector.exception.QueueNotFoundException if the queue does not exist
     * @throws org.sifunity.responsesconnector.exception.QueueServiceUnavailableException if the Queue Service is unavailable
     */
    public ResponseResultDTO routeResponse(
            String domainId, String solutionId,
            UUID environmentId, String environmentName,
            UUID queueId, String requestId,
            ResponseDTO response,
            Map<String, String> passthroughHeaders,
            String zoneId, String contextId,
            String relativeServicePath) {
        
        // Create custom span for SIF response processing with metadata
        return tracingUtils.traceOperation(
            "sif.response.route",
            Map.of(
                "sif.message_type", "RESPONSE",
                "sif.service_name", response.getServiceName(),
                "sif.service_type", response.getServiceType(),
                "sif.queue_id", queueId.toString(),
                "sif.request_id", requestId != null ? requestId.toString() : "none"
            ),
            () -> {
                log.info("Routing response to queue: queueId={}, requestId={}, serviceName={}, serviceType={}",
                    queueId, requestId, response.getServiceName(), response.getServiceType());

                // PUBLIC INGRESS GATE: the destination queue's owner must be entitled to this
                // object. Runs before the envelope is built or anything is enqueued, so an
                // unauthorised destination costs no work and reveals nothing. The internal path
                // (routeInternalResponse) is deliberately NOT gated — it is token-gated and its
                // queueId was already validated at request admission.
                destinationValidator.requireEntitledDestination(
                    domainId, solutionId, queueId, response.getServiceName(),
                    zoneId, contextId, environmentId);

                // Build SIF message envelope
                SifMessageEnvelope<?> envelope = messageBuilder.buildResponseEnvelope(
                    environmentId, environmentName, requestId, response, passthroughHeaders);
                
                // Enrich headers with formatted relativeServicePath and zoneId/contextId
                envelope = rebuildWithEnrichedHeaders(envelope, domainId, solutionId, queueId,
                        response.getServiceName(), zoneId, contextId, relativeServicePath);
                
                log.debug("Built response envelope: messageId={}, requestId={}, serviceName={}", 
                    envelope.getMessageId(), envelope.getRequestId(), envelope.getServiceName());
                
                // Enqueue to consumer's queue
                UUID messageId = queueClient.enqueueMessage(domainId, solutionId, queueId, envelope);
                
                log.info("Response routed successfully: messageId={}, queueId={}, requestId={}", 
                    messageId, queueId, requestId);
                
                return ResponseResultDTO.builder()
                    .messageId(messageId)
                    .queueId(queueId)
                    .status("DELIVERED")
                    .timestamp(Instant.now())
                    .build();
            }
        );
    }
    
    /**
     * Routes an internal response from the Requests Connector to the consumer's queue.
     * <p>
     * This method handles responses from the Requests Connector (DELAYED_to_IMMEDIATE pattern)
     * which don't have environment context headers. The relativeServicePath is already formatted
     * by the Requests Connector before forwarding.
     * </p>
     * 
     * @param domainId The domain ID
     * @param solutionId The solution ID
     * @param queueId The UUID of the consumer's queue
     * @param requestId The original request ID for correlation
     * @param relativeServicePath The pre-formatted relativeServicePath from Requests Connector
     * @param response The response DTO containing service details and payload
     * @return ResponseResultDTO with messageId and status
     * @throws org.sifunity.responsesconnector.exception.QueueNotFoundException if the queue does not exist
     * @throws org.sifunity.responsesconnector.exception.QueueServiceUnavailableException if the Queue Service is unavailable
     */
    /**
     * Routes an internal response from the Requests Connector to the consumer's queue.
     * <p>
     * This method handles responses from the Requests Connector (DELAYED_to_IMMEDIATE pattern)
     * which don't have environment context headers. The relativeServicePath is already formatted
     * by the Requests Connector before forwarding.
     * </p>
     * 
     * @param domainId The domain ID
     * @param solutionId The solution ID
     * @param queueId The UUID of the consumer's queue
     * @param requestId The original request ID for correlation
     * @param relativeServicePath The pre-formatted relativeServicePath from Requests Connector
     * @param response The response DTO containing service details and payload
     * @param passthroughHeaders Sanitized headers from the inbound HTTP request
     * @return ResponseResultDTO with messageId and status
     * @throws org.sifunity.responsesconnector.exception.QueueNotFoundException if the queue does not exist
     * @throws org.sifunity.responsesconnector.exception.QueueServiceUnavailableException if the Queue Service is unavailable
     */
    public ResponseResultDTO routeInternalResponse(
            String domainId, String solutionId,
            UUID queueId, String requestId,
            String relativeServicePath,
            ResponseDTO response,
            Map<String, String> passthroughHeaders) {
        
        log.info("Routing internal response to queue: queueId={}, requestId={}, serviceName={}, serviceType={}, relativeServicePath={}", 
            queueId, requestId, response.getServiceName(), response.getServiceType(), relativeServicePath);
        
        // Build SIF message envelope (no environment context for internal)
        SifMessageEnvelope<?> envelope = messageBuilder.buildInternalResponseEnvelope(
            requestId, response);
        
        // Merge passthrough headers (from HTTP request, e.g. x-sif-sequenceId) with envelope headers.
        // Exclude internal routing headers that are consumed by the controller itself —
        // these are plumbing, not meant for the consumer.
        Map<String, String> headers = new HashMap<>(envelope.getHeaders());
        if (passthroughHeaders != null) {
            for (Map.Entry<String, String> entry : passthroughHeaders.entrySet()) {
                String key = entry.getKey();
                if (!isInternalRoutingHeader(key)) {
                    headers.put(key, entry.getValue());
                }
            }
        }
        
        // If relativeServicePath is provided, set it (overrides any copy from passthrough)
        if (relativeServicePath != null) {
            headers.put("relativeServicePath", relativeServicePath);
        }
        
        envelope = SifMessageEnvelope.builder()
            .messageId(envelope.getMessageId())
            .timestamp(envelope.getTimestamp())
            .messageType(envelope.getMessageType())
            .serviceName(envelope.getServiceName())
            .serviceType(envelope.getServiceType())
            .requestId(envelope.getRequestId())
            .headers(headers)
            .payload(envelope.getPayload())
            .build();
        
        log.debug("Built internal response envelope: messageId={}, requestId={}, serviceName={}", 
            envelope.getMessageId(), envelope.getRequestId(), envelope.getServiceName());
        
        // Enqueue to consumer's queue
        UUID messageId = queueClient.enqueueMessage(domainId, solutionId, queueId, envelope);
        
        log.info("Internal response routed successfully: messageId={}, queueId={}, requestId={}", 
            messageId, queueId, requestId);
        
        return ResponseResultDTO.builder()
            .messageId(messageId)
            .status("DELIVERED")
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Routes an internal error from the Requests Connector to the consumer's queue.
     * 
     * @param domainId The domain ID
     * @param solutionId The solution ID
     * @param queueId The UUID of the consumer's queue
     * @param requestId The original request ID for correlation
     * @param errorMessage The error message describing what went wrong
     * @return ResponseResultDTO with messageId and status
     * @throws org.sifunity.responsesconnector.exception.QueueNotFoundException if the queue does not exist
     * @throws org.sifunity.responsesconnector.exception.QueueServiceUnavailableException if the Queue Service is unavailable
     */
    /**
     * Routes an internal error from the Requests Connector to the consumer's queue.
     * 
     * @param domainId The domain ID
     * @param solutionId The solution ID
     * @param queueId The UUID of the consumer's queue
     * @param requestId The original request ID for correlation
     * @param errorMessage The error message describing what went wrong
     * @param passthroughHeaders Sanitized headers from the inbound HTTP request
     * @return ResponseResultDTO with messageId and status
     * @throws org.sifunity.responsesconnector.exception.QueueNotFoundException if the queue does not exist
     * @throws org.sifunity.responsesconnector.exception.QueueServiceUnavailableException if the Queue Service is unavailable
     */
    public ResponseResultDTO routeInternalErrorResponse(
            String domainId, String solutionId,
            UUID queueId, String requestId,
            org.sifunity.responsesconnector.dto.InternalErrorDetail error,
            Map<String, String> passthroughHeaders) {

        log.warn("Routing internal error response to queue: queueId={}, requestId={}, subCode={}, scope={}, message={}",
            queueId, requestId, error.subCodeOrDefault(), error.scopeOrDefault(), error.message());

        // Build SIF error message envelope
        SifMessageEnvelope<?> envelope = messageBuilder.buildInternalErrorEnvelope(
            requestId, error);
        
        // Merge passthrough headers (from HTTP request, e.g. x-sif-sequenceId) with envelope headers.
        // Exclude internal routing headers that are consumed by the controller itself.
        if (passthroughHeaders != null && !passthroughHeaders.isEmpty()) {
            Map<String, String> headers = new HashMap<>(envelope.getHeaders());
            for (Map.Entry<String, String> entry : passthroughHeaders.entrySet()) {
                String key = entry.getKey();
                if (!isInternalRoutingHeader(key)) {
                    headers.put(key, entry.getValue());
                }
            }
            envelope = SifMessageEnvelope.builder()
                .messageId(envelope.getMessageId())
                .timestamp(envelope.getTimestamp())
                .messageType(envelope.getMessageType())
                .serviceName(envelope.getServiceName())
                .serviceType(envelope.getServiceType())
                .requestId(envelope.getRequestId())
                .headers(headers)
                .payload(envelope.getPayload())
                .build();
        }
        
        log.debug("Built internal error envelope: messageId={}, requestId={}", 
            envelope.getMessageId(), envelope.getRequestId());
        
        // Enqueue to consumer's queue
        UUID messageId = queueClient.enqueueMessage(domainId, solutionId, queueId, envelope);
        
        log.info("Internal error response routed successfully: messageId={}, queueId={}, requestId={}", 
            messageId, queueId, requestId);
        
        return ResponseResultDTO.builder()
            .messageId(messageId)
            .status("ERROR_DELIVERED")
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Rebuilds the envelope with enriched headers containing formatted relativeServicePath
     * and ensuring zoneId/contextId are always present.
     *
     * <p>Looks up the consumer environment's legacy preference via the queue owner,
     * formats relativeServicePath accordingly, and always includes zoneId and contextId
     * as separate headers regardless of legacy flag (Requirement 5.3).</p>
     *
     * @param envelope the original message envelope
     * @param domainId the domain ID for legacy tag lookup
     * @param solutionId the solution ID for legacy tag lookup
     * @param queueId the consumer's queue ID (used to resolve consumer environment)
     * @param serviceName the SIF service name
     * @param zoneId the resolved zone ID
     * @param contextId the resolved context ID
     * @param relativeServicePath the original relativeServicePath header
     * @return a new envelope with enriched headers
     */
    private SifMessageEnvelope<?> rebuildWithEnrichedHeaders(SifMessageEnvelope<?> envelope,
                                       String domainId, String solutionId, UUID queueId,
                                       String serviceName, String zoneId, String contextId,
                                       String relativeServicePath) {
        
        // Resolve consumer environment ID from queue owner
        UUID consumerEnvironmentId = queueClient.getQueueOwnerEnvironmentId(domainId, solutionId, queueId);
        
        // Determine legacy format preference for the consumer
        boolean legacyFormat = false;
        if (consumerEnvironmentId != null) {
            legacyFormat = legacyTagLookupService.isLegacyEnvironment(domainId, solutionId, consumerEnvironmentId);
            log.debug("Consumer legacy preference: queueId={}, consumerEnvId={}, legacy={}",
                    queueId, consumerEnvironmentId, legacyFormat);
        } else {
            log.warn("Could not resolve consumer environment for queue: queueId={}, defaulting to modern format", queueId);
        }
        
        // Extract query string from original relativeServicePath
        String queryString = extractQueryString(relativeServicePath);
        
        // Format relativeServicePath based on consumer's legacy preference
        String formattedPath = RelativeServicePathFormatter.format(
                serviceName, zoneId, contextId, queryString, legacyFormat);
        
        // Build enriched headers map
        Map<String, String> headers = new HashMap<>(envelope.getHeaders());
        
        // Set formatted relativeServicePath
        headers.put("relativeServicePath", formattedPath);
        
        // Always include zoneId and contextId as separate HTTP headers (Requirement 5.3)
        if (zoneId != null) {
            headers.put("zoneId", zoneId);
        }
        headers.put("contextId", contextId != null ? contextId : "DEFAULT");
        
        // Rebuild envelope with enriched headers
        return SifMessageEnvelope.builder()
            .messageId(envelope.getMessageId())
            .timestamp(envelope.getTimestamp())
            .messageType(envelope.getMessageType())
            .serviceName(envelope.getServiceName())
            .serviceType(envelope.getServiceType())
            .zoneId(envelope.getZoneId())
            .contextId(envelope.getContextId())
            .requestId(envelope.getRequestId())
            .queueId(envelope.getQueueId())
            .sourceId(envelope.getSourceId())
            .headers(headers)
            .payload(envelope.getPayload())
            .build();
    }
    
    /**
     * Extracts the query string from a relativeServicePath.
     *
     * @param relativeServicePath the path potentially containing a query string
     * @return the query string without the leading '?', or null if none
     */
    private String extractQueryString(String relativeServicePath) {
        if (relativeServicePath == null) {
            return null;
        }
        int queryIndex = relativeServicePath.indexOf('?');
        if (queryIndex >= 0 && queryIndex < relativeServicePath.length() - 1) {
            return relativeServicePath.substring(queryIndex + 1);
        }
        return null;
    }
    
    /**
     * Headers that are internal routing plumbing for the responses-connector's internal API.
     * These are consumed by InternalResponseController and should NOT be forwarded to consumers.
     */
    private static final Set<String> INTERNAL_ROUTING_HEADERS;
    
    static {
        TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.add("domainId");
        set.add("solutionId");
        set.add("relativeServicePath");
        set.add("X-Environment-Id");
        set.add("X-Environment-Name");
        INTERNAL_ROUTING_HEADERS = Collections.unmodifiableSet(set);
    }
    
    /**
     * Returns true if the given header name is an internal routing header that should
     * not be forwarded to consumers via the envelope passthrough headers.
     */
    private static boolean isInternalRoutingHeader(String headerName) {
        return INTERNAL_ROUTING_HEADERS.contains(headerName);
    }
}
