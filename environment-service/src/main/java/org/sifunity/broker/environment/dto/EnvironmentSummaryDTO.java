// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.util.UUID;

/**
 * Summary DTO for an environment, used in login responses and environment lists.
 *
 * @param id              UUID of the environment
 * @param applicationName Name of the registered application
 */
public record EnvironmentSummaryDTO(
    UUID id,
    String applicationName
) {}
