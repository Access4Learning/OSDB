// SPDX-License-Identifier: MIT
package org.sifunity.integration.dto;

/**
 * Request DTO for creating a new domain (test data generation).
 *
 * @param domainId Unique identifier for the domain
 * @param description Optional description of the domain's purpose
 */
public record DomainCreateRequest(
    String domainId,
    String description
) {}
