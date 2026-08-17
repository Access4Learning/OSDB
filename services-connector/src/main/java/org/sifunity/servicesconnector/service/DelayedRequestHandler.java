// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.service;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.constant.MessageExchangePattern;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.servicesconnector.client.EnvironmentServiceClient;
import org.sifunity.servicesconnector.client.QueueServiceClient;
import org.sifunity.servicesconnector.client.SubscriptionServiceClient;
import org.sifunity.servicesconnector.dto.ProxyRequestMetadata;
import org.sifunity.servicesconnector.exception.NoSubscriptionException;
import org.sifunity.servicesconnector.util.MessageBuilder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import org.sifunity.broker.common.dto.ProtocolDTO;

/**
 * Handler for DELAYED (asynchronous) functional service request patterns.
 *
 * <p>Supports two sub-patterns based on the provider's capabilities:</p>
 * <ul>
 *   <li><b>DELAYED_to_DELAYED</b>: Provider supports DELAYED pattern. The request
 *       is enqueued to the provider's subscription queue. The provider polls its
 *       queue, processes the request, and sends the response via the Responses
 *       Connector to the consumer's queue.</li>
 *   <li><b>DELAYED_to_IMMEDIATE</b>: Provider supports only IMMEDIATE pattern.
 *       Request metadata is stored in Redis for background proxy processing.
 *       A background worker forwards the request synchronously to the provider
 *       and delivers the response to the consumer's queue.</li>
 * </ul>
 *
 * <p>Both sub-patterns return HTTP 202 Accepted immediately with a messageId
 * for correlation.</p>
 *
 * <p>Follows the requests-connector's {@code DelayedRequestHandler} pattern,
 * adapted for functional services (serviceType=FUNCTIONAL, URL path-based
 * routing with jobId/phaseName segments).</p>
 *
 * <p><strong>Requirements:</strong> 5.2, 5.3, 5.4, 5.5</p>
 */
@Service
@Slf4j
public class DelayedRequestHandler {

    private final SubscriptionServiceClient subscriptionClient;
    private final QueueServiceClient queueClient;
    private final MessageBuilder messageBuilder;
    private final RedisTemplate<String, ProxyRequestMetadata> redisTemplate;
    private final EnvironmentServiceClient environmentServiceClient;

    public DelayedRequestHandler(
            SubscriptionServiceClient subscriptionClient,
            QueueServiceClient queueClient,
            MessageBuilder messageBuilder,
            RedisTemplate<String, ProxyRequestMetadata> redisTemplate,
            EnvironmentServiceClient environmentServiceClient) {
        this.subscriptionClient = subscriptionClient;
        this.queueClient = queueClient;
        this.messageBuilder = messageBuilder;
        this.redisTemplate = redisTemplate;
        this.environmentServiceClient = environmentServiceClient;
        log.info("DelayedRequestHandler initialized");
    }

    /**
     * Handles a DELAYED request to a DELAYED-capable provider.
     *
     * <p>DELAYED_to_DELAYED sub-pattern:</p>
     * <ol>
     *   <li>Resolves contextUuid from zone/context identifiers</li>
     *   <li>Queries Subscription Service for provider's request subscription</li>
     *   <li>Builds a SIF message envelope with consumer's queueId for response routing</li>
     *   <li>Enqueues the message to the provider's queue via Queue Service</li>
     *   <li>Returns a map with messageId, queueId, and ACCEPTED status</li>
     * </ol>
     *
     * @param httpMethod              the HTTP method (GET, POST, PUT, DELETE)
     * @param functionalServiceName   the functional service name from the URL path
     * @param jobId                   the job ID (may be null)
     * @param pathSuffix              additional path segments after jobId (may be null)
     * @param environmentId           the consumer's environment ID
     * @param environmentName         the consumer's environment name
     * @param domainId                the domain ID from the URL path
     * @param solutionId              the solution ID from the URL path
     * @param provider                the resolved provider registration
     * @param consumerQueueId         the consumer's queue ID for response delivery
     * @param zoneId                  the zone ID from SIF header
     * @param contextId               the context ID from SIF header (may be null)
     * @param requestBody             the raw request body (may be null for GET/DELETE)
     * @param passthroughHeaders      sanitized headers to forward
     * @return a map containing messageId, queueId, and status=ACCEPTED
     * @throws NoSubscriptionException if provider has no request subscription
     */
    public Map<String, String> handleDelayedToDelayed(
            String httpMethod,
            String functionalServiceName,
            String jobId,
            String pathSuffix,
            UUID environmentId,
            String environmentName,
            String domainId,
            String solutionId,
            ProviderRegistrationDTO provider,
            UUID consumerQueueId,
            String zoneId,
            String contextId,
            String requestId,
            Object requestBody,
            Map<String, String> passthroughHeaders) {

        log.info("Handling DELAYED_to_DELAYED: {} /{}, environmentId={}, consumerQueueId={}",
                httpMethod, functionalServiceName, environmentId, consumerQueueId);

        // Resolve contextUuid for subscription lookup
        UUID contextUuid = environmentServiceClient.resolveContextUuid(
                domainId, solutionId, provider.zoneId(), provider.contextId());
        log.debug("Resolved contextUuid={} for zone={}, context={}",
                contextUuid, provider.zoneId(), provider.contextId());

        // Query provider's request subscription
        SubscriptionDTO subscription = subscriptionClient.getDelayedRequestSubscription(
                domainId, solutionId, functionalServiceName, contextUuid);

        if (subscription == null) {
            throw new NoSubscriptionException(
                    String.format("Provider has no request subscription for service=%s, contextUuid=%s",
                            functionalServiceName, contextUuid));
        }

        log.debug("Found provider subscription: queueId={}", subscription.queueId());

        // Build SIF message envelope with consumer's queueId for response routing
        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
                functionalServiceName, environmentId, zoneId, contextId,
                consumerQueueId, requestId, requestBody, passthroughHeaders);

