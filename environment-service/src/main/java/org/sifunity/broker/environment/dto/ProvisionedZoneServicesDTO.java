// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.util.List;

/**
 * DTO wrapping the list of provisioned services within a zone.
 *
 * @param service the list of provisioned services
 */
public record ProvisionedZoneServicesDTO(
    List<ProvisionedServiceDTO> service
) {
}
