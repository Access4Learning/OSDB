// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.EnvironmentUpdateRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentResponse;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.service.EnvironmentService;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Preservation Property Test for Environment Update operations.
 *
 * <p><strong>IMPORTANT: These tests encode current CORRECT behavior that must survive the fix.</strong></p>
 *
 * <p>Property: For any environment update where fingerprint is null OR matches current fingerprint,
 * update succeeds and other mutable fields are applied.</p>
 *
 * <p>These tests MUST PASS on both unfixed and fixed code, confirming that the fix
 * does not regress legitimate update operations.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2</strong></p>
 */
class EnvironmentUpdatePreservationTest {

    /**
     * Property: When fingerprint is null (not provided), update succeeds and
     * other mutable fields (consumerName, instanceId, userToken) are applied.
     */
    @Property(tries = 20)
    @Label("updateEnvironment SHALL succeed when fingerprint is null and apply mutable fields")
    void updateShallSucceedWhenFingerprintIsNull(
            @ForAll("consumerNames") String consumerName,
            @ForAll("instanceIds") String instanceId,
            @ForAll("userTokens") String userToken) {

        // Set up mocks
        EnvironmentRepository repository = mock(EnvironmentRepository.class);
        EnvironmentService service = createServiceWithMockedDeps(repository);

        UUID environmentId = UUID.randomUUID();

        // Create a real-ish environment entity with an existing fingerprint
        Environment existingEnv = createMockEnvironment(environmentId, "existing-fingerprint");
        when(repository.findByIdWithAclEntries(environmentId)).thenReturn(Optional.of(existingEnv));
        when(repository.save(any(Environment.class))).thenAnswer(inv -> inv.getArgument(0));

        // Create update request with null fingerprint and other mutable fields
        EnvironmentUpdateRequest request = new EnvironmentUpdateRequest(
                consumerName, null, null, instanceId, userToken, null);

        // Act - should succeed without exception
        SifEnvironmentResponse response = service.updateEnvironment(environmentId, request);

        // Assert - verify fields were set on the entity
        verify(existingEnv).setConsumerName(consumerName);
        verify(existingEnv).setInstanceId(instanceId);
        verify(existingEnv).setUserToken(userToken);
        verify(existingEnv, never()).setFingerprint(any());
        verify(repository).save(existingEnv);
    }

    /**
     * Property: When fingerprint matches current value (no-op re-send), update succeeds.
     */
    @Property(tries = 20)
    @Label("updateEnvironment SHALL succeed when fingerprint matches current value")
    void updateShallSucceedWhenFingerprintMatchesCurrent(
            @ForAll("existingFingerprints") String currentFingerprint,
            @ForAll("consumerNames") String consumerName) {

        // Set up mocks
        EnvironmentRepository repository = mock(EnvironmentRepository.class);
        EnvironmentService service = createServiceWithMockedDeps(repository);

        UUID environmentId = UUID.randomUUID();

        Environment existingEnv = createMockEnvironment(environmentId, currentFingerprint);
        when(repository.findByIdWithAclEntries(environmentId)).thenReturn(Optional.of(existingEnv));
        when(repository.save(any(Environment.class))).thenAnswer(inv -> inv.getArgument(0));

        // Create update request with fingerprint == current fingerprint (no-op re-send)
        EnvironmentUpdateRequest request = new EnvironmentUpdateRequest(
                consumerName, null, currentFingerprint, null, null, null);

        // Act - should succeed without exception
        SifEnvironmentResponse response = service.updateEnvironment(environmentId, request);

        // Assert - update completed successfully
        verify(existingEnv).setConsumerName(consumerName);
        verify(repository).save(existingEnv);
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> consumerNames() {
        return Arbitraries.of(
                "Student-SIS-App",
                "LMS-Integration-v2",
                "AssessmentConnector",
                "HR-System-Prod",
                "TestApp-Dev"
        );
    }

    @Provide
    Arbitrary<String> instanceIds() {
        return Arbitraries.of(
                "instance-001",
                "prod-west-2",
                "dev-local",
                "staging-east-1",
                "test-node-3"
        );
    }

    @Provide
    Arbitrary<String> userTokens() {
        return Arbitraries.of(
                "user-token-abc123",
                "tok_9f8e7d6c",
                "bearer-xyz-456",
                "session-token-001",
                "auth-key-prod"
        );
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

    // ========== Helpers ==========

    private Environment createMockEnvironment(UUID id, String fingerprint) {
        Environment env = mock(Environment.class);
        when(env.getId()).thenReturn(id);
        when(env.getFingerprint()).thenReturn(fingerprint);
        when(env.getSolutionUuid()).thenReturn(UUID.randomUUID());
        return env;
    }

    private EnvironmentService createServiceWithMockedDeps(EnvironmentRepository repository) {
        try {
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
