// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

/**
 * DTO representing a single access right in a SIF 3.7 provisioned service.
 *
 * @param type  the access type (e.g., QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE)
 * @param value the access level (e.g., APPROVED, SUPPORTED, REJECTED, UNSUPPORTED, REQUESTED)
 */
public record RightDTO(
    String type,
    String value
) {
}
