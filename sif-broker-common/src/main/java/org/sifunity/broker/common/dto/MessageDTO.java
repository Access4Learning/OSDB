// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import jakarta.validation.constraints.*;
import org.sifunity.broker.common.constant.MessageStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object representing a queued message.
 * 
 * <p>A message represents a SIF message stored in a queue awaiting delivery
 * to a consumer. The message tracks delivery attempts and status.</p>
 * 
 * <p>This DTO is immutable and includes validation annotations to ensure
 * data integrity.</p>
 * 
 * @see MessageStatus
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public record MessageDTO(
    /**
     * Unique identifier for the message.
     */
    @NotNull
    UUID id,
    
    /**
     * ID of the queue containing this message.
     */
    @NotNull
    UUID queueId,
    
    /**
     * Storage key for the message payload in object storage.
     */
    @NotBlank
    String storageKey,
    
    /**
     * Timestamp when the message was enqueued.
     */
    @NotNull
    Instant enqueuedAt,
    
    /**
     * Timestamp when the message was delivered (null if not yet delivered).
     */
    Instant deliveredAt,
    
    /**
     * Number of delivery attempts made.
     */
    Integer deliveryAttempts,
    
    /**
     * Current status of the message.
     */
    @NotNull
    MessageStatus status
) {
}
