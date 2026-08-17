// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for delivering messages from a queue.
 */
public record DeliverMessagesRequest(
    @NotNull(message = "Max messages is required")
    @Min(value = 1, message = "Max messages must be at least 1")
    @Max(value = 100, message = "Max messages cannot exceed 100")
    Integer maxMessages,
    
    @NotNull(message = "Visibility timeout is required")
    @Min(value = 1, message = "Visibility timeout must be at least 1 second")
    @Max(value = 3600, message = "Visibility timeout cannot exceed 3600 seconds")
    Integer visibilityTimeoutSeconds
) {
}
