// SPDX-License-Identifier: MIT
package org.sifunity.integration.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for domain data (test data deserialization).
 * Field names match environment-service {@code DomainDTO}.
 *
 * @param id Internal UUID identifier for the domain
 * @param domainId Business identifier for the domain
 * @param description Optional description of the domain's purpose
 * @param createdAt Timestamp when the domain was created
 */
public record DomainResponse(
    UUID id,
    String domainId,
    String description,
    Instant createdAt
) {}
