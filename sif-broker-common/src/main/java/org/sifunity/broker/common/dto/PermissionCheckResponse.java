// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

/**
 * Response DTO for internal permission check API.
 * Contains the result of ACL permission validation.
 * 
 * @param granted Whether the permission is granted (true) or denied (false)
 */
public record PermissionCheckResponse(
    boolean granted
) {}