        log.debug("Built DELAYED request envelope: messageId={}, consumerQueueId={}",
                envelope.getMessageId(), consumerQueueId);

        // Enqueue message to provider's queue
        UUID messageId = queueClient.enqueueMessage(domainId, solutionId, subscription.queueId(), envelope);

        log.info("Enqueued DELAYED_to_DELAYED request: messageId={}, providerQueueId={}, consumerQueueId={}",
                messageId, subscription.queueId(), consumerQueueId);

        return Map.of(
                "messageId", messageId.toString(),
                "queueId", consumerQueueId.toString(),
                "status", "ACCEPTED"
        );
    }

    /**
     * Handles queue-only routing when no ProviderRegistration exists but a PROVIDE ACL does.
     *
     * <p>This is the fallback for the DELAYED flow per SIF spec: when a provider has a PROVIDE
     * ACL but hasn't registered with an endpoint, DELAYED requests are still accepted and
     * delivered to the provider's subscription queue (if one exists).</p>
     *
     * <p>Queue-only routing pattern:</p>
     * <ol>
     *   <li>Resolves contextUuid from zone/context identifiers</li>
     *   <li>Queries Subscription Service for provider's request subscription</li>
     *   <li>If subscription exists: enqueues message to provider's queue</li>
     *   <li>If no subscription: logs warning but still accepts (provider will poll later)</li>
     *   <li>Returns a map with messageId, queueId, and ACCEPTED status</li>
     * </ol>
     *
     * @param httpMethod              the HTTP method (GET, POST, PUT, DELETE)
     * @param functionalServiceName   the functional service name from the URL path
     * @param environmentId           the consumer's environment ID
     * @param domainId                the domain ID from the URL path
     * @param solutionId              the solution ID from the URL path
     * @param consumerQueueId         the consumer's queue ID for response delivery
     * @param zoneId                  the zone ID from SIF header
     * @param contextId               the context ID from SIF header (may be null)
     * @param requestId               the request ID for correlation
     * @param requestBody             the raw request body (may be null for GET/DELETE)
     * @param passthroughHeaders      sanitized headers to forward
     * @return a map containing messageId, queueId, and status=ACCEPTED
     */
    public Map<String, String> handleQueueOnlyRouting(
            String httpMethod,
            String functionalServiceName,
            UUID environmentId,
            String domainId,
            String solutionId,
            UUID consumerQueueId,
            String zoneId,
            String contextId,
            String requestId,
            UUID providerEnvironmentId,
            Object requestBody,
            Map<String, String> passthroughHeaders) {

        log.info("Handling queue-only routing (no ProviderRegistration): {} /{}, zoneId={}, contextId={}, consumerQueueId={}, providerEnvId={}",
                httpMethod, functionalServiceName, zoneId, contextId, consumerQueueId, providerEnvironmentId);

        // Build SIF message envelope
        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
                functionalServiceName, environmentId, zoneId, contextId,
                consumerQueueId, requestId, requestBody, passthroughHeaders);

        // Try to find provider's queue by their environment ID and deliver there
        if (providerEnvironmentId != null) {
            try {
                UUID providerQueueId = queueClient.findQueueByOwner(domainId, solutionId, providerEnvironmentId);

                if (providerQueueId != null) {
                    UUID messageId = queueClient.enqueueMessage(domainId, solutionId, providerQueueId, envelope);
                    log.info("Enqueued to provider's queue (queue-only routing): messageId={}, providerQueueId={}, consumerQueueId={}",
                            messageId, providerQueueId, consumerQueueId);

                    return Map.of(
                            "messageId", messageId.toString(),
                            "queueId", consumerQueueId.toString(),
                            "status", "ACCEPTED"
                    );
                } else {
                    log.warn("No queue found for provider environment {}, falling back to consumer queue", providerEnvironmentId);
                }
            } catch (Exception e) {
                log.warn("Failed to deliver to provider queue (queue-only routing): providerEnvId={}, error={}. " +
                        "Falling back to consumer queue.", providerEnvironmentId, e.getMessage());
            }
        }

        // Fallback: enqueue to consumer's queue for acknowledgment
        UUID messageId = queueClient.enqueueMessage(domainId, solutionId, consumerQueueId, envelope);
        log.info("Enqueued to consumer's queue (queue-only fallback): messageId={}, consumerQueueId={}",
                messageId, consumerQueueId);

        return Map.of(
                "messageId", messageId.toString(),
                "queueId", consumerQueueId.toString(),
                "status", "ACCEPTED"
        );
    }

    /**
     * Handles a DELAYED request to an IMMEDIATE-only provider.
     *
     * <p>DELAYED_to_IMMEDIATE sub-pattern:</p>
     * <ol>
     *   <li>Builds a SIF message envelope with consumer's queueId for response routing</li>
     *   <li>Creates {@link ProxyRequestMetadata} with all info needed for background processing</li>
     *   <li>Stores the metadata in Redis work queue for the background worker</li>
     *   <li>Returns a map with messageId, queueId, and ACCEPTED status</li>
     * </ol>
     *
     * <p>A background worker will later retrieve this metadata, make a synchronous
     * HTTP call to the provider, and deliver the response to the consumer's queue.</p>
     *
     * @param httpMethod              the HTTP method (GET, POST, PUT, DELETE)
     * @param functionalServiceName   the functional service name from the URL path
     * @param jobId                   the job ID (may be null)
     * @param pathSuffix              additional path segments after jobId (may be null)
     * @param environmentId           the consumer's environment ID
     * @param environmentName         the consumer's environment name
     * @param provider                the resolved provider registration
     * @param consumerQueueId         the consumer's queue ID for response delivery
     * @param zoneId                  the zone ID from SIF header
     * @param contextId               the context ID from SIF header (may be null)
     * @param requestBody             the raw request body (may be null for GET/DELETE)
     * @param passthroughHeaders      sanitized headers to forward
     * @return a map containing messageId, queueId, and status=ACCEPTED
     */
    public Map<String, String> handleDelayedToImmediate(
            String httpMethod,
            String functionalServiceName,
            String jobId,
            String pathSuffix,
            UUID environmentId,
            String environmentName,
            ProviderRegistrationDTO provider,
            UUID consumerQueueId,
            String zoneId,
            String contextId,
            String requestId,
            Object requestBody,
            Map<String, String> passthroughHeaders) {

        log.info("Handling DELAYED_to_IMMEDIATE: {} /{}, environmentId={}, providerEndpoint={}, consumerQueueId={}",
                httpMethod, functionalServiceName, environmentId, ProtocolDTO.locationOf(provider.endPoint()), consumerQueueId);

        // Build SIF message envelope with consumer's queueId for response routing
        SifMessageEnvelope<Object> envelope = messageBuilder.buildDelayedRequestEnvelope(
                functionalServiceName, environmentId, zoneId, contextId,
                consumerQueueId, requestId, requestBody, passthroughHeaders);

        log.debug("Built DELAYED_to_IMMEDIATE envelope: messageId={}, consumerQueueId={}",
                envelope.getMessageId(), consumerQueueId);

        // Build the provider URL path suffix for the background worker
        String providerPathSuffix = FunctionalServiceRoutingService.buildProviderUrl(
                ProtocolDTO.locationOf(provider.endPoint()), functionalServiceName, jobId, pathSuffix);

        // Create proxy request metadata for background worker
        ProxyRequestMetadata metadata = ProxyRequestMetadata.builder()
                .messageId(envelope.getMessageId())
                .consumerQueueId(consumerQueueId)
                .environmentId(environmentId)
                .environmentName(environmentName)
                .providerEndpoint(providerPathSuffix)
                .httpMethod(httpMethod)
                .pathSuffix(pathSuffix)
                .requestEnvelope(envelope)
                .passthroughHeaders(passthroughHeaders)
                .createdAt(envelope.getTimestamp())
                .retryCount(0)
                .build();

        // Store metadata in Redis work queue
        redisTemplate.opsForList().rightPush("functional-proxy-requests", metadata);

        log.info("Stored DELAYED_to_IMMEDIATE request for background processing: messageId={}, providerEndpoint={}, consumerQueueId={}",
                envelope.getMessageId(), ProtocolDTO.locationOf(provider.endPoint()), consumerQueueId);

        return Map.of(
                "messageId", envelope.getMessageId().toString(),
                "queueId", consumerQueueId.toString(),
                "status", "ACCEPTED"
        );
    }
}
