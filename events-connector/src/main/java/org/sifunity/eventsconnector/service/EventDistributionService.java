// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.TracingUtilsBase;
import org.sifunity.eventsconnector.client.EnvironmentServiceClient;
import org.sifunity.eventsconnector.dto.DeliveryStatus;
import org.sifunity.eventsconnector.dto.EventDistributionResponse;
import org.sifunity.eventsconnector.dto.EventPublicationRequest;
import org.sifunity.eventsconnector.dto.QueueDeliveryResult;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core service for distributing events to all matching subscriber queues.
 * 
 * <p>This service orchestrates the complete event distribution flow:</p>
 * <ol>
 *   <li>Generate correlation ID for request tracing</li>
 *   <li>Query subscriptions matching the event criteria</li>
 *   <li>Handle no subscriptions case (return NO_SUBSCRIBERS status)</li>
 *   <li>Create SIF message envelope with event data</li>
 *   <li>Distribute event to all subscriber queues concurrently</li>
 *   <li>Aggregate results and determine overall delivery status</li>
 * </ol>
 * 
 * <p><strong>Per Requirement 7: Events are intentionally excluded from matrix parameter
 * injection. relativeServicePath never contains matrix params for events, regardless of
 * the SIF_LEGACY_MATRIX_PARAMS tag.</strong> Unlike the requests-connector and
 * responses-connector, this service does NOT use {@code LegacyTagLookupService} or
 * {@code RelativeServicePathFormatter}. The {@code zoneId} and {@code contextId} are
 * conveyed solely as fields in the {@link SifMessageEnvelope}, which are delivered as
 * HTTP headers when subscribers poll their queues.</p>
 * 
 * <p>The service provides comprehensive logging at each step to enable monitoring
 * and troubleshooting of event distribution. All log entries include the correlation
 * ID for request tracing across services.</p>
 * 
 * <p><strong>Delivery Status Determination:</strong></p>
 * <ul>
 *   <li>NO_SUBSCRIBERS: No subscriptions found for the event</li>
 *   <li>SUCCESS: All subscriber queues received the event successfully</li>
 *   <li>PARTIAL: Some queues succeeded, some failed</li>
 *   <li>FAILED: All queues failed to receive the event</li>
 * </ul>
 * 
 * <p>Requirements:</p>
 * <ul>
 *   <li>1: Event Reception from Providers - Accept and process events</li>
 *   <li>2: Subscription Querying - Query matching subscriptions</li>
 *   <li>3: Event Distribution to Subscriber Queues - Deliver to all queues</li>
 *   <li>4: Fan-out Event Delivery - Concurrent distribution to multiple queues</li>
 *   <li>5: Event Message Construction - Create SIF message envelopes</li>
 *   <li>11: Partial Delivery Handling - Handle mixed success/failure scenarios</li>
 *   <li>14: Observability and Logging - Comprehensive logging with correlation IDs</li>
 * </ul>
 * 
 * @see SubscriptionQueryService
 * @see QueueEnqueueService
 * @see EventPublicationRequest
 * @see EventDistributionResponse
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventDistributionService {
    
    private final SubscriptionQueryService subscriptionQueryService;
    private final QueueEnqueueService queueEnqueueService;
    private final EnvironmentServiceClient environmentServiceClient;
    private final org.sifunity.eventsconnector.metrics.EventMetrics eventMetrics;
    private final TracingUtilsBase tracingUtils;
    
    /**
     * Distributes an event to all matching subscriber queues.
     * 
     * <p>This method orchestrates the complete event distribution flow. It queries
     * subscriptions, creates a SIF message envelope, distributes to all queues
     * concurrently, and aggregates the results.</p>
     * 
     * <p><strong>Flow:</strong></p>
     * <ol>
     *   <li>Generate and add correlation ID to MDC for logging</li>
     *   <li>Log event reception with key details</li>
     *   <li>Query subscriptions matching event criteria</li>
     *   <li>If no subscriptions: log warning and return NO_SUBSCRIBERS response</li>
     *   <li>Log number of subscriptions found</li>
     *   <li>Create SIF message envelope with event data</li>
     *   <li>Extract queue IDs from subscriptions</li>
     *   <li>Distribute event to all queues concurrently via QueueEnqueueService</li>
     *   <li>Aggregate results and determine overall delivery status</li>
     *   <li>Log distribution completion with status and counts</li>
     *   <li>Return EventDistributionResponse with all details</li>
     * </ol>
     * 
     * <p><strong>Logging:</strong></p>
     * <ul>
     *   <li>INFO: Event reception, subscription count, distribution completion</li>
     *   <li>WARN: No subscriptions found</li>
     *   <li>DEBUG: Detailed flow information</li>
     * </ul>
     * 
     * <p><strong>Error Handling:</strong></p>
     * <ul>
     *   <li>SubscriptionServiceException: Propagated if subscription query fails</li>
     *   <li>Queue failures: Handled gracefully, included in partial delivery results</li>
     * </ul>
     * 
     * @param request Event publication request containing event details and payload
     * @return EventDistributionResponse with delivery status and results for each queue
     * @throws org.sifunity.eventsconnector.exception.SubscriptionServiceException 
     *         if subscription query fails
     */
    public EventDistributionResponse distributeEvent(String domainId, String solutionId, EventPublicationRequest request) {
        // Generate correlation ID and add to MDC for request tracing
        String correlationId = generateCorrelationId();
        MDC.put("correlationId", correlationId);
        
        // Create custom span for SIF event processing with metadata
        return tracingUtils.traceOperation(
            "sif.event.distribute",
            Map.of(
                "sif.message_type", "EVENT",
                "sif.service_name", request.serviceName(),
                "sif.zone_id", request.zoneId(),
                "sif.context_id", request.contextId(),
                "sif.message_id", request.messageId()
            ),
            () -> {
                try {
                    // Record event received metric
                    eventMetrics.recordEventReceived(request.serviceName(), request.zoneId());
                    
                    // Log event reception with key details
                    log.info("Distributing event: service={}, zone={}, context={}, messageId={}, correlationId={}",
                        request.serviceName(), request.zoneId(), request.contextId(), 
                        request.messageId(), correlationId);
                    
                    // Query subscriptions matching event criteria
                    log.debug("Querying subscriptions for event: service={}, zone={}, context={}",
                        request.serviceName(), request.zoneId(), request.contextId());
                    
                    // Resolve zoneId/contextId from SIF headers to contextUuid
                    UUID contextUuid = environmentServiceClient.resolveContextUuid(
                        domainId, solutionId, request.zoneId(), request.contextId());
                    log.debug("Resolved contextUuid={} for zone={}, context={}",
                        contextUuid, request.zoneId(), request.contextId());
                    
                    long subscriptionQueryStart = System.nanoTime();
                    List<SubscriptionDTO> subscriptions = subscriptionQueryService.querySubscriptions(
                        domainId,
                        solutionId,
                        request.serviceName(),
                        contextUuid
                    );
                    long subscriptionQueryEnd = System.nanoTime();
                    
                    // Record subscription query latency
                    java.time.Duration queryDuration = java.time.Duration.ofNanos(subscriptionQueryEnd - subscriptionQueryStart);
                    eventMetrics.recordSubscriptionQueryLatency(queryDuration);
                    
                    // Handle no subscriptions case
                    if (subscriptions.isEmpty()) {
                        log.warn("No subscriptions found for event: service={}, zone={}, context={}, messageId={}",
                            request.serviceName(), request.zoneId(), request.contextId(), request.messageId());
                        
                        // Record no subscribers metric
                        eventMetrics.recordNoSubscribers();
                        eventMetrics.recordEventDistributed(DeliveryStatus.NO_SUBSCRIBERS, 0);
                        
                        return EventDistributionResponse.noSubscribers(
                            request.messageId(),
                            request.timestamp()
                        );
                    }
                    
                    // Log number of subscriptions found
                    log.info("Found {} subscription(s) for event: service={}, zone={}, context={}, messageId={}",
                        subscriptions.size(), request.serviceName(), request.zoneId(), 
                        request.contextId(), request.messageId());
                    
                    // Create SIF message envelope with event data
                    SifMessageEnvelope<JsonNode> envelope = createMessageEnvelope(request);
                    
                    log.debug("Created SIF message envelope: messageId={}, messageType={}, serviceName={}",
                        envelope.getMessageId(), envelope.getMessageType(), envelope.getServiceName());
                    
                    // Extract queue IDs from subscriptions
                    List<UUID> queueIds = subscriptions.stream()
                        .map(SubscriptionDTO::queueId)
                        .collect(Collectors.toList());
                    
                    log.debug("Distributing event to {} queue(s): messageId={}", 
                        queueIds.size(), request.messageId());
                    
                    // Distribute to all queues via QueueEnqueueService (concurrent processing)
                    List<QueueDeliveryResult> deliveryResults = queueEnqueueService.enqueueToMultipleQueues(
                        domainId,
                        solutionId,
                        queueIds,
                        envelope
                    );
                    
                    // Aggregate results and determine delivery status
                    EventDistributionResponse response = EventDistributionResponse.fromResults(
                        request.messageId(),
                        request.timestamp(),
                        subscriptions.size(),
                        deliveryResults
                    );
                    
                    // Record event distributed metrics
                    eventMetrics.recordEventDistributed(response.deliveryStatus(), subscriptions.size());
                    
                    // Record specific metrics based on delivery status
                    if (response.deliveryStatus() == DeliveryStatus.PARTIAL) {
                        eventMetrics.recordPartialDelivery();
                    } else if (response.deliveryStatus() == DeliveryStatus.FAILED) {
                        eventMetrics.recordFailedDelivery();
                    }
                    
                    // Log distribution completion with status and counts
                    long successCount = deliveryResults.stream()
                        .filter(r -> r.status() == org.sifunity.eventsconnector.dto.DeliveryResultStatus.SUCCESS)
                        .count();
                    long failureCount = deliveryResults.size() - successCount;
                    
                    log.info("Event distribution complete: messageId={}, status={}, success={}/{}, failed={}, correlationId={}",
                        request.messageId(), response.deliveryStatus(), successCount, 
                        deliveryResults.size(), failureCount, correlationId);
                    
                    // Log details for partial or complete failures
                    if (response.deliveryStatus() == DeliveryStatus.PARTIAL || 
                        response.deliveryStatus() == DeliveryStatus.FAILED) {
                        
                        deliveryResults.stream()
                            .filter(r -> r.status() == org.sifunity.eventsconnector.dto.DeliveryResultStatus.FAILED)
                            .forEach(r -> log.warn("Queue delivery failed: queueId={}, error={}, messageId={}",
                                r.queueId(), r.error(), request.messageId()));
                    }
                    
                    return response;
                    
                } finally {
                    // Clean up MDC to prevent memory leaks
                    MDC.remove("correlationId");
                }
            }
        );
    }
    
    /**
     * Creates a SIF message envelope from an event publication request.
     * 
     * <p>The envelope includes all required SIF headers and the event payload.
     * The messageId and timestamp from the request are used in the envelope.</p>
     * 
     * <p><strong>Envelope Fields:</strong></p>
     * <ul>
     *   <li>messageId: From request (auto-generated if not provided)</li>
     *   <li>timestamp: From request (set to current time if not provided)</li>
     *   <li>messageType: EVENT (constant for all events)</li>
     *   <li>serviceName: From request</li>
     *   <li>serviceType: OBJECT (default for event services)</li>
     *   <li>zoneId: From request (conveyed as HTTP header on delivery — Req 7.2)</li>
     *   <li>contextId: From request (conveyed as HTTP header on delivery — Req 7.2)</li>
     *   <li>payload: Event payload from request</li>
     * </ul>
     * 
     * <p><strong>Note:</strong> No relativeServicePath or matrix parameter formatting is
     * applied here. Per Requirement 7, events are excluded from matrix parameter injection
     * regardless of the recipient's SIF_LEGACY_MATRIX_PARAMS tag setting.</p>
     * 
     * @param request Event publication request
     * @return SIF message envelope ready for distribution
     */
    private SifMessageEnvelope<JsonNode> createMessageEnvelope(EventPublicationRequest request) {
        return SifMessageEnvelope.<JsonNode>builder()
            .messageId(UUID.fromString(request.messageId()))
            .timestamp(request.timestamp())
            .messageType(MessageType.EVENT)
            .serviceName(request.serviceName())
            .serviceType(ServiceType.OBJECT)
            .zoneId(request.zoneId())
            .contextId(request.contextId())
            .sourceId(request.environmentId())
            .headers(request.headers())
            .payload(request.payload())
            .build();
    }
    
    /**
     * Generates a unique correlation ID for request tracing.
     * 
     * <p>The correlation ID is a UUID that can be used to trace a request
     * across multiple services and log entries. It's added to the MDC so
     * that all log entries for this request include the correlation ID.</p>
     * 
     * @return Unique correlation ID as a string
     */
    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
