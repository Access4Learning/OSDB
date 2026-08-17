// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for triggering cascade cleanup of Redis keys and storage objects
 * when a domain or solution is deleted.
 *
 * <p>If {@code solutionId} is non-null, cleanup is scoped to that solution.
 * Otherwise, cleanup covers the entire domain.</p>
 */
public record CascadeCleanupRequest(
    @NotBlank(message = "domainId is required")
    String domainId,

    String solutionId
) {
}
