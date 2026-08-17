// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * Request DTO for updating a solution.
 * 
 * @param description New description for the solution
 */
public record SolutionUpdateRequest(
    String description
) {}
