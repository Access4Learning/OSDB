// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.client;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.responsesconnector.dto.EnqueueResponseDTO;
import org.sifunity.responsesconnector.dto.QueueResponse;
import org.sifunity.responsesconnector.exception.QueueNotFoundException;
import org.sifunity.responsesconnector.exception.QueueServiceUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Client for communicating with the Queue Service.
 * <p>
 * Handles enqueuing response messages to consumer queues via the Queue Service API.
 * Implements retry logic with exponential backoff for transient failures and proper error
 * handling for different failure scenarios.
 * </p>
 *
 * <p>Uses Spring's synchronous {@link RestClient} with JDK HttpClient transport — no Reactor
 * or Netty dependencies.</p>
 *
 * <p><b>Retry Strategy:</b></p>
 * <ul>
 *   <li>Retries on 5xx server errors (transient failures)</li>
 *   <li>Does NOT retry on 4xx client errors</li>
 *   <li>Exponential backoff with configurable initial delay</li>
 *   <li>Maximum configurable retry attempts</li>
 * </ul>
 *
 * <p><b>Error Handling:</b></p>
 * <ul>
 *   <li>404 Not Found → QueueNotFoundException</li>
 *   <li>5xx Server Errors → QueueServiceUnavailableException (after retries exhausted)</li>
 *   <li>Timeout → QueueServiceUnavailableException</li>
 * </ul>
 *
 * <p><b>Requirements:</b> 9.1–9.6, 12.1–12.6</p>
 */
@Component
@Slf4j
public class QueueServiceClient {

    private final RestClient restClient;
    private final int maxRetries;
    private final long retryDelayMs;
    private final String internalServiceToken;

    public QueueServiceClient(
            @Qualifier("queueServiceRestClient") RestClient restClient,
            @Value("${queue.service.url}") String baseUrl,
            @Value("${queue.service.max-retries:3}") int maxRetries,
            @Value("${queue.service.retry-delay:100}") long retryDelayMs,
            @Value("${broker.internal-service-token:}") String internalServiceToken) {
        this.restClient = restClient;
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
        this.internalServiceToken = internalServiceToken;

        log.info("QueueServiceClient initialized: baseUrl={}, maxRetries={}, retryDelay={}ms",
            baseUrl, maxRetries, retryDelayMs);
    }

    /**
     * Enqueues a message to the specified queue via the Queue Service API.
     *
     * @param domainId   The domain ID for the request path
     * @param solutionId The solution ID for the request path
     * @param queueId    The UUID of the consumer's queue
     * @param envelope   The SIF message envelope to enqueue
     * @return The messageId assigned by the Queue Service
     * @throws QueueNotFoundException             if the queue does not exist (404)
     * @throws QueueServiceUnavailableException   if the Queue Service is unavailable or times out
     */
    public UUID enqueueMessage(String domainId, String solutionId, UUID queueId, SifMessageEnvelope<?> envelope) {
        log.info("Enqueuing message to queue: domainId={}, solutionId={}, queueId={}, messageId={}, requestId={}",
            domainId, solutionId, queueId, envelope.getMessageId(), envelope.getRequestId());

        int attempts = 0;
        long delay = retryDelayMs;

        while (true) {
            attempts++;
            try {
                EnqueueResponseDTO response = restClient.post()
                    .uri("/{domainId}/{solutionId}/queues/{queueId}/messages", domainId, solutionId, queueId)
                    .header("X-Environment-Fingerprint", "broker-services")
                    .header("X-Internal-Service-Token", internalServiceToken)
                    .body(envelope)
                    .retrieve()
                    .body(EnqueueResponseDTO.class);

                if (response == null) {
                    log.error("Queue Service returned null response: queueId={}", queueId);
                    throw new QueueServiceUnavailableException(
                        "Queue Service returned null response for queueId: " + queueId);
                }

                log.info("Message enqueued successfully: queueId={}, messageId={}",
                    queueId, response.getMessageId());
                return response.getMessageId();

            } catch (HttpClientErrorException.NotFound e) {
                log.error("Queue not found: queueId={}", queueId);
                throw new QueueNotFoundException("Queue not found: " + queueId);

            } catch (HttpClientErrorException e) {
                // 4xx errors — don't retry
                log.error("Queue Service client error: queueId={}, status={}, message={}",
                    queueId, e.getStatusCode(), e.getMessage());
                throw new QueueServiceUnavailableException(
                    "Queue Service error: " + e.getMessage(), e);

            } catch (HttpServerErrorException e) {
                // 5xx errors — retry
                if (attempts <= maxRetries) {
                    log.warn("Retrying Queue Service call: queueId={}, attempt={}, error={}",
                        queueId, attempts, e.getMessage());
                    sleep(delay);
                    delay *= 2; // exponential backoff
                    continue;
                }
                log.error("Queue Service unavailable after retries: queueId={}, status={}, message={}",
                    queueId, e.getStatusCode(), e.getMessage());
                throw new QueueServiceUnavailableException(
                    "Queue Service unavailable: " + e.getMessage(), e);

            } catch (Exception e) {
                // Network errors, timeouts — retry
                if (attempts <= maxRetries) {
                    log.warn("Retrying Queue Service call: queueId={}, attempt={}, error={}",
                        queueId, attempts, e.getMessage());
                    sleep(delay);
                    delay *= 2;
                    continue;
                }
                log.error("Unexpected error enqueuing message: queueId={}, error={}",
                    queueId, e.getMessage(), e);
                throw new QueueServiceUnavailableException(
                    "Failed to enqueue message: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Retrieves the owner environment ID for a given queue.
     *
     * <p>Calls the Queue Service to get queue metadata and extracts the owner's
     * environment UUID. Used to determine the consumer's environment for legacy
     * tag lookup when formatting outbound headers.</p>
     *
     * @param domainId   the domain ID for the request path
     * @param solutionId the solution ID for the request path
     * @param queueId    the UUID of the queue
     * @return the owner environment UUID, or null if lookup fails
     */
    public UUID getQueueOwnerEnvironmentId(String domainId, String solutionId, UUID queueId) {
        log.debug("Looking up queue owner: domainId={}, solutionId={}, queueId={}", domainId, solutionId, queueId);

        try {
            QueueResponse response = restClient.get()
                .uri("/{domainId}/{solutionId}/queues/{queueId}", domainId, solutionId, queueId)
                .header("X-Environment-Fingerprint", "broker-services")
                .header("X-Internal-Service-Token", internalServiceToken)
                .retrieve()
                .body(QueueResponse.class);

            if (response != null && response.queue() != null) {
                log.debug("Queue owner resolved: queueId={}, ownerId={}", queueId, response.queue().ownerId());
                return response.queue().ownerId();
            }
            return null;

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Queue not found during owner lookup: queueId={}", queueId);
            return null;
        } catch (Exception e) {
            log.warn("Failed to look up queue owner: queueId={}, error={}", queueId, e.getMessage());
            return null;
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
