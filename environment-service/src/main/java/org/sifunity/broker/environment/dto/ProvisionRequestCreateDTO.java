// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * DTO for creating a provision request (POST request body).
 * Wraps the provisionedZones containing the consumer's rights assertions.
 *
 * @param provisionedZones the provisioned zones with rights assertions (each right value should be REQUESTED)
 */
public record ProvisionRequestCreateDTO(
    ProvisionedZonesDTO provisionedZones
) {
}
