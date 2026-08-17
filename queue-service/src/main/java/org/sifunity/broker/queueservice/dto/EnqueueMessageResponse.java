// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.dto;

import org.sifunity.broker.queueservice.domain.MessageStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for message enqueue operation.
 */
public record EnqueueMessageResponse(
    UUID messageId,
    UUID queueId,
    String storageKey,
    Instant enqueuedAt,
    MessageStatus status
) {
}
