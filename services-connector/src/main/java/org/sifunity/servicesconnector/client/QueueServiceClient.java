// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.client;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * HTTP client for communicating with the Queue Service.
 *
 * <p>Provides methods for enqueuing messages to queues. Used in the DELAYED
 * request pattern to enqueue request messages to provider queues
 * (DELAYED_to_DELAYED) and to deliver provider responses to consumer queues
 * (DELAYED_to_IMMEDIATE).</p>
 *
 * <p>Mirrors the requests-connector's {@code QueueServiceClient}.</p>
 *
 * <p><strong>Requirements:</strong> 5.4</p>
 */
@Component
@Slf4j
public class QueueServiceClient {

    private final WebClient webClient;
    private final String internalServiceToken;

    public QueueServiceClient(
            @Qualifier("environmentServiceWebClientBuilder") WebClient.Builder webClientBuilder,
            @Value("${queue.service.url}") String baseUrl,
            @Value("${broker.internal-service-token:}") String internalServiceToken) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.internalServiceToken = internalServiceToken;
    }

    /**
     * Enqueues a message to the specified queue.
     *
     * @param domainId   the domain ID for the request path
     * @param solutionId the solution ID for the request path
     * @param queueId    the UUID of the queue to enqueue to
     * @param envelope   the SIF message envelope containing the request data
     * @return the messageId assigned by the Queue Service
     */
    @SuppressWarnings("unchecked")
    public UUID enqueueMessage(String domainId, String solutionId,
                               UUID queueId, SifMessageEnvelope<?> envelope) {
        log.debug("Enqueuing message to queue: queueId={}, messageId={}",
                queueId, envelope.getMessageId());

        try {
            HashMap<String, Object> request = new HashMap<>();
            request.put("messageId", envelope.getMessageId());
            request.put("environment", envelope.getZoneId() != null ? envelope.getZoneId() : "default");
            request.put("zone", envelope.getZoneId() != null ? envelope.getZoneId() : "default");
            request.put("context", envelope.getContextId() != null ? envelope.getContextId() : "default");
            request.put("serviceName", envelope.getServiceName());
            if (envelope.getMessageType() != null) {
                request.put("messageType", envelope.getMessageType().name());
            }
            if (envelope.getServiceType() != null) {
                request.put("serviceType", envelope.getServiceType().name());
            }
            request.put("payload", envelope.getPayload() != null ? envelope.getPayload() : envelope);

            // Pass through all non-blacklisted headers for SIF spec compliance
            if (envelope.getHeaders() != null && !envelope.getHeaders().isEmpty()) {
                request.put("headers", envelope.getHeaders());
            }
            // Also include SIF structured headers for queue delivery
            if (envelope.getRequestId() != null) {
                request.put("requestId", envelope.getRequestId().toString());
            }
            if (envelope.getQueueId() != null) {
                request.put("queueId", envelope.getQueueId().toString());
            }

            Map<String, Object> response = webClient.post()
                    .uri("/{domainId}/{solutionId}/queues/{queueId}/messages",
                            domainId, solutionId, queueId)
                    .header("X-Environment-Fingerprint", "broker-services")
                    .header("X-Internal-Service-Token", internalServiceToken)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            UUID messageId = UUID.fromString((String) response.get("messageId"));

            log.info("Successfully enqueued message: queueId={}, messageId={}",
                    queueId, messageId);

            return messageId;

        } catch (Exception e) {
            log.error("Failed to enqueue message: queueId={}, messageId={}",
                    queueId, envelope.getMessageId(), e);
            throw e;
        }
    }

    /**
     * Finds the first queue owned by the specified environment.
     *
     * <p>Used by the queue-only routing flow to find the provider's queue
     * for message delivery when no ProviderRegistration exists.</p>
     *
     * @param domainId      the domain ID for the request path
     * @param solutionId    the solution ID for the request path
     * @param ownerId       the environment ID that owns the queue
     * @return the queue ID if found, or null if no queue exists for this owner
     */
    @SuppressWarnings("unchecked")
    public UUID findQueueByOwner(String domainId, String solutionId, UUID ownerId) {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/{domainId}/{solutionId}/queues", domainId, solutionId)
                    .header("X-Environment-Id", ownerId.toString())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("queues")) {
                Map<String, Object> queuesWrapper = (Map<String, Object>) response.get("queues");
                if (queuesWrapper != null && queuesWrapper.containsKey("queue")) {
                    Object queuesObj = queuesWrapper.get("queue");
                    if (queuesObj instanceof java.util.List<?> queues && !queues.isEmpty()) {
                        Map<String, Object> firstQueue = (Map<String, Object>) queues.get(0);
                        String queueId = (String) firstQueue.get("id");
                        log.debug("Found queue for owner {}: {}", ownerId, queueId);
                        return UUID.fromString(queueId);
                    }
                }
            }

            log.debug("No queue found for owner: {}", ownerId);
            return null;

        } catch (Exception e) {
            log.warn("Failed to find queue for owner {}: {}", ownerId, e.getMessage());
            return null;
        }
    }

    /**
     * Resolves the owner (environment ID) of a queue by its UUID.
     *
     * <p>Calls {@code GET /{domainId}/{solutionId}/queues/{queueId}} with broker-services
     * headers and parses the SIF 3.7 wrapped response {@code {"queue": {"ownerId": ...}}}.</p>
     *
     * <p>Returns {@code Optional.empty()} on 404, malformed body, or transport failure.
     * The caller treats empty as deny (fail-closed).</p>
     *
     * @param domainId   the domain ID
     * @param solutionId the solution ID
     * @param queueId    the queue UUID to look up
     * @return Optional containing the owner's environment UUID, or empty
     */
    public Optional<UUID> findQueueOwner(String domainId, String solutionId, UUID queueId) {
        try {
            org.sifunity.servicesconnector.dto.QueueResponse response = webClient.get()
                    .uri("/{domainId}/{solutionId}/queues/{queueId}",
                            domainId, solutionId, queueId)
                    .header("X-Environment-Fingerprint", "broker-services")
                    .header("X-Internal-Service-Token", internalServiceToken)
                    .retrieve()
                    .bodyToMono(org.sifunity.servicesconnector.dto.QueueResponse.class)
                    .block();

            if (response != null && response.queue() != null && response.queue().ownerId() != null) {
                log.debug("Queue owner resolved: queueId={}, ownerId={}", queueId, response.queue().ownerId());
                return Optional.of(response.queue().ownerId());
            }

            log.warn("Queue owner lookup returned malformed body: queueId={}", queueId);
            return Optional.empty();

        } catch (org.springframework.web.reactive.function.client.WebClientResponseException.NotFound e) {
            log.info("Queue not found during owner lookup: queueId={}", queueId);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Queue owner lookup failed: queueId={}, error={}", queueId, e.getMessage());
            return Optional.empty();
        }
    }
}
