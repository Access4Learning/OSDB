// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object representing a message queue aligned with SIF 3.7.
 * 
 * <p>A queue stores messages for asynchronous delivery to consumers.
 * Each queue is owned by an environment and has configurable retry settings.</p>
 * 
 * <p>Per SIF 3.7, the queue object exposes: {@code id}, {@code polling}, {@code ownerId},
 * {@code name}, {@code queueUri}, {@code ownerUri}, {@code idleTimeout}, {@code minWaitTime},
 * {@code maxConcurrentConnections}, {@code created}, {@code lastAccessed}, {@code lastModified},
 * {@code messageCount}. Internal fields ({@code maxRetries}, {@code retryDelayMs}) are excluded
 * from API responses via {@code @JsonIgnore} on the accessor (matching the project-wide
 * pattern used by ZoneDTO, ContextDTO, SubscriptionDTO, and SifErrorResponse).</p>
 * 
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueueDTO(
    /**
     * Unique identifier for the queue.
     */
    @NotNull
    UUID id,
    
    /**
     * Name of the queue.
     */
    @NotBlank
    String name,
    
    /**
     * ID of the environment that owns this queue.
     */
    @NotNull
    UUID ownerId,
    
    /**
     * Polling mode. Always IMMEDIATE for this broker.
     */
    String polling,
    
    /**
     * Fully-qualified URI to the queue's messages endpoint.
     */
    String queueUri,
    
    /**
     * Maximum number of concurrent connections (informational only).
     */
    Integer maxConcurrentConnections,
    
    /**
     * Timestamp when the queue was created.
     */
    @NotNull
    Instant created,
    
    /**
     * Timestamp when a message was last delivered from this queue.
     */
    Instant lastAccessed,
    
    /**
     * Timestamp when a message was last enqueued to this queue.
     */
    Instant lastModified,
    
    /**
     * Maximum number of delivery retry attempts.
     * Internal implementation detail — not part of SIF 3.7 queue schema.
     */
    @JsonIgnore
    Integer maxRetries,
    
    /**
     * Delay in milliseconds between retry attempts.
     * Internal implementation detail — not part of SIF 3.7 queue schema.
     */
    @JsonIgnore
    Long retryDelayMs,
    
    /**
     * Current number of messages in the queue.
     */
    Long messageCount
) {
    /** Ensure maxRetries is not serialized in API responses (not part of SIF 3.7 queue schema). */
    @JsonIgnore
    @Override
    public Integer maxRetries() { return maxRetries; }

    /** Ensure retryDelayMs is not serialized in API responses (not part of SIF 3.7 queue schema). */
    @JsonIgnore
    @Override
    public Long retryDelayMs() { return retryDelayMs; }
}
