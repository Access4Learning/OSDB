// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.service;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.eventsconnector.client.QueueServiceClient;
import org.sifunity.eventsconnector.dto.DeliveryResultStatus;
import org.sifunity.eventsconnector.dto.QueueDeliveryResult;
import org.sifunity.eventsconnector.metrics.EventMetrics;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for enqueuing events to multiple subscriber queues concurrently.
 * 
 * <p>This service handles the fan-out distribution of events to multiple subscriber queues.
 * It uses {@link CompletableFuture} with a bounded {@link ExecutorService} to process
 * multiple queues concurrently, ensuring efficient event distribution even when there
 * are many subscribers.</p>
 * 
 * <p><strong>Per Requirement 7: Events are intentionally excluded from matrix parameter
 * injection.</strong> This service enqueues the raw {@link SifMessageEnvelope} without
 * any relativeServicePath formatting or LegacyTagLookupService consultation. The
 * {@code zoneId} and {@code contextId} are conveyed as envelope fields (delivered as
 * HTTP headers when subscribers poll), regardless of the SIF_LEGACY_MATRIX_PARAMS tag.</p>
 * 
 * <p>Key features:</p>
 * <ul>
 *   <li>Concurrent processing: Enqueues to multiple queues in parallel</li>
 *   <li>Error resilience: Continues processing even if some queues fail</li>
 *   <li>Result aggregation: Collects all results (success and failure) for reporting</li>
 *   <li>Comprehensive logging: Logs each queue delivery attempt and outcome</li>
 * </ul>
 * 
 * <p>Requirements:</p>
 * <ul>
 *   <li>3: Event Distribution to Subscriber Queues - Enqueue events to queues</li>
 *   <li>4: Fan-out Event Delivery - Concurrent processing of multiple queues</li>
 *   <li>9: Queue Service Integration - Call Queue Service API for enqueuing</li>
 * </ul>
 * 
 * @see QueueServiceClient
 * @see QueueDeliveryResult
 */
@Service
@Slf4j
public class QueueEnqueueService {

    private final QueueServiceClient queueServiceClient;
    private final EventMetrics eventMetrics;
    private final ExecutorService fanOutExecutor;

    public QueueEnqueueService(QueueServiceClient queueServiceClient, EventMetrics eventMetrics) {
        this.queueServiceClient = queueServiceClient;
        this.eventMetrics = eventMetrics;
        this.fanOutExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Enqueues an event to multiple subscriber queues concurrently.
     * 
     * <p>This method distributes the event to all specified queues in parallel using
     * {@link CompletableFuture}. It handles failures gracefully - if one queue fails,
     * processing continues for other queues. All results (success and failure) are
     * collected and returned.</p>
     * 
     * @param domainId Domain identifier
     * @param solutionId Solution identifier
     * @param queueIds List of queue identifiers to enqueue to - must not be null or empty
     * @param envelope SIF message envelope containing the event data - must not be null
     * @return List of QueueDeliveryResult containing the outcome for each queue - never null
     * @throws IllegalArgumentException if queueIds is null or empty, or if envelope is null
     */
    public List<QueueDeliveryResult> enqueueToMultipleQueues(
        String domainId,
        String solutionId,
        List<UUID> queueIds,
        SifMessageEnvelope<?> envelope
    ) {
        // Validate input parameters
        if (queueIds == null || queueIds.isEmpty()) {
            throw new IllegalArgumentException("queueIds must not be null or empty");
        }
        if (envelope == null) {
            throw new IllegalArgumentException("envelope must not be null");
        }

        log.info("Enqueuing event to {} queue(s): messageId={}",
            queueIds.size(), envelope.getMessageId());

        // Use CompletableFuture for concurrent processing of all queues
        List<CompletableFuture<QueueDeliveryResult>> futures = queueIds.stream()
            .map(queueId -> CompletableFuture.supplyAsync(
                () -> enqueueToQueue(domainId, solutionId, queueId, envelope),
                fanOutExecutor
            ))
            .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        List<QueueDeliveryResult> results = futures.stream()
            .map(CompletableFuture::join)
            .toList();

        // Log summary of results
        long successCount = results.stream()
            .filter(r -> r.status() == DeliveryResultStatus.SUCCESS)
            .count();
        long failureCount = results.size() - successCount;

        log.info("Event enqueue complete: messageId={}, success={}, failed={}, total={}",
            envelope.getMessageId(), successCount, failureCount, results.size());

        return results;
    }

    /**
     * Enqueues an event to a single queue, handling errors gracefully.
     * 
     * <p>This method wraps the QueueServiceClient call in error handling logic.
     * If the enqueue succeeds, it returns a success result. If it fails, it logs
     * the error and returns a failure result instead of propagating the exception.
     * This ensures that one queue failure doesn't prevent processing of other queues.</p>
     * 
     * @param domainId Domain identifier
     * @param solutionId Solution identifier
     * @param queueId Identifier of the queue to enqueue to
     * @param envelope SIF message envelope containing the event data
     * @return QueueDeliveryResult containing the outcome (success or failure)
     */
    private QueueDeliveryResult enqueueToQueue(
        String domainId,
        String solutionId,
        UUID queueId,
        SifMessageEnvelope<?> envelope
    ) {
        log.debug("Attempting to enqueue to queue: queueId={}, messageId={}",
            queueId, envelope.getMessageId());

        long startTime = System.nanoTime();

        try {
            var response = queueServiceClient.enqueueMessage(domainId, solutionId, queueId, envelope);
            Duration duration = Duration.ofNanos(System.nanoTime() - startTime);

            // Record queue enqueue latency metric
            eventMetrics.recordQueueEnqueueLatency(duration, queueId.toString(), true);

            log.info("Successfully enqueued to queue: queueId={}, messageId={}, storageKey={}",
                queueId, envelope.getMessageId(), response.storageKey());
            return QueueDeliveryResult.success(queueId.toString());
        } catch (Exception error) {
            Duration duration = Duration.ofNanos(System.nanoTime() - startTime);

            // Record queue enqueue latency metric (failure)
            eventMetrics.recordQueueEnqueueLatency(duration, queueId.toString(), false);

            log.error("Failed to enqueue to queue: queueId={}, messageId={}, error={}",
                queueId, envelope.getMessageId(), error.getMessage(), error);
            return QueueDeliveryResult.failure(
                queueId.toString(),
                error.getMessage()
            );
        }
    }
}
