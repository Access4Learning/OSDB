// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotNull;
import org.sifunity.broker.common.constant.AccessLevel;

/**
 * Request DTO for updating an existing ACL entry.
 * 
 * <p>This record represents the data required to update an ACL entry's
 * access level. Only the access level can be modified; all other fields
 * (environment, service, zone, context, access type) remain unchanged.</p>
 * 
 * <p>This design ensures that the unique constraint parameters cannot be
 * modified after creation, maintaining data integrity.</p>
 * 
 * @param accessLevel New access level for the ACL entry (APPROVED, REJECTED, or PENDING)
 * 
 * @see AccessLevel
 * @see org.sifunity.broker.common.dto.AclEntryDTO
 * @see AclEntryCreateRequest
 */
public record AclEntryUpdateRequest(
    /**
     * New access level for the ACL entry.
     * APPROVED grants permission, REJECTED denies permission,
     * PENDING indicates the request is awaiting approval.
     * Cannot be null.
     */
    @NotNull(message = "Access level is required")
    AccessLevel accessLevel
) {
}