// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.EnvironmentType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TokenValidationResponse.
 * 
 * Tests the TokenValidationResponse record structure and behavior.
 */
@DisplayName("TokenValidationResponse Tests")
class TokenValidationResponseTest {

    @Test
    @DisplayName("Should create TokenValidationResponse with all fields")
    void shouldCreateTokenValidationResponseWithAllFields() {
        UUID environmentId = UUID.randomUUID();
        String applicationName = "test-app";
        EnvironmentType type = EnvironmentType.BROKERED;
        String defaultZoneId = "test-zone";
        
        TokenValidationResponse response = new TokenValidationResponse(
            environmentId, applicationName, type, defaultZoneId, null, null, null, null
        );
        
        assertThat(response.environmentId()).isEqualTo(environmentId);
        assertThat(response.applicationName()).isEqualTo(applicationName);
        assertThat(response.type()).isEqualTo(type);
        assertThat(response.defaultZoneId()).isEqualTo(defaultZoneId);
    }

    @Test
    @DisplayName("Should create TokenValidationResponse with fingerprint and domain/solution")
    void shouldCreateTokenValidationResponseWithFingerprintAndDomainSolution() {
        UUID environmentId = UUID.randomUUID();
        String applicationName = "test-app";
        EnvironmentType type = EnvironmentType.BROKERED;
        String defaultZoneId = "test-zone";
        String fingerprint = "fp-123";
        String domainId = "iowa";
        String solutionId = "production";
        UUID solutionUuid = UUID.randomUUID();
        
        TokenValidationResponse response = new TokenValidationResponse(
            environmentId, applicationName, type, defaultZoneId, fingerprint, domainId, solutionId, solutionUuid
        );
        
        assertThat(response.environmentId()).isEqualTo(environmentId);
        assertThat(response.applicationName()).isEqualTo(applicationName);
        assertThat(response.type()).isEqualTo(type);
        assertThat(response.defaultZoneId()).isEqualTo(defaultZoneId);
        assertThat(response.fingerprint()).isEqualTo(fingerprint);
        assertThat(response.domainId()).isEqualTo(domainId);
        assertThat(response.solutionId()).isEqualTo(solutionId);
        assertThat(response.solutionUuid()).isEqualTo(solutionUuid);
    }

    @Test
    @DisplayName("Should support all environment types")
    void shouldSupportAllEnvironmentTypes() {
        UUID environmentId = UUID.randomUUID();
        String applicationName = "test-app";
        String defaultZoneId = "test-zone";
        
        for (EnvironmentType type : EnvironmentType.values()) {
            TokenValidationResponse response = new TokenValidationResponse(
                environmentId, applicationName, type, defaultZoneId, null, null, null, null
            );
            assertThat(response.type()).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("Should support equality comparison")
    void shouldSupportEqualityComparison() {
        UUID environmentId = UUID.randomUUID();
        String applicationName = "test-app";
        EnvironmentType type = EnvironmentType.BROKERED;
        String defaultZoneId = "test-zone";
        
        TokenValidationResponse response1 = new TokenValidationResponse(
            environmentId, applicationName, type, defaultZoneId, null, null, null, null
        );
        TokenValidationResponse response2 = new TokenValidationResponse(
            environmentId, applicationName, type, defaultZoneId, null, null, null, null
        );
        TokenValidationResponse response3 = new TokenValidationResponse(
            UUID.randomUUID(), applicationName, type, defaultZoneId, null, null, null, null
        );
        
        assertThat(response1).isEqualTo(response2);
        assertThat(response1).isNotEqualTo(response3);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void shouldHaveMeaningfulToStringRepresentation() {
        UUID environmentId = UUID.randomUUID();
        String applicationName = "test-app";
        EnvironmentType type = EnvironmentType.BROKERED;
        String defaultZoneId = "test-zone";
        
        TokenValidationResponse response = new TokenValidationResponse(
            environmentId, applicationName, type, defaultZoneId, null, null, null, null
        );
        
        String toString = response.toString();
        
        assertThat(toString).contains("TokenValidationResponse");
        assertThat(toString).contains(environmentId.toString());
        assertThat(toString).contains(applicationName);
        assertThat(toString).contains(type.toString());
        assertThat(toString).contains(defaultZoneId);
    }
}
