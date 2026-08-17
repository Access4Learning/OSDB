// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.RelativeServicePathFormatter;
import org.sifunity.requestsconnector.client.EnvironmentServiceClient;
import org.sifunity.requestsconnector.client.QueueServiceClient;
import org.sifunity.requestsconnector.client.SubscriptionServiceClient;
import org.sifunity.requestsconnector.config.ProxyStreamConfig;
import org.sifunity.requestsconnector.dto.DelayedRequestDTO;
import org.sifunity.requestsconnector.dto.ProxyRequestMetadata;
import org.sifunity.requestsconnector.dto.RequestResponseDTO;
import org.sifunity.requestsconnector.exception.NoSubscriptionException;
import org.sifunity.requestsconnector.util.MessageBuilder;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.sifunity.broker.common.dto.ProtocolDTO;

/**
 * Handler for DELAYED (asynchronous) request pattern.
 * 
 * <p>This service handles the DELAYED request pattern where consumers send requests
 * and receive an immediate HTTP 202 Accepted response. The actual response is delivered
 * to the consumer's queue later. This handler supports two sub-patterns:</p>
 * 
 * <ul>
 *   <li><b>DELAYED_to_DELAYED</b>: Provider supports DELAYED pattern. Request is enqueued
 *       to the provider's request subscription queue.</li>
 *   <li><b>DELAYED_to_IMMEDIATE</b>: Provider supports only IMMEDIATE pattern. Request
 *       metadata is stored in Redis for background proxy processing.</li>
 * </ul>
 * 
 * <p>Requirements:</p>
 * <ul>
 *   <li>4.1: Query Subscription Service for provider's request subscription</li>
 *   <li>4.2: Extract provider's queueId from subscription</li>
 *   <li>4.3: Return HTTP 404 if provider has no request subscription</li>
 *   <li>4.4: Enqueue message to provider's queue via Queue Service</li>
 *   <li>4.5: Return HTTP 202 Accepted with messageId</li>
 *   <li>5.1: Store request metadata for background processing</li>
 *   <li>5.2: Include consumer's queueId, provider endpoint, and request payload</li>
 *   <li>5.3: Return HTTP 202 Accepted with messageId</li>
 * </ul>
 */
@Service
@Slf4j
public class DelayedRequestHandler {
    
    private final SubscriptionServiceClient subscriptionClient;
    private final QueueServiceClient queueClient;
    private final MessageBuilder messageBuilder;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final EnvironmentServiceClient environmentServiceClient;
    private final LegacyTagLookupService legacyTagLookupService;
    
    /**
     * Constructs a DelayedRequestHandler with required dependencies.
     * 
     * @param subscriptionClient client for querying provider subscriptions
     * @param queueClient client for enqueuing messages to queues
     * @param messageBuilder utility for building SIF message envelopes
     * @param stringRedisTemplate StringRedisTemplate for XADD to proxy requests stream
     * @param objectMapper Jackson ObjectMapper for serializing complex fields
     * @param environmentServiceClient client for resolving context UUIDs
     * @param legacyTagLookupService service for checking legacy matrix param preference
     */
    public DelayedRequestHandler(
            SubscriptionServiceClient subscriptionClient,
            QueueServiceClient queueClient,
            MessageBuilder messageBuilder,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            EnvironmentServiceClient environmentServiceClient,
            LegacyTagLookupService legacyTagLookupService) {
        this.subscriptionClient = subscriptionClient;
        this.queueClient = queueClient;
        this.messageBuilder = messageBuilder;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.environmentServiceClient = environmentServiceClient;
        this.legacyTagLookupService = legacyTagLookupService;
    }
    
