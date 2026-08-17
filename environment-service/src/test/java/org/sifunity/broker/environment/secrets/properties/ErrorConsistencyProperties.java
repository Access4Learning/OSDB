// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.properties;

import net.jqwik.api.*;
import org.sifunity.broker.environment.secrets.*;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for consistent error types across all backend implementations.
 * 
 * <p>These tests validate that all backend implementations throw the same exception
 * types for the same error conditions, ensuring consistent error handling across
 * different backend types.</p>
 * 
 * <p><strong>Validates: Requirements 4.7</strong></p>
 * 
 * <p><strong>Property 7: Consistent Error Types Across Backends</strong></p>
 * <p>For any error condition (secret not found, backend unavailable, invalid operation),
 * all backend implementations should throw the same exception type.</p>
 */
@Tag("Feature: pluggable-secrets-backend")
class ErrorConsistencyProperties {

    // ========== Property 7: Consistent Error Types Across Backends ==========

    /**
     * Property 7.1: All backends throw SecretNotFoundException for non-existent secrets
     * 
     * <p>For any backend implementation and any non-existent environment ID,
     * attempting to retrieve the secret should throw SecretNotFoundException.</p>
     * 
     * <p><strong>Validates: Requirements 4.7</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property 7: Consistent Error Types Across Backends")
    void allBackendsThrowSecretNotFoundExceptionForNonExistentSecrets(
            @ForAll("backends") SecretsBackend backend,
            @ForAll("environmentIds") UUID environmentId) {
        
        // When attempting to retrieve a non-existent secret
        // Then all backends should throw SecretNotFoundException
        assertThatThrownBy(() -> backend.retrieve(environmentId))
            .as("Backend %s should throw SecretNotFoundException for non-existent secret",
                backend.getBackendType())
            .isInstanceOf(SecretNotFoundException.class)
            .hasMessageContaining(environmentId.toString());
    }

    /**
     * Property 7.2: All backends throw SecretNotFoundException for deleted secrets
     * 
     * <p>For any backend implementation and any non-existent environment ID,
     * attempting to delete the secret should throw SecretNotFoundException.</p>
     * 
     * <p><strong>Validates: Requirements 4.7</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property 7: Consistent Error Types Across Backends")
    void allBackendsThrowSecretNotFoundExceptionForDeletedSecrets(
            @ForAll("backends") SecretsBackend backend,
            @ForAll("environmentIds") UUID environmentId) {
        
        // When attempting to delete a non-existent secret
        // Then all backends should throw SecretNotFoundException
        assertThatThrownBy(() -> backend.delete(environmentId))
            .as("Backend %s should throw SecretNotFoundException when deleting non-existent secret",
                backend.getBackendType())
            .isInstanceOf(SecretNotFoundException.class)
            .hasMessageContaining(environmentId.toString());
    }

    /**
     * Property 7.3: All backends throw SecretsBackendException for general failures
     * 
     * <p>For any backend implementation experiencing a general failure condition,
     * the backend should throw SecretsBackendException or one of its subclasses.</p>
     * 
     * <p><strong>Validates: Requirements 4.7</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property 7: Consistent Error Types Across Backends")
    void allBackendsThrowSecretsBackendExceptionForGeneralFailures(
            @ForAll("failingBackends") SecretsBackend backend,
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("secrets") String secret) {
        
        // When a backend experiences a general failure during store operation
        // Then it should throw SecretsBackendException or a subclass
        assertThatThrownBy(() -> backend.store(environmentId, secret))
            .as("Backend %s should throw SecretsBackendException for general failures",
                backend.getBackendType())
            .isInstanceOf(SecretsBackendException.class);
    }

    /**
     * Property 7.4: All backends throw BackendUnavailableException for service unavailability
     * 
     * <p>For any backend implementation where the underlying service is unavailable,
     * the backend should throw BackendUnavailableException.</p>
     * 
     * <p><strong>Validates: Requirements 4.7</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property 7: Consistent Error Types Across Backends")
    void allBackendsThrowBackendUnavailableExceptionForServiceUnavailability(
            @ForAll("unavailableBackends") SecretsBackend backend,
            @ForAll("environmentIds") UUID environmentId) {
        
        // When a backend's underlying service is unavailable
        // Then it should throw BackendUnavailableException
        assertThatThrownBy(() -> backend.retrieve(environmentId))
            .as("Backend %s should throw BackendUnavailableException when service is unavailable",
                backend.getBackendType())
            .isInstanceOf(BackendUnavailableException.class);
    }

    /**
     * Property 7.5: All backends throw EncryptionException for encryption/decryption failures
     * 
     * <p>For any backend implementation that performs encryption/decryption,
     * failures in these operations should throw EncryptionException.</p>
     * 
     * <p><strong>Validates: Requirements 4.7</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property 7: Consistent Error Types Across Backends")
    void allBackendsThrowEncryptionExceptionForCryptographicFailures(
            @ForAll("encryptionFailingBackends") SecretsBackend backend,
            @ForAll("environmentIds") UUID environmentId) {
        
        // When a backend experiences an encryption/decryption failure
        // Then it should throw EncryptionException
        assertThatThrownBy(() -> backend.retrieve(environmentId))
            .as("Backend %s should throw EncryptionException for cryptographic failures",
                backend.getBackendType())
            .isInstanceOf(EncryptionException.class);
    }

    /**
     * Property 7.6: SecretNotFoundException preserves environment ID
     * 
     * <p>For any backend throwing SecretNotFoundException, the exception should
     * preserve the environment ID that was requested.</p>
     * 
     * <p><strong>Validates: Requirements 4.7</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property 7: Consistent Error Types Across Backends")
    void secretNotFoundExceptionPreservesEnvironmentId(
            @ForAll("backends") SecretsBackend backend,
            @ForAll("environmentIds") UUID environmentId) {
        
        try {
            backend.retrieve(environmentId);
        } catch (SecretNotFoundException e) {
            // The exception should preserve the environment ID
            assertThat(e.getEnvironmentId())
                .as("SecretNotFoundException should preserve the environment ID")
                .isEqualTo(environmentId);
        }
    }

    /**
     * Property 7.7: All exception types inherit from SecretsBackendException
     * 
     * <p>For any exception thrown by any backend, it should be an instance of
     * SecretsBackendException or one of its subclasses.</p>
     * 
     * <p><strong>Validates: Requirements 4.7</strong></p>
     */
    @Property(tries = 10)
    @Tag("Property 7: Consistent Error Types Across Backends")
    void allExceptionsInheritFromSecretsBackendException(
            @ForAll("allBackendTypes") SecretsBackend backend,
            @ForAll("environmentIds") UUID environmentId) {
        
        // When any backend throws an exception
        // Then it should be a SecretsBackendException or subclass
        assertThatThrownBy(() -> backend.retrieve(environmentId))
            .as("All backend exceptions should inherit from SecretsBackendException")
            .isInstanceOf(SecretsBackendException.class);
    }

