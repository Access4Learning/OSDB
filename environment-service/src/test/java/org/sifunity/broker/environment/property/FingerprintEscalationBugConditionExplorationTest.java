// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.exception.InvalidParameterException;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.EnvironmentUpdateRequest;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.service.EnvironmentService;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Bug Condition Exploration Test for Fingerprint Privilege Escalation.
 *
 * <p><strong>CRITICAL: These tests MUST FAIL on unfixed code — failure confirms the bug exists.</strong></p>
 *
 * <p>Bug: {@code EnvironmentService.updateEnvironment()} at line ~290 accepts any fingerprint value
 * without validation. Combined with {@code AuthorizationService.isAdmin()} checking for
 * fingerprint == "broker-services", any authenticated environment can escalate to solution-admin.</p>
 *
 * <p>Bug Condition: For any environment update where fingerprint is set to "broker-services",
 * the system SHALL reject with HTTP 400.</p>
 *
 * <p>On UNFIXED code: test FAILS because updateEnvironment blindly sets any fingerprint value.</p>
 * <p>On FIXED code: test PASSES because updateEnvironment rejects fingerprint changes.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2</strong></p>
 */
class FingerprintEscalationBugConditionExplorationTest {

    /**
     * Property: For any environment update where fingerprint is set to "broker-services",
     * updateEnvironment SHALL throw InvalidParameterException.
     *
     * <p>On UNFIXED code: the update succeeds (no exception thrown) — test FAILS.</p>
     * <p>On FIXED code: InvalidParameterException is thrown — test PASSES.</p>
     */
    @Property(tries = 20)
    @Label("updateEnvironment SHALL reject fingerprint='broker-services' with InvalidParameterException")
    void updateEnvironmentShallRejectBrokerServicesFingerprint(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("existingFingerprints") String existingFingerprint) {

        // Set up mocks
        EnvironmentRepository repository = mock(EnvironmentRepository.class);
        EnvironmentService service = createServiceWithMockedDeps(repository);

        // Create an existing environment entity with a non-admin fingerprint
        Environment existingEnv = mock(Environment.class);
        when(existingEnv.getFingerprint()).thenReturn(existingFingerprint);
        when(existingEnv.getId()).thenReturn(environmentId);
        when(repository.findByIdWithAclEntries(environmentId)).thenReturn(Optional.of(existingEnv));

        // Create update request with the escalation payload
        EnvironmentUpdateRequest request = new EnvironmentUpdateRequest(
                null, null, "broker-services", null, null, null);

        // Assert that updating fingerprint to "broker-services" throws InvalidParameterException
        assertThatThrownBy(() -> service.updateEnvironment(environmentId, request))
                .as("updateEnvironment with fingerprint='broker-services' should throw InvalidParameterException " +
                    "but on UNFIXED code it accepts the update, enabling privilege escalation")
                .isInstanceOf(InvalidParameterException.class);
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<String> existingFingerprints() {
        return Arbitraries.of(
                "env-fingerprint-abc",
                "my-app-2024",
                "test-environment-fp",
                "student-sis-connector",
                "lms-integration-v3"
        );
    }

    // ========== Helper ==========

    private EnvironmentService createServiceWithMockedDeps(EnvironmentRepository repository) {
        try {
            // Use reflection to create EnvironmentService with mocked repository
            var constructor = EnvironmentService.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            var paramTypes = constructor.getParameterTypes();
            Object[] args = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                if (paramTypes[i] == EnvironmentRepository.class) {
                    args[i] = repository;
                } else {
                    args[i] = mock(paramTypes[i]);
                }
            }
            return (EnvironmentService) constructor.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create EnvironmentService with mocked dependencies", e);
        }
    }
}
