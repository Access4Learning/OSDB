// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * DTO representing a single infrastructure service entry.
 *
 * @param name  the service name (e.g., "environment", "requestsConnector")
 * @param value the base URL for the service
 */
public record InfrastructureServiceDTO(
    String name,
    String value
) {
}