    /**
     * Handles a DELAYED request to a DELAYED-capable provider.
     * 
     * <p>This method implements the DELAYED_to_DELAYED sub-pattern:</p>
     * <ol>
     *   <li>Queries Subscription Service for provider's request subscription</li>
     *   <li>Extracts the provider's queueId from the subscription</li>
     *   <li>Builds a SIF message envelope with consumer's queueId for response routing</li>
     *   <li>Enqueues the message to the provider's queue via Queue Service</li>
     *   <li>Returns HTTP 202 Accepted with the messageId</li>
     * </ol>
     * 
     * <p>The provider will poll their queue, process the request asynchronously, and
     * send the response to the Responses Connector, which will deliver it to the
     * consumer's queue.</p>
     * 
     * <p>Error handling:</p>
     * <ul>
     *   <li>No subscription found: Throws NoSubscriptionException (HTTP 404)</li>
     *   <li>Queue Service unavailable: Throws WebClientResponseException (HTTP 503)</li>
     * </ul>
     * 
     * @param environmentId the environment ID from the request context
     * @param environmentName the environment name from the request context
     * @param provider the provider registration containing service details
     * @param consumerQueueId the consumer's queue ID for receiving the response
     * @param request the DELAYED request DTO from the consumer
     * @return a response DTO with HTTP 202 status, messageId, and queueId
     * @throws NoSubscriptionException if provider has no request subscription
     * @throws org.springframework.web.reactive.function.client.WebClientResponseException
     *         if Queue Service is unavailable
     */
    public RequestResponseDTO handleDelayedToDelayed(
            UUID environmentId,
            String environmentName,
            String domainId,
            String solutionId,
            ProviderRegistrationDTO provider,
            UUID consumerQueueId,
            DelayedRequestDTO request,
            Map<String, String> passthroughHeaders,
            String relativeServicePath,
            String requestId) {
        
        log.info("Handling DELAYED_to_DELAYED request: serviceName={}, environmentId={}, consumerQueueId={}", 
            request.getServiceName(), environmentId, consumerQueueId);
        
        // Resolve zoneId/contextId from provider to contextUuid
        UUID contextUuid = environmentServiceClient.resolveContextUuid(
            domainId, solutionId, provider.zoneId(), provider.contextId());
        log.debug("Resolved contextUuid={} for zone={}, context={}",
            contextUuid, provider.zoneId(), provider.contextId());
        
        // Query provider's request subscription
        SubscriptionDTO subscription = subscriptionClient.getDelayedRequestSubscription(
            request.getServiceName(), 
            contextUuid);
        
        if (subscription == null) {
            throw new NoSubscriptionException(
                String.format("Provider has no request subscription for service=%s, contextUuid=%s",
                    request.getServiceName(), contextUuid));
        }
        
        log.debug("Found provider subscription: queueId={}", subscription.queueId());
        
        // Format relativeServicePath based on recipient's legacy preference
        boolean legacyFormat = legacyTagLookupService.isLegacyEnvironment(
                domainId, solutionId, provider.environmentId());
        String queryString = extractQueryString(relativeServicePath);
        String finalRelativeServicePath = RelativeServicePathFormatter.format(
                request.getServiceName(), provider.zoneId(), provider.contextId(), queryString, legacyFormat);
        
        // Build SIF message envelope with consumer's queueId for response routing
        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
            environmentId, 
            environmentName, 
            consumerQueueId, 
            request,
            passthroughHeaders,
            provider.zoneId(),
            provider.contextId(),
            requestId);
        
        // Add relativeServicePath to envelope headers so it flows through the queue
        if (envelope.getHeaders() != null) {
            envelope.getHeaders().put("relativeServicePath", finalRelativeServicePath);
            // Always include zoneId and contextId as separate HTTP headers on delivery (Req 5.3)
            envelope.getHeaders().put("zoneId", provider.zoneId());
            envelope.getHeaders().put("contextId", provider.contextId() != null ? provider.contextId() : "DEFAULT");
        }
        
        log.debug("Built DELAYED request envelope: messageId={}, consumerQueueId={}", 
            envelope.getMessageId(), consumerQueueId);
        
        // Enqueue message to provider's queue
        UUID messageId = queueClient.enqueueMessage(subscription.queueId(), envelope, domainId, solutionId);
        
