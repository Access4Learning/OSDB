// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * DTO for provision request responses.
 * The completionStatus is null (omitted from JSON) until the request is resolved.
 *
 * @param id               the provision request ID
 * @param completionStatus the aggregate resolution status (ACCEPTED, MIXED, REJECTED), null if unresolved
 * @param provisionedZones the provisioned zones with rights assertions/resolutions
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProvisionRequestResponseDTO(
    UUID id,
    String completionStatus,
    ProvisionedZonesDTO provisionedZones
) {
}
