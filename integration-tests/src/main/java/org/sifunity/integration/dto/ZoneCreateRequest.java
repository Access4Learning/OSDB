// SPDX-License-Identifier: MIT
package org.sifunity.integration.dto;

/**
 * Request DTO for creating a new zone (test data generation).
 *
 * @param solutionId Identifier for the parent solution
 * @param zoneId Unique identifier for the zone within the solution
 * @param description Optional description of the zone's purpose
 */
public record ZoneCreateRequest(
    String solutionId,
    String zoneId,
    String description
) {}
