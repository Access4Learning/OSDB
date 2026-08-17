// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a SIF 3.7 ProductIdentity (applicationProduct or adapterProduct).
 *
 * @param vendorName     the vendor name
 * @param productName    the product name
 * @param productVersion the product version
 * @param iconURI        optional icon URI
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductIdentityDTO(
    String vendorName,
    String productName,
    String productVersion,
    String iconURI
) {
}
