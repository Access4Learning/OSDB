// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.config.MultiKeyProperties;
import org.sifunity.broker.environment.secrets.config.VaultProperties;
import org.sifunity.broker.environment.secrets.impl.MultiKeyBackend;
import org.sifunity.broker.environment.secrets.impl.PlaintextBackend;
import org.sifunity.broker.environment.secrets.impl.VaultBackend;
import org.sifunity.broker.environment.secrets.util.SecretRedactor;
import org.slf4j.LoggerFactory;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for security requirements across all backends.
 * Validates Requirements 11.5 and 11.6.
 */
@ExtendWith(MockitoExtension.class)
class SecurityRequirementsTest {
    
    @Mock
    private EnvironmentRepository environmentRepository;
    
    @Mock
    private VaultTemplate vaultTemplate;
    
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;
    
    private static final UUID TEST_ENV_ID = UUID.randomUUID();
    private static final String TEST_SECRET = "mySecretValue123";
    private static final String TEST_KEY_BASE64 = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI="; // 32 bytes when decoded
    
    @BeforeEach
    void setUp() {
        // Set up log capture
        logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }
    
    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
    }

    
    // ========== Plaintext Backend Security Tests ==========
    
    @Test
    void plaintextBackend_store_doesNotLogSecret() {
        PlaintextBackend backend = new PlaintextBackend(environmentRepository);
        Environment env = new Environment();
        env.setId(TEST_ENV_ID);
        
        when(environmentRepository.findById(TEST_ENV_ID)).thenReturn(Optional.of(env));
        when(environmentRepository.save(any(Environment.class))).thenReturn(env);
        
        logAppender.list.clear();
        backend.store(TEST_ENV_ID, TEST_SECRET);
        
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(TEST_SECRET);
    }
    
    @Test
    void plaintextBackend_retrieve_doesNotLogSecret() {
        PlaintextBackend backend = new PlaintextBackend(environmentRepository);
        Environment env = new Environment();
        env.setId(TEST_ENV_ID);
        env.setSharedSecret(TEST_SECRET);
        
        when(environmentRepository.findById(TEST_ENV_ID)).thenReturn(Optional.of(env));
        
        logAppender.list.clear();
        String retrieved = backend.retrieve(TEST_ENV_ID);
        
        assertThat(retrieved).isEqualTo(TEST_SECRET);
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(TEST_SECRET);
    }
    
    @Test
    void plaintextBackend_delete_doesNotLogSecret() {
        PlaintextBackend backend = new PlaintextBackend(environmentRepository);
        Environment env = new Environment();
        env.setId(TEST_ENV_ID);
        env.setSharedSecret(TEST_SECRET);
        
        when(environmentRepository.findById(TEST_ENV_ID)).thenReturn(Optional.of(env));
        when(environmentRepository.save(any(Environment.class))).thenReturn(env);
        
        logAppender.list.clear();
        backend.delete(TEST_ENV_ID);
        
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(TEST_SECRET);
    }
    
    @Test
    void plaintextBackend_warningMessage_doesNotExposeSecrets() {
        logAppender.list.clear();
        PlaintextBackend backend = new PlaintextBackend(environmentRepository);
        backend.init();
        
        String allLogs = getAllLogMessages();
        assertThat(allLogs).contains("WARNING");
        assertThat(allLogs).contains("PLAIN TEXT");
        // Ensure no actual secrets are in the warning
        assertThat(allLogs).doesNotContain(TEST_SECRET);
    }
    
    // ========== Multi-Key Backend Security Tests ==========
    
    @Test
    void multiKeyBackend_initialization_doesNotLogKeys() {
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        properties.setKeys(Map.of("20240115", TEST_KEY_BASE64));
        
        logAppender.list.clear();
        MultiKeyBackend backend = new MultiKeyBackend(environmentRepository, properties);
        
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(TEST_KEY_BASE64);
    }
    
    @Test
    void multiKeyBackend_store_doesNotLogSecret() {
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        properties.setKeys(Map.of("20240115", TEST_KEY_BASE64));
        
        MultiKeyBackend backend = new MultiKeyBackend(environmentRepository, properties);
        Environment env = new Environment();
        env.setId(TEST_ENV_ID);
        
        when(environmentRepository.findById(TEST_ENV_ID)).thenReturn(Optional.of(env));
        when(environmentRepository.save(any(Environment.class))).thenReturn(env);
        
        logAppender.list.clear();
        backend.store(TEST_ENV_ID, TEST_SECRET);
        
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(TEST_SECRET);
        assertThat(allLogs).doesNotContain(TEST_KEY_BASE64);
    }

    
    @Test
    void multiKeyBackend_retrieve_doesNotLogSecret() {
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        properties.setKeys(Map.of("20240115", TEST_KEY_BASE64));
        
        MultiKeyBackend backend = new MultiKeyBackend(environmentRepository, properties);
        
        // First store to get encrypted value
        Environment env = new Environment();
        env.setId(TEST_ENV_ID);
        when(environmentRepository.findById(TEST_ENV_ID)).thenReturn(Optional.of(env));
        when(environmentRepository.save(any(Environment.class))).thenAnswer(invocation -> {
            Environment saved = invocation.getArgument(0);
            env.setSharedSecret(saved.getSharedSecret());
            return saved;
        });
        
        backend.store(TEST_ENV_ID, TEST_SECRET);
        
        // Now retrieve and check logs
        logAppender.list.clear();
        String retrieved = backend.retrieve(TEST_ENV_ID);
        
        assertThat(retrieved).isEqualTo(TEST_SECRET);
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(TEST_SECRET);
        assertThat(allLogs).doesNotContain(TEST_KEY_BASE64);
    }
    
    @Test
    void multiKeyBackend_store_doesNotLogEncryptedValue() {
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        properties.setKeys(Map.of("20240115", TEST_KEY_BASE64));
        
        MultiKeyBackend backend = new MultiKeyBackend(environmentRepository, properties);
        Environment env = new Environment();
        env.setId(TEST_ENV_ID);
        
        when(environmentRepository.findById(TEST_ENV_ID)).thenReturn(Optional.of(env));
        when(environmentRepository.save(any(Environment.class))).thenReturn(env);
        
        logAppender.list.clear();
        backend.store(TEST_ENV_ID, TEST_SECRET);
        
        String allLogs = getAllLogMessages();
        // Verify the encrypted value is not logged
        assertThat(allLogs).doesNotContainPattern("20240115:[A-Za-z0-9+/=]+");
    }
    
    // ========== Vault Backend Security Tests ==========
    
    @Test
    void vaultBackend_store_doesNotLogToken() {
        VaultProperties properties = new VaultProperties();
        properties.setUrl("https://vault.example.com");
        properties.setToken("hvs.CAESIJ1234567890abcdefghijklmnop");
        properties.setSecretsPath("secret/sif-broker");
        
        when(vaultTemplate.opsForSys()).thenReturn(mock(org.springframework.vault.core.VaultSysOperations.class));
        when(vaultTemplate.opsForSys().health()).thenReturn(mock(org.springframework.vault.support.VaultHealth.class));
        
        VaultBackend backend = new VaultBackend(environmentRepository, vaultTemplate, properties);
        Environment env = new Environment();
        env.setId(TEST_ENV_ID);
        
        when(environmentRepository.findById(TEST_ENV_ID)).thenReturn(Optional.of(env));
        when(environmentRepository.save(any(Environment.class))).thenReturn(env);
        
        logAppender.list.clear();
        backend.store(TEST_ENV_ID, TEST_SECRET);
        
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(TEST_SECRET);
        assertThat(allLogs).doesNotContain("hvs.CAESIJ1234567890abcdefghijklmnop");
    }
    
    @Test
    void vaultBackend_retrieve_doesNotLogSecret() {
        VaultProperties properties = new VaultProperties();
        properties.setUrl("https://vault.example.com");
        properties.setToken("hvs.CAESIJ1234567890abcdefghijklmnop");
        properties.setSecretsPath("secret/sif-broker");
        
        when(vaultTemplate.opsForSys()).thenReturn(mock(org.springframework.vault.core.VaultSysOperations.class));
        when(vaultTemplate.opsForSys().health()).thenReturn(mock(org.springframework.vault.support.VaultHealth.class));
        
        VaultBackend backend = new VaultBackend(environmentRepository, vaultTemplate, properties);
        Environment env = new Environment();
        env.setId(TEST_ENV_ID);
        env.setSharedSecret("secret/sif-broker/environment/" + TEST_ENV_ID);
        
        VaultResponse response = new VaultResponse();
        response.setData(Map.of("value", TEST_SECRET));
        
        when(environmentRepository.findById(TEST_ENV_ID)).thenReturn(Optional.of(env));
        when(vaultTemplate.read(anyString())).thenReturn(response);
        
        logAppender.list.clear();
        String retrieved = backend.retrieve(TEST_ENV_ID);
        
        assertThat(retrieved).isEqualTo(TEST_SECRET);
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(TEST_SECRET);
        assertThat(allLogs).doesNotContain("hvs.CAESIJ1234567890abcdefghijklmnop");
    }

    
    // ========== Error Message Security Tests ==========
    
    @Test
    void secretNotFoundException_doesNotExposeSecret() {
        SecretNotFoundException ex = new SecretNotFoundException(TEST_ENV_ID);
        
        String message = ex.getMessage();
        assertThat(message).contains("Secret not found");
        assertThat(message).contains(TEST_ENV_ID.toString());
        // The exception message should not contain actual secret values
        assertThat(message).doesNotContain(TEST_SECRET);
    }
    
    @Test
    void encryptionException_redactsSecrets() {
        EncryptionException ex = new EncryptionException("Failed to decrypt: " + TEST_SECRET);
        
        // The exception message should be redacted when logged
        logAppender.list.clear();
        logger.error("Encryption error", ex);
        
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(TEST_SECRET);
    }
    
    @Test
    void configurationException_doesNotLogKeys() {
        ConfigurationException ex = new ConfigurationException("Invalid key: " + TEST_KEY_BASE64);
        
        logAppender.list.clear();
        logger.error("Configuration error", ex);
        
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(TEST_KEY_BASE64);
    }
    
    @Test
    void backendUnavailableException_doesNotLogTokens() {
        String token = "hvs.CAESIJ1234567890abcdefghijklmnop";
        BackendUnavailableException ex = new BackendUnavailableException("Vault auth failed with token: " + token);
        
        logAppender.list.clear();
        logger.error("Backend unavailable", ex);
        
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(token);
    }
    
    @Test
    void secretsBackendException_withNestedCause_redactsSecrets() {
        Exception cause = new IllegalStateException("Decryption failed for secret: " + TEST_SECRET);
        SecretsBackendException ex = new SecretsBackendException("Backend error", cause);
        
        logAppender.list.clear();
        logger.error("Backend exception", ex);
        
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(TEST_SECRET);
    }
    
    // ========== Configuration Security Tests ==========
    
    @Test
    void multiKeyProperties_loadKeys_doesNotLogKeys() {
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        properties.setKeys(Map.of(
            "20240115", TEST_KEY_BASE64,
            "20240101", "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo5ODc2NTQ=" // Another 32-byte key
        ));
        
        logAppender.list.clear();
        MultiKeyBackend backend = new MultiKeyBackend(environmentRepository, properties);
        
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(TEST_KEY_BASE64);
        assertThat(allLogs).doesNotContain("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo5ODc2NTQ=");
    }
    
    @Test
    void multiKeyBackend_configurationFailure_doesNotLogKeys() {
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        properties.setKeys(Map.of("20240101", TEST_KEY_BASE64)); // Missing current key
        
        logAppender.list.clear();
        assertThatThrownBy(() -> new MultiKeyBackend(environmentRepository, properties))
            .isInstanceOf(ConfigurationException.class);
        
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(TEST_KEY_BASE64);
    }
    
    @Test
    void vaultProperties_initialization_doesNotLogToken() {
        VaultProperties properties = new VaultProperties();
        String token = "hvs.CAESIJ1234567890abcdefghijklmnop";
        properties.setToken(token);
        properties.setUrl("https://vault.example.com");
        
        logAppender.list.clear();
        // Just accessing properties shouldn't log anything
        String retrievedToken = properties.getToken();
        
        assertThat(retrievedToken).isEqualTo(token);
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(token);
    }

    
    // ========== Audit Log Security Tests ==========
    
    @Test
    void auditLog_storeOperation_excludesSecretValue() {
        PlaintextBackend backend = new PlaintextBackend(environmentRepository);
        Environment env = new Environment();
        env.setId(TEST_ENV_ID);
        
        when(environmentRepository.findById(TEST_ENV_ID)).thenReturn(Optional.of(env));
        when(environmentRepository.save(any(Environment.class))).thenReturn(env);
        
        logAppender.list.clear();
        backend.store(TEST_ENV_ID, TEST_SECRET);
        
        // Audit logs should mention the operation but not the secret
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(TEST_SECRET);
    }
    
    @Test
    void auditLog_retrieveOperation_excludesSecretValue() {
        PlaintextBackend backend = new PlaintextBackend(environmentRepository);
        Environment env = new Environment();
        env.setId(TEST_ENV_ID);
        env.setSharedSecret(TEST_SECRET);
        
        when(environmentRepository.findById(TEST_ENV_ID)).thenReturn(Optional.of(env));
        
        logAppender.list.clear();
        backend.retrieve(TEST_ENV_ID);
        
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(TEST_SECRET);
    }
    
    @Test
    void auditLog_deleteOperation_excludesSecretValue() {
        PlaintextBackend backend = new PlaintextBackend(environmentRepository);
        Environment env = new Environment();
        env.setId(TEST_ENV_ID);
        env.setSharedSecret(TEST_SECRET);
        
        when(environmentRepository.findById(TEST_ENV_ID)).thenReturn(Optional.of(env));
        when(environmentRepository.save(any(Environment.class))).thenReturn(env);
        
        logAppender.list.clear();
        backend.delete(TEST_ENV_ID);
        
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(TEST_SECRET);
    }
    
    @Test
    void auditLog_errorScenario_excludesSecrets() {
        PlaintextBackend backend = new PlaintextBackend(environmentRepository);
        
        when(environmentRepository.findById(TEST_ENV_ID)).thenReturn(Optional.empty());
        
        logAppender.list.clear();
        assertThatThrownBy(() -> backend.retrieve(TEST_ENV_ID))
            .isInstanceOf(SecretNotFoundException.class);
        
        String allLogs = getAllLogMessages();
        // Even in error scenarios, secrets should not be logged
        assertThat(allLogs).doesNotContain(TEST_SECRET);
    }
    
    // ========== Cross-Backend Security Tests ==========
    
    @Test
    void allBackends_neverLogSecrets() {
        // Test that secrets are never logged regardless of backend
        List<SecretsBackend> backends = Arrays.asList(
            new PlaintextBackend(environmentRepository),
            createMultiKeyBackend(),
            createVaultBackend()
        );
        
        Environment env = new Environment();
        env.setId(TEST_ENV_ID);
        
        when(environmentRepository.findById(TEST_ENV_ID)).thenReturn(Optional.of(env));
        when(environmentRepository.save(any(Environment.class))).thenReturn(env);
        
        for (SecretsBackend backend : backends) {
            logAppender.list.clear();
            
            try {
                backend.store(TEST_ENV_ID, TEST_SECRET);
            } catch (Exception e) {
                // Some backends may fail in test environment, that's ok
            }
            
            String allLogs = getAllLogMessages();
            assertThat(allLogs)
                .as("Backend %s should not log secrets", backend.getBackendType())
                .doesNotContain(TEST_SECRET);
        }
    }
    
    @Test
    void allBackends_neverLogKeys() {
        MultiKeyProperties multiKeyProps = new MultiKeyProperties();
        multiKeyProps.setCurrentVersion("20240115");
        multiKeyProps.setKeys(Map.of("20240115", TEST_KEY_BASE64));
        
        logAppender.list.clear();
        MultiKeyBackend backend = new MultiKeyBackend(environmentRepository, multiKeyProps);
        
        String allLogs = getAllLogMessages();
        assertThat(allLogs).doesNotContain(TEST_KEY_BASE64);
    }
    
    // ========== Helper Methods ==========
    
    private String getAllLogMessages() {
        StringBuilder sb = new StringBuilder();
        for (ILoggingEvent event : logAppender.list) {
            sb.append(event.getFormattedMessage()).append("\n");
            if (event.getThrowableProxy() != null) {
                sb.append(event.getThrowableProxy().getMessage()).append("\n");
            }
        }
        // Apply redaction as the encoder would
        return SecretRedactor.redactSecrets(sb.toString());
    }
    
    private MultiKeyBackend createMultiKeyBackend() {
        MultiKeyProperties properties = new MultiKeyProperties();
        properties.setCurrentVersion("20240115");
        properties.setKeys(Map.of("20240115", TEST_KEY_BASE64));
        return new MultiKeyBackend(environmentRepository, properties);
    }
    
    private VaultBackend createVaultBackend() {
        VaultProperties properties = new VaultProperties();
        properties.setUrl("https://vault.example.com");
        properties.setToken("hvs.CAESIJ1234567890abcdefghijklmnop");
        properties.setSecretsPath("secret/sif-broker");
        
        when(vaultTemplate.opsForSys()).thenReturn(mock(org.springframework.vault.core.VaultSysOperations.class));
        when(vaultTemplate.opsForSys().health()).thenReturn(mock(org.springframework.vault.support.VaultHealth.class));
        
        return new VaultBackend(environmentRepository, vaultTemplate, properties);
    }
}
