// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.dto;

import org.sifunity.broker.queueservice.domain.MessageStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single message with payload.
 */
public record MessageResponse(
    UUID messageId,
    UUID queueId,
    String storageKey,
    Instant enqueuedAt,
    Instant deliveredAt,
    Integer deliveryAttempts,
    MessageStatus status,
    Object payload
) {
}
