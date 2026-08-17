// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * DTO representing a single provisioned zone in the environment response.
 *
 * @param id       the zone identifier
 * @param services the provisioned services within this zone
 */
public record ProvisionedZoneDTO(
    String id,
    ProvisionedZoneServicesDTO services
) {
}
