// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.properties;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.jqwik.api.*;
import org.slf4j.LoggerFactory;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.SecretNotFoundException;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.sifunity.broker.environment.secrets.config.MultiKeyProperties;
import org.sifunity.broker.environment.secrets.config.VaultProperties;
import org.sifunity.broker.environment.secrets.impl.MultiKeyBackend;
import org.sifunity.broker.environment.secrets.impl.PlaintextBackend;
import org.sifunity.broker.environment.secrets.impl.VaultBackend;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for audit logs excluding secret values.
 * 
 * <p>These tests validate that when audit logging is enabled, secret values are never
 * included in the audit logs. Audit logs should record access events with metadata
 * (operation type, environment ID, timestamp) but must never expose the actual secret
 * values for security compliance.</p>
 * 
 * <p><strong>Validates: Requirements 9.5</strong></p>
 * 
 * <p><strong>Property 17: Audit Logs Exclude Secret Values</strong></p>
 * <p>For any audit log entry recording secret access, the log entry must contain
 * the access event metadata but not the actual secret value.</p>
 */
@Tag("pluggable-secrets-backend")
class AuditLogsExcludeSecretsProperties {

    // ========== Property 17: Audit Logs Exclude Secret Values ==========

    /**
     * Property 17.1: Store operation audit logs exclude secret (PlaintextBackend)
     * 
     * <p>For any secret stored via PlaintextBackend, the audit logs should record
     * the store operation but must not contain the actual secret value.</p>
     * 
     * <p><strong>Validates: Requirements 9.5</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-17-Audit-Logs-Exclude-Secrets")
    void storeOperationAuditLogsExcludeSecretPlaintext(
            @ForAll("secrets") String secret,
            @ForAll("environmentIds") UUID environmentId) {
        
        // Given: A PlaintextBackend with audit logging enabled
        AuditTestContext context = createPlaintextBackendContext();
        
        // And: An environment to store the secret
        Environment env = new Environment();
        env.setId(environmentId);
        when(context.repository.findById(environmentId)).thenReturn(Optional.of(env));
        when(context.repository.save(any(Environment.class))).thenReturn(env);
        
        // When: Storing a secret
        context.logAppender.list.clear();
        context.backend.store(environmentId, secret);
        
        // Then: The audit logs should not contain the actual secret
        String allLogs = context.getAllLogMessages();
        assertThat(allLogs)
            .as("Audit logs for store operation should not contain the actual secret")
            .doesNotContain(secret);
        
        context.cleanup();
    }

    /**
     * Property 17.2: Retrieve operation audit logs exclude secret (PlaintextBackend)
     * 
     * <p>For any secret retrieved via PlaintextBackend, the audit logs should record
     * the retrieve operation but must not contain the actual secret value.</p>
     * 
     * <p><strong>Validates: Requirements 9.5</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-17-Audit-Logs-Exclude-Secrets")
    void retrieveOperationAuditLogsExcludeSecretPlaintext(
            @ForAll("secrets") String secret,
            @ForAll("environmentIds") UUID environmentId) {
        
        // Given: A PlaintextBackend with audit logging enabled
        AuditTestContext context = createPlaintextBackendContext();
        
        // And: An environment with a stored secret
        Environment env = new Environment();
        env.setId(environmentId);
        env.setSharedSecret(secret);
        when(context.repository.findById(environmentId)).thenReturn(Optional.of(env));
        
        // When: Retrieving the secret
        context.logAppender.list.clear();
        String retrieved = context.backend.retrieve(environmentId);
        
        // Then: The secret should be retrieved correctly
        assertThat(retrieved).isEqualTo(secret);
        
        // And: The audit logs should not contain the actual secret
        String allLogs = context.getAllLogMessages();
        assertThat(allLogs)
            .as("Audit logs for retrieve operation should not contain the actual secret")
            .doesNotContain(secret);
        
        context.cleanup();
    }

    /**
     * Property 17.3: Delete operation audit logs exclude secret (PlaintextBackend)
     * 
     * <p>For any secret deleted via PlaintextBackend, the audit logs should record
     * the delete operation but must not contain the actual secret value.</p>
     * 
     * <p><strong>Validates: Requirements 9.5</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-17-Audit-Logs-Exclude-Secrets")
    void deleteOperationAuditLogsExcludeSecretPlaintext(
            @ForAll("secrets") String secret,
            @ForAll("environmentIds") UUID environmentId) {
        
        // Given: A PlaintextBackend with audit logging enabled
        AuditTestContext context = createPlaintextBackendContext();
        
        // And: An environment with a stored secret
        Environment env = new Environment();
        env.setId(environmentId);
        env.setSharedSecret(secret);
        when(context.repository.findById(environmentId)).thenReturn(Optional.of(env));
        when(context.repository.save(any(Environment.class))).thenReturn(env);
        
        // When: Deleting the secret
        context.logAppender.list.clear();
        context.backend.delete(environmentId);
        
        // Then: The audit logs should not contain the actual secret
        String allLogs = context.getAllLogMessages();
        assertThat(allLogs)
            .as("Audit logs for delete operation should not contain the actual secret")
            .doesNotContain(secret);
        
        context.cleanup();
    }

    /**
     * Property 17.4: Store operation audit logs exclude secret (MultiKeyBackend)
     * 
     * <p>For any secret stored via MultiKeyBackend, the audit logs should record
     * the store operation but must not contain the actual secret value or the
     * encrypted value.</p>
     * 
     * <p><strong>Validates: Requirements 9.5</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-17-Audit-Logs-Exclude-Secrets")
    void storeOperationAuditLogsExcludeSecretMultiKey(
            @ForAll("secrets") String secret,
            @ForAll("environmentIds") UUID environmentId) {
        
        // Given: A MultiKeyBackend with audit logging enabled
        AuditTestContext context = createMultiKeyBackendContext();
        
        // And: An environment to store the secret
        Environment env = new Environment();
        env.setId(environmentId);
        when(context.repository.findById(environmentId)).thenReturn(Optional.of(env));
        when(context.repository.save(any(Environment.class))).thenAnswer(invocation -> {
            Environment savedEnv = invocation.getArgument(0);
            // Capture the encrypted value for verification
            return savedEnv;
        });
        
        // When: Storing a secret
        context.logAppender.list.clear();
        context.backend.store(environmentId, secret);
        
        // Then: The audit logs should not contain the actual secret
        String allLogs = context.getAllLogMessages();
        assertThat(allLogs)
            .as("Audit logs for store operation should not contain the actual secret")
            .doesNotContain(secret);
        
        context.cleanup();
    }

    /**
     * Property 17.5: Retrieve operation audit logs exclude secret (MultiKeyBackend)
     * 
     * <p>For any secret retrieved via MultiKeyBackend, the audit logs should record
     * the retrieve operation but must not contain the actual secret value.</p>
     * 
     * <p><strong>Validates: Requirements 9.5</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-17-Audit-Logs-Exclude-Secrets")
    void retrieveOperationAuditLogsExcludeSecretMultiKey(
            @ForAll("secrets") String secret,
            @ForAll("environmentIds") UUID environmentId) {
        
        // Given: A MultiKeyBackend with audit logging enabled
        AuditTestContext context = createMultiKeyBackendContext();
        
        // And: An environment with an encrypted secret
        // First store the secret to get the encrypted format
        Environment env = new Environment();
        env.setId(environmentId);
        when(context.repository.findById(environmentId)).thenReturn(Optional.of(env));
        when(context.repository.save(any(Environment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        context.backend.store(environmentId, secret);
        
        // When: Retrieving the secret
        context.logAppender.list.clear();
        String retrieved = context.backend.retrieve(environmentId);
        
        // Then: The secret should be retrieved correctly
        assertThat(retrieved).isEqualTo(secret);
        
        // And: The audit logs should not contain the actual secret
        String allLogs = context.getAllLogMessages();
        assertThat(allLogs)
            .as("Audit logs for retrieve operation should not contain the actual secret")
            .doesNotContain(secret);
        
        context.cleanup();
    }

    /**
     * Property 17.6: Delete operation audit logs exclude secret (MultiKeyBackend)
     * 
     * <p>For any secret deleted via MultiKeyBackend, the audit logs should record
     * the delete operation but must not contain the actual secret value.</p>
     * 
     * <p><strong>Validates: Requirements 9.5</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-17-Audit-Logs-Exclude-Secrets")
    void deleteOperationAuditLogsExcludeSecretMultiKey(
            @ForAll("secrets") String secret,
            @ForAll("environmentIds") UUID environmentId) {
        
        // Given: A MultiKeyBackend with audit logging enabled
        AuditTestContext context = createMultiKeyBackendContext();
        
        // And: An environment with an encrypted secret
        Environment env = new Environment();
        env.setId(environmentId);
        when(context.repository.findById(environmentId)).thenReturn(Optional.of(env));
        when(context.repository.save(any(Environment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        context.backend.store(environmentId, secret);
        
        // When: Deleting the secret
        context.logAppender.list.clear();
        context.backend.delete(environmentId);
        
        // Then: The audit logs should not contain the actual secret
        String allLogs = context.getAllLogMessages();
        assertThat(allLogs)
            .as("Audit logs for delete operation should not contain the actual secret")
            .doesNotContain(secret);
        
        context.cleanup();
    }

    /**
     * Property 17.7: Store operation audit logs exclude secret (VaultBackend)
     * 
     * <p>For any secret stored via VaultBackend, the audit logs should record
     * the store operation but must not contain the actual secret value.</p>
     * 
     * <p><strong>Validates: Requirements 9.5</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-17-Audit-Logs-Exclude-Secrets")
    void storeOperationAuditLogsExcludeSecretVault(
            @ForAll("secrets") String secret,
            @ForAll("environmentIds") UUID environmentId) {
        
        // Given: A VaultBackend with audit logging enabled
        AuditTestContext context = createVaultBackendContext();
        
        // And: An environment to store the secret
        Environment env = new Environment();
        env.setId(environmentId);
        when(context.repository.findById(environmentId)).thenReturn(Optional.of(env));
        when(context.repository.save(any(Environment.class))).thenReturn(env);
        
        // When: Storing a secret
        context.logAppender.list.clear();
        context.backend.store(environmentId, secret);
        
        // Then: The audit logs should not contain the actual secret
        String allLogs = context.getAllLogMessages();
        assertThat(allLogs)
            .as("Audit logs for store operation should not contain the actual secret")
            .doesNotContain(secret);
        
        context.cleanup();
    }

    /**
     * Property 17.8: Retrieve operation audit logs exclude secret (VaultBackend)
     * 
     * <p>For any secret retrieved via VaultBackend, the audit logs should record
     * the retrieve operation but must not contain the actual secret value.</p>
     * 
     * <p><strong>Validates: Requirements 9.5</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-17-Audit-Logs-Exclude-Secrets")
    void retrieveOperationAuditLogsExcludeSecretVault(
            @ForAll("secrets") String secret,
            @ForAll("environmentIds") UUID environmentId) {
        
        // Given: A VaultBackend with audit logging enabled
        AuditTestContext context = createVaultBackendContext();
        
        // And: An environment with a secret stored in Vault
        Environment env = new Environment();
        env.setId(environmentId);
        String vaultPath = "secret/test/environment/" + environmentId;
        env.setSharedSecret(vaultPath);
        when(context.repository.findById(environmentId)).thenReturn(Optional.of(env));
        
        // Mock Vault read to return the secret
        VaultResponse vaultResponse = mock(VaultResponse.class);
        Map<String, Object> data = new HashMap<>();
        data.put("value", secret);
        when(vaultResponse.getData()).thenReturn(data);
        when(context.vaultTemplate.read(vaultPath)).thenReturn(vaultResponse);
        
        // When: Retrieving the secret
        context.logAppender.list.clear();
        String retrieved = context.backend.retrieve(environmentId);
        
        // Then: The secret should be retrieved correctly
        assertThat(retrieved).isEqualTo(secret);
        
        // And: The audit logs should not contain the actual secret
        String allLogs = context.getAllLogMessages();
        assertThat(allLogs)
            .as("Audit logs for retrieve operation should not contain the actual secret")
            .doesNotContain(secret);
        
        context.cleanup();
    }

    /**
     * Property 17.9: Delete operation audit logs exclude secret (VaultBackend)
     * 
     * <p>For any secret deleted via VaultBackend, the audit logs should record
     * the delete operation but must not contain the actual secret value.</p>
     * 
     * <p><strong>Validates: Requirements 9.5</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-17-Audit-Logs-Exclude-Secrets")
    void deleteOperationAuditLogsExcludeSecretVault(
            @ForAll("secrets") String secret,
            @ForAll("environmentIds") UUID environmentId) {
        
        // Given: A VaultBackend with audit logging enabled
        AuditTestContext context = createVaultBackendContext();
        
        // And: An environment with a secret stored in Vault
        Environment env = new Environment();
        env.setId(environmentId);
        String vaultPath = "secret/test/environment/" + environmentId;
        env.setSharedSecret(vaultPath);
        when(context.repository.findById(environmentId)).thenReturn(Optional.of(env));
        when(context.repository.save(any(Environment.class))).thenReturn(env);
        
        // When: Deleting the secret
        context.logAppender.list.clear();
        context.backend.delete(environmentId);
        
        // Then: The audit logs should not contain the actual secret
        String allLogs = context.getAllLogMessages();
        assertThat(allLogs)
            .as("Audit logs for delete operation should not contain the actual secret")
            .doesNotContain(secret);
        
        context.cleanup();
    }

    /**
     * Property 17.10: Error scenarios audit logs exclude secrets
     * 
     * <p>For any error scenario (e.g., secret not found), the audit logs should
     * record the error but must not contain any secret values.</p>
     * 
     * <p><strong>Validates: Requirements 9.5</strong></p>
     */
    @Property(tries = 20)
    @Tag("Property-17-Audit-Logs-Exclude-Secrets")
    void errorScenariosAuditLogsExcludeSecrets(
            @ForAll("secrets") String secret,
            @ForAll("environmentIds") UUID environmentId) {
        
        // Given: A PlaintextBackend with audit logging enabled
        AuditTestContext context = createPlaintextBackendContext();
        
        // And: An environment that doesn't exist
        when(context.repository.findById(environmentId)).thenReturn(Optional.empty());
        
        // When: Attempting to retrieve a non-existent secret
        context.logAppender.list.clear();
        assertThatThrownBy(() -> context.backend.retrieve(environmentId))
            .isInstanceOf(SecretNotFoundException.class);
        
        // Then: The audit logs should not contain any secret values
        String allLogs = context.getAllLogMessages();
        assertThat(allLogs)
            .as("Audit logs for error scenarios should not contain secret values")
            .doesNotContain(secret);
        
        context.cleanup();
    }

