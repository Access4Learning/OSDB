// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import org.sifunity.broker.common.exception.QueueNotFoundException;
import org.sifunity.broker.common.exception.StorageException;
import org.sifunity.broker.common.util.StorageKeyGenerator;
import org.sifunity.broker.common.util.TracingUtilsBase;
import org.sifunity.broker.queueservice.domain.MessageMetadata;
import org.sifunity.broker.queueservice.domain.MessageStatus;
import org.sifunity.broker.queueservice.domain.Queue;
import org.sifunity.broker.queueservice.metrics.QueueMetricsService;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing message operations including enqueuing messages.
 *
 * <p>This service handles:
 * <ul>
 *   <li>Message enqueuing with payload storage via StorageProvider</li>
 *   <li>Stream entry creation via StreamMessageRepository (XADD)</li>
 *   <li>Reference count increment via RefCountService</li>
 *   <li>Storage key generation using the SIF 3.7 hierarchical format</li>
 *   <li>Updating lastModified on the Queue entity after enqueue</li>
 * </ul>
 *
 * <p>Integrates with:
 * <ul>
 *   <li>{@link QueueRepository} — queue validation and lastModified updates</li>
 *   <li>{@link StreamMessageRepository} — Redis Streams XADD for enqueue</li>
 *   <li>{@link RefCountService} — atomic ref count increment for shared payloads</li>
 *   <li>{@link StorageProvider} — message payload persistence (S3/filesystem)</li>
 * </ul>
 *
 * @see StreamMessageRepository
 * @see RefCountService
 * @see org.sifunity.broker.common.util.StorageKeyGenerator
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final QueueRepository queueRepository;
    private final StreamMessageRepository streamMessageRepository;
    private final RefCountService refCountService;
    private final StorageProvider storageProvider;
    private final TracingUtilsBase tracingUtils;
    private final ObjectMapper objectMapper;
    private final String storageKeyPrefix;
    private final QueueMetricsService queueMetricsService;

    /**
     * Constructs a MessageService with required dependencies.
     *
     * @param queueRepository         repository for queue operations
     * @param streamMessageRepository Redis Streams message repository
     * @param refCountService         service for ref count management
     * @param storageProvider         provider for message payload storage
     * @param tracingUtils            utility for creating custom spans
     * @param objectMapper            Jackson ObjectMapper for JSON serialization
     * @param storageKeyPrefix        configurable storage key prefix
     * @param queueMetricsService     service for recording queue metrics (optional)
     */
    public MessageService(QueueRepository queueRepository,
                          StreamMessageRepository streamMessageRepository,
                          RefCountService refCountService,
                          StorageProvider storageProvider,
                          TracingUtilsBase tracingUtils,
                          ObjectMapper objectMapper,
                          @Value("${message.storage.key-prefix:}") String storageKeyPrefix,
                          QueueMetricsService queueMetricsService) {
        this.queueRepository = queueRepository;
        this.streamMessageRepository = streamMessageRepository;
        this.refCountService = refCountService;
        this.storageProvider = storageProvider;
        this.tracingUtils = tracingUtils;
        this.objectMapper = objectMapper;
        this.storageKeyPrefix = storageKeyPrefix;
        this.queueMetricsService = queueMetricsService;
    }

    /**
     * Enqueues a message to the specified queue using Redis Streams.
     *
     * <p>This method:
     * <ol>
     *   <li>Validates that the queue exists</li>
     *   <li>Generates a message ID if not provided</li>
     *   <li>Generates a hierarchical storage key using the SIF 3.7 format</li>
     *   <li>Stores the message payload to the storage provider</li>
     *   <li>Appends a stream entry via {@link StreamMessageRepository#enqueue(String, String, UUID, String, Instant)}</li>
     *   <li>Increments the ref count via {@link RefCountService#increment(String)}</li>
     *   <li>Updates {@code lastModified} on the Queue entity</li>
     * </ol>
     *
     * <p>If storage fails, no stream entry is created (atomic operation).
     * If stream entry creation fails, the stored payload is cleaned up.
     *
     * @param queueId       the ID of the queue to enqueue the message to
     * @param messageId     the message ID (generated if null)
     * @param domainId      human-readable domain identifier
     * @param solutionId    human-readable solution identifier
     * @param environmentId the environment UUID
     * @param zone          the zone identifier
     * @param context       the context identifier (may be null, defaults to "default")
     * @param messageType   one of: event, request, response
     * @param service       the service/object name (e.g., "StudentPersonal")
     * @param payload       the message payload to store
     * @return the created message metadata
     * @throws QueueNotFoundException   if the queue does not exist
     * @throws StorageException         if payload storage fails
     * @throws IllegalArgumentException if messageType is invalid
     */
    public MessageMetadata enqueueMessage(UUID queueId, UUID messageId,
                                          String domainId, String solutionId, UUID environmentId,
                                          UUID sourceEnvironmentId,
                                          String zone, String context, String messageType,
                                          String service, Object payload) {
        return enqueueMessage(queueId, messageId, domainId, solutionId, environmentId,
                sourceEnvironmentId, zone, context, messageType, service, payload, null);
    }

    @Timed(value = "sif.queue.enqueue.duration",
           description = "Time to enqueue a message to a queue",
           percentiles = {0.5, 0.95, 0.99})
    public MessageMetadata enqueueMessage(UUID queueId, UUID messageId,
                                          String domainId, String solutionId, UUID environmentId,
                                          UUID sourceEnvironmentId,
                                          String zone, String context, String messageType,
                                          String service, Object payload,
                                          Map<String, String> sifHeaders) {
        log.debug("Enqueuing message to queue: queueId={}, messageId={}, domainId={}, solutionId={}, " +
                  "environmentId={}, sourceEnvironmentId={}, zone={}, context={}, messageType={}, service={}",
                queueId, messageId, domainId, solutionId, environmentId, sourceEnvironmentId, zone, context, messageType, service);

        // Validate queue exists
        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> {
                    log.error("Queue not found: queueId={}", queueId);
                    return new QueueNotFoundException("Queue not found: " + queueId);
                });

        // Generate message ID if not provided
        UUID finalMessageId = messageId != null ? messageId : UUID.randomUUID();
        log.debug("Using message ID: {}", finalMessageId);

        // Create custom span for enqueue operation
        Map<String, String> spanAttributes = new HashMap<>();
        spanAttributes.put("queue.name", queue.getName());
        spanAttributes.put("queue.id", queueId.toString());
        spanAttributes.put("message.id", finalMessageId.toString());
        spanAttributes.put("message.type", "enqueue");
        spanAttributes.put("domain.id", domainId);
        spanAttributes.put("solution.id", solutionId);
        spanAttributes.put("environment.id", environmentId.toString());
        spanAttributes.put("zone", zone);
        if (context != null) {
            spanAttributes.put("context", context);
        }
        if (service != null) {
            spanAttributes.put("service", service);
        }

        return tracingUtils.traceOperation("queue.enqueue_message", spanAttributes, () -> {
            // Generate storage key using SIF 3.7 hierarchical format
            String storageKey = StorageKeyGenerator.generateKey(
                    storageKeyPrefix, domainId, solutionId, sourceEnvironmentId.toString(),
                    zone, context, messageType, service, finalMessageId);
            log.debug("Generated storage key: {}", storageKey);

            // Use ref count as coordination: first thread to increment 0→1 writes the payload.
            // This avoids race conditions during parallel fan-out.
            String refCountKey = refCountService.refCountKey(domainId, solutionId, storageKey);
            long newRefCount = refCountService.increment(refCountKey);
            log.debug("Incremented ref count for storageKey={}, newCount={}", storageKey, newRefCount);

            // Only the first reference (0→1) writes the payload
            if (newRefCount == 1) {
                try {
                    storageProvider.store(storageKey, payload);
                    log.debug("Stored message payload: storageKey={}", storageKey);
                } catch (Exception e) {
                    // Roll back ref count on storage failure
                    try { refCountService.decrementAndGet(refCountKey); } catch (Exception ignored) {}
                    log.error("Failed to store message payload: storageKey={}, error={}", storageKey, e.getMessage(), e);
                    throw new StorageException("Failed to store message payload: " + e.getMessage(), e);
                }
            } else {
                log.debug("Payload already stored by another thread (fan-out), skipping write: storageKey={}", storageKey);
            }

            // Append stream entry via XADD
            Instant enqueuedAt = Instant.now();
            String streamKey = streamMessageRepository.streamKey(domainId, solutionId, environmentId, queueId);
            String groupName = streamMessageRepository.groupName(queueId);

            // Serialize SIF headers to JSON for Redis stream storage
            String headersJson = null;
            if (sifHeaders != null && !sifHeaders.isEmpty()) {
                try {
                    headersJson = objectMapper.writeValueAsString(sifHeaders);
                } catch (Exception e) {
                    log.warn("Failed to serialize SIF headers, skipping: {}", e.getMessage());
                }
            }

            try {
                String streamId = streamMessageRepository.enqueue(
                        streamKey, groupName, finalMessageId, storageKey, enqueuedAt, headersJson);
                log.info("Successfully enqueued message: messageId={}, queueId={}, storageKey={}, streamId={}",
                        finalMessageId, queueId, storageKey, streamId);
            } catch (Exception e) {
                // If stream entry creation fails, decrement ref count and clean up if last reference
                log.error("Failed to create stream entry, cleaning up: messageId={}", finalMessageId, e);
                try {
                    long remaining = refCountService.decrementAndGet(refCountKey);
                    if (remaining <= 0) {
                        storageProvider.delete(storageKey);
                        log.debug("Cleaned up payload after stream entry failure (last reference)");
                    }
                } catch (Exception cleanupException) {
                    log.error("Failed to clean up after stream entry failure: storageKey={}",
                            storageKey, cleanupException);
                }
                throw new RuntimeException("Failed to enqueue message to stream: " + e.getMessage(), e);
            }

            // Record enqueue metric
            try {
                queueMetricsService.recordEnqueue(queue.getName(), messageType);
            } catch (Exception e) {
                log.warn("Failed to record enqueue metric: {}", e.getMessage());
            }

            // Update lastModified on Queue entity
            updateLastModified(queue);

            // Return the created message metadata
            return MessageMetadata.builder()
                    .messageId(finalMessageId)
                    .queueId(queueId)
                    .storageKey(storageKey)
                    .status(MessageStatus.PENDING)
                    .deliveryAttempts(0)
                    .enqueuedAt(enqueuedAt)
                    .build();
        });
    }

    /**
     * Updates the lastModified timestamp on the Queue entity after successful enqueue.
     *
     * @param queue the Queue entity to update
     */
    private void updateLastModified(Queue queue) {
        try {
            queue.setLastModified(Instant.now());
            queueRepository.save(queue);
            log.debug("Updated lastModified for queue: queueId={}", queue.getId());
        } catch (Exception e) {
            log.warn("Failed to update lastModified for queue: queueId={}, error={}",
                    queue.getId(), e.getMessage(), e);
        }
    }
}
