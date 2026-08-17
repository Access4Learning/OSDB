// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.util.List;

/**
 * DTO wrapping the list of provisioned zones in the environment response.
 *
 * @param provisionedZone the list of provisioned zones
 */
public record ProvisionedZonesDTO(
    List<ProvisionedZoneDTO> provisionedZone
) {
}
