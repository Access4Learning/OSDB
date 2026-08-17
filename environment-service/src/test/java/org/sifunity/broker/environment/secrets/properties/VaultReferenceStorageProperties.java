// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.properties;

import net.jqwik.api.*;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.impl.VaultBackend;
import org.sifunity.broker.environment.secrets.config.VaultProperties;
import org.springframework.vault.core.VaultSysOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultHealth;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Vault Backend reference storage.
 * 
 * **Property 5: Vault Backend Stores References Not Secrets**
 * **Validates: Requirements 3.1**
 */
class VaultReferenceStorageProperties {
    
    @Property(tries = 10)
    @Tag("Feature: pluggable-secrets-backend, Property 5: Vault Backend Stores References Not Secrets")
    void vaultBackendStoresReferenceNotSecret(@ForAll("secrets") String secret) {
        // Setup mocks for each property test execution
        EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
        VaultTemplate vaultTemplate = mock(VaultTemplate.class);
        VaultSysOperations vaultSysOperations = mock(VaultSysOperations.class);
        
        VaultProperties properties = new VaultProperties();
        properties.setUrl("https://vault.example.com:8200");
        properties.setToken("test-token");
        properties.setSecretsPath("secret/test");
        
        // Mock health check
        when(vaultTemplate.opsForSys()).thenReturn(vaultSysOperations);
        when(vaultSysOperations.health()).thenReturn(mock(VaultHealth.class));
        
        // Simulate Vault storage in memory
        Map<String, Map<String, Object>> vaultStorage = new HashMap<>();
        
        // Mock write operation
        doAnswer(invocation -> {
            String path = invocation.getArgument(0);
            Map<String, Object> data = invocation.getArgument(1);
            vaultStorage.put(path, new HashMap<>(data));
            return null;
        }).when(vaultTemplate).write(anyString(), any(Map.class));
        
        VaultBackend vaultBackend = new VaultBackend(environmentRepository, vaultTemplate, properties);
        
        // Given: A random environment ID
        UUID environmentId = UUID.randomUUID();
        Environment env = new Environment();
        env.setId(environmentId);
        
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(env));
        when(environmentRepository.save(any(Environment.class))).thenAnswer(invocation -> {
            Environment saved = invocation.getArgument(0);
            env.setSharedSecret(saved.getSharedSecret());
            return saved;
        });
        
        // When: We store the secret
        vaultBackend.store(environmentId, secret);
        
        // Then: The database should contain a Vault path reference, not the secret
        String storedValue = env.getSharedSecret();
        assertThat(storedValue).isNotNull();
        // The stored value should be a Vault path, not the actual secret
        // We verify this by checking it starts with the expected path prefix
        assertThat(storedValue).startsWith("secret/test/environment/");
        assertThat(storedValue).contains(environmentId.toString());
        // The stored value should not equal the secret (it's a path, not the secret itself)
        assertThat(storedValue).isNotEqualTo(secret);
        
        // And: The actual secret should be in Vault, not the database
        assertThat(vaultStorage.get(storedValue)).isNotNull();
        assertThat(vaultStorage.get(storedValue).get("value")).isEqualTo(secret);
    }
    
    @Provide
    Arbitrary<String> secrets() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '-', '_', '=', '+')
            .ofMinLength(1)
            .ofMaxLength(1000);
    }
}