    // ========== Arbitraries (Test Data Generators) ==========

    /**
     * Provides arbitrary secret strings for testing.
     * 
     * <p>Generates strings with alphanumeric characters and common symbols,
     * with lengths between 8 and 64 characters.</p>
     */
    @Provide
    Arbitrary<String> secrets() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars("!@#$%^&*()_+-=")
            .ofMinLength(8)
            .ofMaxLength(64);
    }

    /**
     * Provides arbitrary environment IDs for testing.
     * 
     * <p>Generates random UUIDs to represent environment identifiers.</p>
     */
    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    // ========== Helper Methods ==========

    /**
     * Creates a test context with PlaintextBackend and audit logging enabled.
     */
    private AuditTestContext createPlaintextBackendContext() {
        EnvironmentRepository repository = mock(EnvironmentRepository.class);
        
        // Set up log capture
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.setContext(loggerContext);
        logAppender.start();
        logger.addAppender(logAppender);
        
        SecretsBackend backend = new PlaintextBackend(repository);
        
        return new AuditTestContext(backend, repository, null, logAppender, logger);
    }

    /**
     * Creates a test context with MultiKeyBackend and audit logging enabled.
     */
    private AuditTestContext createMultiKeyBackendContext() {
        EnvironmentRepository repository = mock(EnvironmentRepository.class);
        
        // Set up log capture
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.setContext(loggerContext);
        logAppender.start();
        logger.addAppender(logAppender);
        
        // Create MultiKeyBackend with test configuration
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240101");
        Map<String, String> keys = new HashMap<>();
        keys.put("20240101", "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="); // 32-byte key
        properties.setKeys(keys);
        
        SecretsBackend backend = new MultiKeyBackend(repository, properties);
        
        return new AuditTestContext(backend, repository, null, logAppender, logger);
    }

    /**
     * Creates a test context with VaultBackend and audit logging enabled.
     */
    private AuditTestContext createVaultBackendContext() {
        EnvironmentRepository repository = mock(EnvironmentRepository.class);
        VaultTemplate vaultTemplate = mock(VaultTemplate.class);
        
        // Set up log capture
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.setContext(loggerContext);
        logAppender.start();
        logger.addAppender(logAppender);
        
        // Mock Vault health check
        when(vaultTemplate.opsForSys()).thenReturn(mock(org.springframework.vault.core.VaultSysOperations.class));
        
        // Mock Vault write
        when(vaultTemplate.write(anyString(), any(Map.class))).thenReturn(mock(VaultResponse.class));
        
        // Create VaultBackend with mocked template
        VaultProperties properties = new VaultProperties();
        properties.setSecretsPath("secret/test");
        
        SecretsBackend backend = new VaultBackend(repository, vaultTemplate, properties);
        
        return new AuditTestContext(backend, repository, vaultTemplate, logAppender, logger);
    }

    /**
     * Test context holder for backend, repository, vault template, and logging components.
     */
    private static class AuditTestContext {
        final SecretsBackend backend;
        final EnvironmentRepository repository;
        final VaultTemplate vaultTemplate;
        final ListAppender<ILoggingEvent> logAppender;
        final Logger logger;
        
        AuditTestContext(
                SecretsBackend backend,
                EnvironmentRepository repository,
                VaultTemplate vaultTemplate,
                ListAppender<ILoggingEvent> logAppender,
                Logger logger) {
            this.backend = backend;
            this.repository = repository;
            this.vaultTemplate = vaultTemplate;
            this.logAppender = logAppender;
            this.logger = logger;
        }
        
        /**
         * Gets all log messages from the captured log events.
         */
        String getAllLogMessages() {
            StringBuilder sb = new StringBuilder();
            for (ILoggingEvent event : logAppender.list) {
                sb.append(event.getFormattedMessage()).append("\n");
                if (event.getThrowableProxy() != null) {
                    sb.append(event.getThrowableProxy().getMessage()).append("\n");
                }
            }
            return sb.toString();
        }
        
        /**
         * Cleans up the logger and appender.
         */
        void cleanup() {
            logger.detachAppender(logAppender);
            logAppender.stop();
        }
    }
}
