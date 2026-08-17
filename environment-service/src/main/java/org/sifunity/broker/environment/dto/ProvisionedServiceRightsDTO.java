// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.util.List;

/**
 * DTO wrapping the list of rights for a provisioned service.
 *
 * @param right the list of access rights
 */
public record ProvisionedServiceRightsDTO(
    List<RightDTO> right
) {
}
