// SPDX-License-Identifier: MIT
package org.sifunity.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sifunity.broker.common.constant.AccessLevel;

/**
 * DTO for ACL entry update requests (test data generation).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AclEntryUpdateRequest {
    private AccessLevel accessLevel;  // Use enum
}
