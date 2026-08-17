// SPDX-License-Identifier: MIT
package org.sifunity.integration.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for solution data (test data deserialization).
 * Field names match environment-service {@code SolutionDTO}.
 *
 * @param id Internal UUID identifier for the solution
 * @param domainId Business identifier for the parent domain
 * @param solutionId Business identifier for the solution
 * @param description Optional description of the solution's purpose
 * @param createdAt Timestamp when the solution was created
 */
public record SolutionResponse(
    UUID id,
    String domainId,
    String solutionId,
    String description,
    Instant createdAt
) {}
