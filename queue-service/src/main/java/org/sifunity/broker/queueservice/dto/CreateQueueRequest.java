// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Request DTO for creating a new queue.
 * Includes SIF 3.7 maxConcurrentConnections parameter.
 */
public record CreateQueueRequest(
    @NotBlank(message = "Queue name is required")
    String name,
    
    @NotNull(message = "Owner ID is required")
    UUID ownerId,
    
    @Positive(message = "Max retries must be positive")
    Integer maxRetries,
    
    @Positive(message = "Retry delay must be positive")
    Long retryDelayMs,
    
    @Positive(message = "Max concurrent connections must be positive")
    Integer maxConcurrentConnections
) {
}
