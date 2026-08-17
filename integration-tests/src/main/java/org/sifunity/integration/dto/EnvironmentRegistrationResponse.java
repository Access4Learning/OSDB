// SPDX-License-Identifier: MIT
package org.sifunity.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for environment registration responses (test data generation).
 */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentRegistrationResponse {
    private String id;  // Changed from environmentId to match actual API
    private String sessionToken;
    private String sharedSecret;
    private String applicationName;  // Changed from applicationKey to match environment-service DTO
    private String solutionId;
    private String userToken;
    private String instanceId;
    private String environmentType;
}
