// SPDX-License-Identifier: MIT
package org.sifunity.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for ACL creation requests (test data generation).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AclRequest {
    private String environmentId;
    private String serviceName;
    private String accessType;
    private String accessLevel;
    private String zoneId;
    private String contextId;
}