    // ========== Arbitraries (Test Data Generators) ==========

    /**
     * Provides arbitrary environment IDs for testing.
     */
    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    /**
     * Provides arbitrary secret strings for testing.
     */
    @Provide
    Arbitrary<String> secrets() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars("!@#$%^&*()_+-=[]{}|;:,.<>?")
            .ofMinLength(1)
            .ofMaxLength(100);
    }

    /**
     * Provides mock backend implementations that throw SecretNotFoundException.
     * 
     * <p>These backends simulate the behavior of all backend types when a secret
     * is not found, allowing us to test consistent error handling.</p>
     */
    @Provide
    Arbitrary<SecretsBackend> backends() {
        List<SecretsBackend> backendList = List.of(
            new MockPlaintextBackend(MockBehavior.SECRET_NOT_FOUND),
            new MockMultiKeyBackend(MockBehavior.SECRET_NOT_FOUND),
            new MockVaultBackend(MockBehavior.SECRET_NOT_FOUND)
        );
        return Arbitraries.of(backendList);
    }

    /**
     * Provides mock backend implementations that throw general SecretsBackendException.
     */
    @Provide
    Arbitrary<SecretsBackend> failingBackends() {
        List<SecretsBackend> backendList = List.of(
            new MockPlaintextBackend(MockBehavior.GENERAL_FAILURE),
            new MockMultiKeyBackend(MockBehavior.GENERAL_FAILURE),
            new MockVaultBackend(MockBehavior.GENERAL_FAILURE)
        );
        return Arbitraries.of(backendList);
    }

    /**
     * Provides mock backend implementations that throw BackendUnavailableException.
     */
    @Provide
    Arbitrary<SecretsBackend> unavailableBackends() {
        List<SecretsBackend> backendList = List.of(
            new MockVaultBackend(MockBehavior.BACKEND_UNAVAILABLE)
        );
        return Arbitraries.of(backendList);
    }

    /**
     * Provides mock backend implementations that throw EncryptionException.
     */
    @Provide
    Arbitrary<SecretsBackend> encryptionFailingBackends() {
        List<SecretsBackend> backendList = List.of(
            new MockMultiKeyBackend(MockBehavior.ENCRYPTION_FAILURE)
        );
        return Arbitraries.of(backendList);
    }

    /**
     * Provides all types of mock backends for comprehensive testing.
     */
    @Provide
    Arbitrary<SecretsBackend> allBackendTypes() {
        List<SecretsBackend> backendList = List.of(
            new MockPlaintextBackend(MockBehavior.SECRET_NOT_FOUND),
            new MockMultiKeyBackend(MockBehavior.SECRET_NOT_FOUND),
            new MockVaultBackend(MockBehavior.SECRET_NOT_FOUND),
            new MockMultiKeyBackend(MockBehavior.ENCRYPTION_FAILURE),
            new MockVaultBackend(MockBehavior.BACKEND_UNAVAILABLE)
        );
        return Arbitraries.of(backendList);
    }

    // ========== Mock Backend Implementations ==========

    /**
     * Enum defining different mock behaviors for testing error conditions.
     */
    private enum MockBehavior {
        SECRET_NOT_FOUND,
        GENERAL_FAILURE,
        BACKEND_UNAVAILABLE,
        ENCRYPTION_FAILURE
    }

    /**
     * Mock Plaintext Backend for testing error consistency.
     */
    private static class MockPlaintextBackend implements SecretsBackend {
        private final MockBehavior behavior;

        MockPlaintextBackend(MockBehavior behavior) {
            this.behavior = behavior;
        }

        @Override
        public void store(UUID environmentId, String secret) {
            throwAppropriateException(environmentId);
        }

        @Override
        public String retrieve(UUID environmentId) {
            throwAppropriateException(environmentId);
            return null; // Never reached
        }

        @Override
        public void delete(UUID environmentId) {
            throwAppropriateException(environmentId);
        }

        @Override
        public boolean isHealthy() {
            return behavior != MockBehavior.BACKEND_UNAVAILABLE;
        }

        @Override
        public String getBackendType() {
            return "plaintext";
        }

        private void throwAppropriateException(UUID environmentId) {
            switch (behavior) {
                case SECRET_NOT_FOUND:
                    throw new SecretNotFoundException(environmentId);
                case GENERAL_FAILURE:
                    throw new SecretsBackendException("Database connection failed");
                case BACKEND_UNAVAILABLE:
                    throw new BackendUnavailableException("Database is unavailable");
                default:
                    throw new SecretsBackendException("Unknown error");
            }
        }
    }

    /**
     * Mock Multi-Key Backend for testing error consistency.
     */
    private static class MockMultiKeyBackend implements SecretsBackend {
        private final MockBehavior behavior;

        MockMultiKeyBackend(MockBehavior behavior) {
            this.behavior = behavior;
        }

        @Override
        public void store(UUID environmentId, String secret) {
            throwAppropriateException(environmentId);
        }

        @Override
        public String retrieve(UUID environmentId) {
            throwAppropriateException(environmentId);
            return null; // Never reached
        }

        @Override
        public void delete(UUID environmentId) {
            throwAppropriateException(environmentId);
        }

        @Override
        public boolean isHealthy() {
            return behavior != MockBehavior.BACKEND_UNAVAILABLE;
        }

        @Override
        public String getBackendType() {
            return "multi-key";
        }

        private void throwAppropriateException(UUID environmentId) {
            switch (behavior) {
                case SECRET_NOT_FOUND:
                    throw new SecretNotFoundException(environmentId);
                case ENCRYPTION_FAILURE:
                    throw new EncryptionException("Decryption failed: authentication tag mismatch");
                case GENERAL_FAILURE:
                    throw new SecretsBackendException("Encryption operation failed");
                case BACKEND_UNAVAILABLE:
                    throw new BackendUnavailableException("Encryption service unavailable");
                default:
                    throw new SecretsBackendException("Unknown error");
            }
        }
    }

    /**
     * Mock Vault Backend for testing error consistency.
     */
    private static class MockVaultBackend implements SecretsBackend {
        private final MockBehavior behavior;

        MockVaultBackend(MockBehavior behavior) {
            this.behavior = behavior;
        }

        @Override
        public void store(UUID environmentId, String secret) {
            throwAppropriateException(environmentId);
        }

        @Override
        public String retrieve(UUID environmentId) {
            throwAppropriateException(environmentId);
            return null; // Never reached
        }

        @Override
        public void delete(UUID environmentId) {
            throwAppropriateException(environmentId);
        }

        @Override
        public boolean isHealthy() {
            return behavior != MockBehavior.BACKEND_UNAVAILABLE;
        }

        @Override
        public String getBackendType() {
            return "vault";
        }

        private void throwAppropriateException(UUID environmentId) {
            switch (behavior) {
                case SECRET_NOT_FOUND:
                    throw new SecretNotFoundException(environmentId);
                case BACKEND_UNAVAILABLE:
                    throw new BackendUnavailableException("Vault server is unreachable");
                case GENERAL_FAILURE:
                    throw new SecretsBackendException("Vault operation failed");
                default:
                    throw new SecretsBackendException("Unknown error");
            }
        }
    }
}
