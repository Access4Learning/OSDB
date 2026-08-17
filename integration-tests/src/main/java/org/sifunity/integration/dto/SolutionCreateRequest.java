// SPDX-License-Identifier: MIT
package org.sifunity.integration.dto;

/**
 * Request DTO for creating a new solution (test data generation).
 *
 * @param domainId Identifier for the parent domain
 * @param solutionId Unique identifier for the solution within the domain
 * @param description Optional description of the solution's purpose
 */
public record SolutionCreateRequest(
    String domainId,
    String solutionId,
    String description
) {}
