// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for solution information.
 * 
 * @param id Internal UUID identifier for the solution
 * @param domainId Business identifier for the parent domain
 * @param solutionId Business identifier for the solution (unique within domain)
 * @param description Optional description of the solution's purpose
 * @param createdAt Timestamp when the solution was created
 */
public record SolutionDTO(
    UUID id,
    String domainId,
    String solutionId,
    String description,
    Instant createdAt
) {}
