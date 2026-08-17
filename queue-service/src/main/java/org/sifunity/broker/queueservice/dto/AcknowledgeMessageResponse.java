// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for message acknowledgment operation.
 */
public record AcknowledgeMessageResponse(
    UUID messageId,
    String status,
    Instant acknowledgedAt
) {
}
