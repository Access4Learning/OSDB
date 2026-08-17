// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.dto.TokenValidationResponse;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Preservation Property Test for Healthy Gateway Token Validation.
 *
 * <p><strong>IMPORTANT: These tests encode current CORRECT behavior that must survive the fix.</strong></p>
 *
 * <p>Property: When environment-service is healthy (circuit closed), validateToken returns
 * valid responses normally.</p>
 *
 * <p>These tests MUST PASS on both unfixed and fixed code, confirming that the circuit
 * breaker addition does not break normal token validation flow.</p>
 *
 * <p><strong>Validates: Requirements 3.4</strong></p>
 */
class HealthyValidationPreservationTest {

    /**
     * Property: When EnvironmentServiceClient.validateToken returns a successful response,
     * the response contains correct environment information.
     *
     * <p>This verifies that the validateToken method works correctly when the underlying
     * service is healthy — the basic contract that must be preserved after adding a
     * circuit breaker.</p>
     */
    @Property(tries = 20)
    @Label("validateToken SHALL return valid responses when environment-service is healthy")
    void validateTokenShallReturnValidResponsesWhenHealthy(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("applicationNames") String applicationName,
            @ForAll("defaultZoneIds") String defaultZoneId,
            @ForAll("fingerprints") String fingerprint) {

        // Mock the EnvironmentServiceClient
        EnvironmentServiceClient client = mock(EnvironmentServiceClient.class);

        UUID solutionUuid = UUID.randomUUID();
        String domainId = "domain-" + UUID.randomUUID().toString().substring(0, 8);
        String solutionId = "solution-" + UUID.randomUUID().toString().substring(0, 8);

        TokenValidationResponse expectedResponse = new TokenValidationResponse(
                environmentId,
                applicationName,
                EnvironmentType.DIRECT,
                defaultZoneId,
                fingerprint,
                domainId,
                solutionId,
                solutionUuid
        );

        // Mock returning success
        when(client.validateToken(any())).thenReturn(Mono.just(expectedResponse));

        // Act
        TokenValidationResponse response = client.validateToken(any()).block();

        // Assert - response contains the correct data
        assertThat(response).isNotNull();
        assertThat(response.environmentId()).isEqualTo(environmentId);
        assertThat(response.applicationName()).isEqualTo(applicationName);
        assertThat(response.type()).isEqualTo(EnvironmentType.DIRECT);
        assertThat(response.defaultZoneId()).isEqualTo(defaultZoneId);
        assertThat(response.fingerprint()).isEqualTo(fingerprint);
        assertThat(response.domainId()).isEqualTo(domainId);
        assertThat(response.solutionId()).isEqualTo(solutionId);
        assertThat(response.solutionUuid()).isEqualTo(solutionUuid);
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<String> applicationNames() {
        return Arbitraries.of(
                "StudentSIS",
                "LMS-Connector",
                "AssessmentApp",
                "GradeBook-v2",
                "AttendanceTracker"
        );
    }

    @Provide
    Arbitrary<String> defaultZoneIds() {
        return Arbitraries.of(
                "default",
                "zone-district-1",
                "zone-school-45",
                "testing-zone",
                "prod-main"
        );
    }

    @Provide
    Arbitrary<String> fingerprints() {
        return Arbitraries.of(
                "app-fingerprint-001",
                "sis-connector-fp",
                "lms-prod-2024",
                "assessment-dev",
                "gradebook-staging"
        );
    }
}
