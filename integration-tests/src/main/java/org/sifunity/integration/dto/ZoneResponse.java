// SPDX-License-Identifier: MIT
package org.sifunity.integration.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for zone data (test data deserialization).
 * Field names match environment-service {@code ZoneDTO}.
 *
 * @param id Internal UUID identifier for the zone
 * @param solutionId Identifier for the parent solution
 * @param zoneId Business identifier for the zone
 * @param description Optional description of the zone's purpose
 * @param createdAt Timestamp when the zone was created
 */
public record ZoneResponse(
    UUID id,
    String solutionId,
    String zoneId,
    String description,
    Instant createdAt
) {}
