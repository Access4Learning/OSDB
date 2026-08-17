// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.util.List;

/**
 * DTO wrapping the list of infrastructure service entries.
 *
 * @param infrastructureService the list of infrastructure service entries
 */
public record InfrastructureServicesDTO(
    List<InfrastructureServiceDTO> infrastructureService
) {
}
