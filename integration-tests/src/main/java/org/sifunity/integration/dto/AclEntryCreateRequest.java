// SPDX-License-Identifier: MIT
package org.sifunity.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;

/**
 * DTO for ACL entry creation requests (test data generation).
 *
 * <p>Uses SIF-standard {@code zoneId} and {@code contextId} string identifiers
 * instead of internal UUIDs. The server resolves these to internal context references.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AclEntryCreateRequest {
    private String environmentId;
    private String serviceName;
    private String zoneId;
    private String contextId;
    private AccessType accessType;
    private AccessLevel accessLevel;
}
