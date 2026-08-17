// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for domain information.
 * 
 * @param id Internal UUID identifier for the domain
 * @param domainId Business identifier for the domain (unique)
 * @param description Optional description of the domain's purpose
 * @param createdAt Timestamp when the domain was created
 */
public record DomainDTO(
    UUID id,
    String domainId,
    String description,
    Instant createdAt
) {}
