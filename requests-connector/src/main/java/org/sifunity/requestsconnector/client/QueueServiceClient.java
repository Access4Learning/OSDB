// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.client;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.sifunity.requestsconnector.exception.ServiceUnavailableException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * HTTP client for communicating with the Queue Service.
 * Provides methods for enqueuing messages to queues.
 * 
 * <p>This client is responsible for enqueuing request messages to provider queues
 * in the DELAYED_to_DELAYED routing pattern. It calls the Queue Service enqueue API
 * and returns the messageId for tracking.</p>
 * 
 * <p>Requirements:</p>
 * <ul>
 *   <li>4.4: Enqueue request message to provider's queue via Queue Service API</li>
 * </ul>
 */
@Component
@Slf4j
public class QueueServiceClient {
    
    private final WebClient webClient;
    private final String baseUrl;
    private final String internalServiceToken;
    
    /**
     * Constructs a QueueServiceClient with the configured WebClient and base URL.
     * 
     * @param webClientBuilder WebClient builder configured with timeouts
     * @param baseUrl Queue Service base URL from configuration
     * @param internalServiceToken Internal service token for broker-services trust bypass
     */
    public QueueServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${queue.service.url}") String baseUrl,
            @Value("${broker.internal-service-token:}") String internalServiceToken) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.baseUrl = baseUrl;
        this.internalServiceToken = internalServiceToken;
    }
    
    /**
     * Enqueues a message to the specified queue.
     * 
     * <p>This method calls the Queue Service enqueue API to deliver a request message
     * to a provider's queue. The message is enqueued for asynchronous processing by
     * the provider.</p>
     * 
     * <p>The method returns the messageId assigned by the Queue Service, which can be
     * used for tracking and correlation. If the Queue Service is unavailable, this
     * method will throw a WebClientResponseException which should be handled by the
     * caller (typically resulting in HTTP 503 Service Unavailable).</p>
     * 
     * @param queueId the UUID of the queue to enqueue to
     * @param envelope the SIF message envelope containing the request data
     * @return the messageId assigned by the Queue Service
     * @throws org.springframework.web.reactive.function.client.WebClientResponseException 
     *         if Queue Service returns an error (e.g., 404 queue not found, 503 unavailable)
     */
    public UUID enqueueMessage(UUID queueId, SifMessageEnvelope<?> envelope) {
        return enqueueMessage(queueId, envelope, "GLOBAL_DOMAIN", "GLOBAL_SOLUTION");
    }

    /**
     * Enqueues a message to the specified queue under the given domain/solution.
     */
    public UUID enqueueMessage(UUID queueId, SifMessageEnvelope<?> envelope,
                               String domainId, String solutionId) {
        log.debug("Enqueuing message to queue: queueId={}, messageId={}, domainId={}, solutionId={}", 
            queueId, envelope.getMessageId(), domainId, solutionId);
        
        try {
            // Send SifMessageEnvelope directly — same pattern as events-connector
            // and responses-connector. Jackson serializes the envelope fields which
            // map to EnqueueMessageRequest on the queue-service side.
            Map<String, Object> response = webClient.post()
                .uri("/{domainId}/{solutionId}/queues/{queueId}/messages", 
                     domainId, solutionId, queueId)
                .header("X-Environment-Fingerprint", "broker-services")
                .header("X-Internal-Service-Token", internalServiceToken)
                .bodyValue(envelope)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            // Extract messageId from response
            UUID messageId = UUID.fromString((String) response.get("messageId"));
            
            log.info("Successfully enqueued message: queueId={}, messageId={}", 
                queueId, messageId);
            
            return messageId;
            
        } catch (WebClientRequestException e) {
            // Transport-level failure: the queue-service could not be reached at all.
            // Surface as a clear 503 rather than a generic 500.
            log.error("queue-service unreachable while enqueuing message: queueId={}, messageId={}", 
                queueId, envelope.getMessageId(), e);
            throw new ServiceUnavailableException("queue-service", e);

        } catch (Exception e) {
            log.error("Failed to enqueue message: queueId={}, messageId={}", 
                queueId, envelope.getMessageId(), e);
            throw e;
        }
    }

    /**
     * Resolves the owner (environment ID) of a queue by its UUID.
     *
     * <p>Calls {@code GET /{domainId}/{solutionId}/queues/{queueId}} with broker-services
     * headers and parses the SIF 3.7 wrapped response {@code {"queue": {"ownerId": ...}}}.</p>
     *
     * <p>Returns {@code Optional.empty()} on 404 (queue not found), malformed body, or any
     * transport failure. The caller treats empty as deny (fail-closed).</p>
     *
     * @param domainId   the domain ID
     * @param solutionId the solution ID
     * @param queueId    the queue UUID to look up
     * @return Optional containing the owner's environment UUID, or empty
     */
    public Optional<UUID> findQueueOwner(String domainId, String solutionId, UUID queueId) {
        try {
            org.sifunity.requestsconnector.dto.QueueResponse response = webClient.get()
                    .uri("/{domainId}/{solutionId}/queues/{queueId}",
                            domainId, solutionId, queueId)
                    .header("X-Environment-Fingerprint", "broker-services")
                    .header("X-Internal-Service-Token", internalServiceToken)
                    .retrieve()
                    .bodyToMono(org.sifunity.requestsconnector.dto.QueueResponse.class)
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
