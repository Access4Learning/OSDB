// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * DTO for admin resolution of a provision request (PUT request body).
 * Same structure as create, but right values should be APPROVED or REJECTED.
 *
 * @param provisionedZones the provisioned zones with resolved rights (APPROVED or REJECTED)
 */
public record ProvisionRequestResolveDTO(
    ProvisionedZonesDTO provisionedZones
) {
}
