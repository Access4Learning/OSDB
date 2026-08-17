// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.dto;

import java.util.UUID;

/**
 * Response DTO for accepted purge operations.
 * Returned with HTTP 202 Accepted when a purge request is successfully submitted.
 */
public record PurgeAcceptedResponse(
    UUID queueId,
    String status,
    String message
) {}
