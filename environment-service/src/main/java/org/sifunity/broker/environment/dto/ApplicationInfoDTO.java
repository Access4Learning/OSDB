// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing the SIF 3.7 ApplicationInfo nested object.
 *
 * @param applicationKey                  the application key
 * @param supportedInfrastructureVersion  the supported SIF infrastructure version
 * @param dataModelNamespace              the data model namespace
 * @param transport                       the transport protocol
 * @param applicationProduct              the application product identity
 * @param adapterProduct                  the adapter product identity
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicationInfoDTO(
    String applicationKey,
    String supportedInfrastructureVersion,
    String dataModelNamespace,
    String transport,
    ProductIdentityDTO applicationProduct,
    ProductIdentityDTO adapterProduct
) {
}
