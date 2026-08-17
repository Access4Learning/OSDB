// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a single provisioned service within a zone.
 *
 * @param type      the service type (e.g., "OBJECT")
 * @param name      the service name
 * @param contextId the optional context identifier
 * @param rights    the access rights for this service
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProvisionedServiceDTO(
    String type,
    String name,
    String contextId,
    ProvisionedServiceRightsDTO rights
) {
}
