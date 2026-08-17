// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.eventsconnector.dto.EnqueueResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * HTTP client for Queue Service communication.
 * Implements retry logic with exponential backoff, circuit breaker pattern, and timeout handling.
 * 
 * <p>This client is responsible for enqueuing events to subscriber queues via the Queue Service.
 * It includes resilience patterns to handle transient failures and prevent cascading failures.</p>
 * 
 * <p>Uses Spring's synchronous {@link RestClient} with JDK HttpClient transport — no Reactor
 * or Netty dependencies. The RestClient throws {@code HttpClientErrorException} (4xx) and
 * {@code HttpServerErrorException} (5xx) by default, so no custom error mapping is needed.</p>
 * 
 * <p>Requirements:</p>
 * <ul>
 *   <li>3: Event Distribution to Subscriber Queues - Enqueue events to queues</li>
 *   <li>9: Queue Service Integration - RestClient for synchronous HTTP calls</li>
 *   <li>12: Service Call Timeouts - Configurable connection and read timeouts</li>
 *   <li>13: Retry Strategy - Exponential backoff with 3 attempts for 5xx errors</li>
 * </ul>
 */
@Component
@Slf4j
public class QueueServiceClient {
    
    private final RestClient restClient;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final String queueServiceUrl;
    private final String internalServiceToken;
    
    /**
     * Constructs a QueueServiceClient with RestClient, retry, and circuit breaker.
     * 
     * @param restClient RestClient configured for Queue Service
     * @param retry Retry instance for handling transient failures
     * @param circuitBreaker Circuit breaker instance for preventing cascading failures
     * @param queueServiceUrl Base URL of the Queue Service (used in logging)
     * @param internalServiceToken Internal service token for broker-services trust bypass
     */
    public QueueServiceClient(
        @Qualifier("queueServiceRestClient") RestClient restClient,
        @Qualifier("queueServiceRetry") Retry retry,
        @Qualifier("queueServiceCircuitBreaker") CircuitBreaker circuitBreaker,
        @Value("${queue-service.url}") String queueServiceUrl,
        @Value("${broker.internal-service-token:}") String internalServiceToken
    ) {
        this.restClient = restClient;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
        this.queueServiceUrl = queueServiceUrl;
        this.internalServiceToken = internalServiceToken;
    }
    
    /**
     * Enqueues a message to the specified queue.
     * 
     * <p>This method calls the Queue Service enqueue API to deliver an event to a subscriber's queue.
     * It includes:</p>
     * <ul>
     *   <li>Retry logic: 3 attempts with exponential backoff for 5xx errors</li>
     *   <li>Timeout: Configured in RestClient via JdkClientHttpRequestFactory</li>
     *   <li>Circuit breaker: Prevents cascading failures</li>
     * </ul>
     * 
     * <p>The method does NOT retry on 4xx client errors as these indicate invalid
     * requests that won't succeed on retry.</p>
     * 
     * @param domainId Domain identifier
     * @param solutionId Solution identifier
     * @param queueId Identifier of the queue to enqueue to
     * @param envelope SIF message envelope containing the event data
     * @return EnqueueResponse containing the enqueue result
     */
    public EnqueueResponse enqueueMessage(
        String domainId,
        String solutionId,
        UUID queueId,
        SifMessageEnvelope<?> envelope
    ) {
        log.debug("Enqueuing message to queue: domainId={}, solutionId={}, queueId={}, messageId={}", 
            domainId, solutionId, queueId, envelope.getMessageId());
        
        try {
            EnqueueResponse response = circuitBreaker.executeSupplier(() ->
                retry.executeSupplier(() ->
                    restClient.post()
                        .uri("/{domainId}/{solutionId}/queues/{queueId}/messages",
                            domainId, solutionId, queueId)
                        .header("X-Environment-Fingerprint", "broker-services")
                        .header("X-Internal-Service-Token", internalServiceToken)
                        .body(envelope)
                        .retrieve()
                        .body(EnqueueResponse.class)
                )
            );
            
            log.info("Successfully enqueued message: queueId={}, messageId={}, storageKey={}", 
                queueId, envelope.getMessageId(), response.storageKey());
            
            return response;
        } catch (Exception error) {
            log.error("Failed to enqueue message: queueId={}, messageId={}", 
                queueId, envelope.getMessageId(), error);
            throw error;
        }
    }
    
    /**
     * Pings the Queue Service to check if it's available.
     * Used by health indicators to monitor service availability.
     */
    public void ping() {
        log.debug("Pinging Queue Service at {}", queueServiceUrl);
        
        restClient.get()
            .uri("/actuator/health")
            .retrieve()
            .toBodilessEntity();
        
        log.debug("Queue Service is available");
    }
}
