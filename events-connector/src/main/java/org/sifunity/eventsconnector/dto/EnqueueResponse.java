// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Internal DTO representing the response from Queue Service enqueue API.
 * Contains the result of enqueuing a message to a specific queue.
 * 
 * <p>This DTO is used internally by the Events Connector when calling the Queue Service
 * to enqueue events to subscriber queues.</p>
 * 
 * @param messageId Unique identifier of the enqueued message
 * @param queueId Identifier of the queue where the message was enqueued
 * @param storageKey Storage key for the message payload
 * @param enqueuedAt Timestamp when the message was enqueued
 * @param status Status of the enqueued message (e.g., PENDING, ENQUEUED)
 */
public record EnqueueResponse(
    @NotNull(message = "messageId cannot be null")
    UUID messageId,
    
    @NotNull(message = "queueId cannot be null")
    UUID queueId,
    
    @NotBlank(message = "storageKey cannot be blank")
    String storageKey,
    
    @NotNull(message = "enqueuedAt cannot be null")
    Instant enqueuedAt,
    
    @NotBlank(message = "status cannot be blank")
    String status
) {
    /**
     * Creates a successful enqueue response.
     * 
     * @param messageId Unique identifier of the enqueued message
     * @param queueId Identifier of the queue
     * @param storageKey Storage key for the message payload
     * @param enqueuedAt Timestamp when the message was enqueued
     * @param status Status of the enqueued message
     * @return EnqueueResponse with the provided details
     */
    public static EnqueueResponse of(
        UUID messageId,
        UUID queueId,
        String storageKey,
        Instant enqueuedAt,
        String status
    ) {
        return new EnqueueResponse(messageId, queueId, storageKey, enqueuedAt, status);
    }
}