        log.info("Successfully enqueued DELAYED_to_DELAYED request: messageId={}, providerQueueId={}, consumerQueueId={}", 
            messageId, subscription.queueId(), consumerQueueId);
        
        // Return HTTP 202 Accepted response
        return RequestResponseDTO.builder()
            .messageId(messageId)
            .queueId(consumerQueueId)
            .requestId(envelope.getRequestId())
            .status("ACCEPTED")
            .timestamp(envelope.getTimestamp())
            .build();
    }
    
    /**
     * Handles a DELAYED request to an IMMEDIATE-only provider.
     * 
     * <p>This method implements the DELAYED_to_IMMEDIATE sub-pattern:</p>
     * <ol>
     *   <li>Builds a SIF message envelope with consumer's queueId for response routing</li>
     *   <li>Creates ProxyRequestMetadata containing all information needed for background processing</li>
     *   <li>Stores the metadata in Redis work queue for the background worker</li>
     *   <li>Returns HTTP 202 Accepted with the messageId</li>
     * </ol>
     * 
     * <p>A background worker will later retrieve this metadata, make a synchronous HTTP
     * call to the provider, and route the response to the Responses Connector for
     * delivery to the consumer's queue.</p>
     * 
     * <p>Error handling:</p>
     * <ul>
     *   <li>Redis unavailable: Throws exception (should result in HTTP 503)</li>
     * </ul>
     * 
     * @param environmentId the environment ID from the request context
     * @param environmentName the environment name from the request context
     * @param provider the provider registration containing endpoint and service details
     * @param consumerQueueId the consumer's queue ID for receiving the response
     * @param request the DELAYED request DTO from the consumer
     * @return a response DTO with HTTP 202 status, messageId, and queueId
     * @throws org.springframework.data.redis.RedisConnectionFailureException
     *         if Redis is unavailable
     */
    public RequestResponseDTO handleDelayedToImmediate(
            UUID environmentId,
            String environmentName,
            String domainId,
            String solutionId,
            ProviderRegistrationDTO provider,
            UUID consumerQueueId,
            DelayedRequestDTO request,
            Map<String, String> passthroughHeaders,
            String httpMethod,
            String relativeServicePath,
            String requestId) {
        
        log.info("Handling DELAYED_to_IMMEDIATE request: serviceName={}, environmentId={}, providerEndpoint={}, consumerQueueId={}", 
            request.getServiceName(), environmentId, ProtocolDTO.locationOf(provider.endPoint()), consumerQueueId);
        
        // Build SIF message envelope with consumer's queueId for response routing
        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
            environmentId, 
            environmentName, 
            consumerQueueId, 
            request,
            passthroughHeaders,
            provider.zoneId(),
            provider.contextId(),
            requestId);
        
        log.debug("Built DELAYED_to_IMMEDIATE request envelope: messageId={}, consumerQueueId={}", 
            envelope.getMessageId(), consumerQueueId);
        
        // Format relativeServicePath based on recipient's legacy preference
        boolean legacyFormat = legacyTagLookupService.isLegacyEnvironment(
                domainId, solutionId, provider.environmentId());
        String queryString = extractQueryString(relativeServicePath);
        String finalRelativeServicePath = RelativeServicePathFormatter.format(
                request.getServiceName(), provider.zoneId(), provider.contextId(), queryString, legacyFormat);
        
        // Always include zoneId and contextId as separate HTTP headers on delivery (Req 5.3)
        if (envelope.getHeaders() != null) {
            envelope.getHeaders().put("zoneId", provider.zoneId());
            envelope.getHeaders().put("contextId", provider.contextId() != null ? provider.contextId() : "DEFAULT");
        }
        
        // Create proxy request metadata for background worker
        ProxyRequestMetadata metadata = ProxyRequestMetadata.builder()
            .messageId(envelope.getMessageId())
            .consumerQueueId(consumerQueueId)
            .environmentId(environmentId)
            .environmentName(environmentName)
            .domainId(domainId)
            .solutionId(solutionId)
            .httpMethod(httpMethod != null ? httpMethod : "POST")
            .providerEnvironmentId(provider.environmentId())
            .relativeServicePath(finalRelativeServicePath)
            .providerEndpoint(ProtocolDTO.locationOf(provider.endPoint()))
            .requestEnvelope(envelope)
            .createdAt(envelope.getTimestamp())
            .retryCount(0)
            .build();
        
        // Enqueue to proxy requests stream via XADD (replaces old list-based rightPush)
        Map<String, String> fieldMap = buildStreamFieldMap(metadata);
        StringRecord record = StreamRecords.string(fieldMap).withStreamKey(ProxyStreamConfig.STREAM_KEY);
        RecordId recordId = stringRedisTemplate.opsForStream().add(record);
        
        log.info("Successfully enqueued DELAYED_to_IMMEDIATE request to stream: messageId={}, streamId={}, providerEndpoint={}, consumerQueueId={}", 
            envelope.getMessageId(), recordId, ProtocolDTO.locationOf(provider.endPoint()), consumerQueueId);
        
        // Return HTTP 202 Accepted response
        return RequestResponseDTO.builder()
            .messageId(envelope.getMessageId())
            .queueId(consumerQueueId)
            .requestId(envelope.getRequestId())
            .status("ACCEPTED")
            .timestamp(envelope.getTimestamp())
            .build();
    }

    /**
     * Extracts the query string from a relativeServicePath, if present.
     *
     * @param relativeServicePath the path that may contain a query string (e.g., "/StudentPersonals?where=x")
     * @return the query string without the leading '?', or null if no query string
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
     * Builds a field map for XADD to the proxy requests stream.
     *
     * <p>Serializes all {@link ProxyRequestMetadata} fields into a flat String map suitable
     * for Redis Streams. Complex objects (requestEnvelope) are serialized as JSON strings.
     * No explicit retryCount field is needed — Redis Streams tracks delivery count natively
     * via XPENDING.</p>
     *
     * @param metadata the proxy request metadata to serialize
     * @return field map for the stream entry
     */
    private Map<String, String> buildStreamFieldMap(ProxyRequestMetadata metadata) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("messageId", metadata.getMessageId() != null ? metadata.getMessageId().toString() : "");
        fields.put("consumerQueueId", metadata.getConsumerQueueId() != null ? metadata.getConsumerQueueId().toString() : "");
        fields.put("environmentId", metadata.getEnvironmentId() != null ? metadata.getEnvironmentId().toString() : "");
        fields.put("environmentName", metadata.getEnvironmentName() != null ? metadata.getEnvironmentName() : "");
        fields.put("domainId", metadata.getDomainId() != null ? metadata.getDomainId() : "");
        fields.put("solutionId", metadata.getSolutionId() != null ? metadata.getSolutionId() : "");
        fields.put("httpMethod", metadata.getHttpMethod() != null ? metadata.getHttpMethod() : "");
        fields.put("providerEnvironmentId", metadata.getProviderEnvironmentId() != null ? metadata.getProviderEnvironmentId().toString() : "");
        fields.put("relativeServicePath", metadata.getRelativeServicePath() != null ? metadata.getRelativeServicePath() : "");
        fields.put("providerEndpoint", metadata.getProviderEndpoint() != null ? metadata.getProviderEndpoint() : "");
        fields.put("createdAt", metadata.getCreatedAt() != null ? metadata.getCreatedAt().toString() : Instant.now().toString());

        // Serialize requestEnvelope as JSON — contains the full SIF message to forward to provider
        try {
            fields.put("requestEnvelope", objectMapper.writeValueAsString(metadata.getRequestEnvelope()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize requestEnvelope for stream entry: messageId=" 
                    + metadata.getMessageId(), e);
        }

        return fields;
    }
}
