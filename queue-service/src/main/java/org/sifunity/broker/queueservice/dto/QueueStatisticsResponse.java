// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.dto;

import java.util.UUID;

/**
 * Response DTO for queue statistics.
 */
public record QueueStatisticsResponse(
    UUID queueId,
    Long pendingCount,
    Long deliveredCount,
    Long deadLetterCount,
    Long totalProcessed
) {
}
