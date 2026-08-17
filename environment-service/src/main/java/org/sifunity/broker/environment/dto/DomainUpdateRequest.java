// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Request DTO for updating a domain.
 * 
 * @param description New description for the domain
 */
public record DomainUpdateRequest(
    String description
) {}
