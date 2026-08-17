// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.properties;

import net.jqwik.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.impl.VaultBackend;
import org.sifunity.broker.environment.secrets.config.VaultProperties;
import org.springframework.vault.core.VaultSysOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultHealth;
import org.springframework.vault.support.VaultResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Vault Backend round-trip operations.
 * 
 * **Property 6: Vault Backend Round-Trip**
 * **Validates: Requirements 3.2, 3.3**
 */
class VaultRoundTripProperties {
    
    @Property(tries = 10)
    @Tag("Feature: pluggable-secrets-backend, Property 6: Vault Backend Round-Trip")
    void vaultBackendRoundTripPreservesSecret(@ForAll("secrets") String secret) {
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
        
        // Mock read operation
        when(vaultTemplate.read(anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(0);
            Map<String, Object> data = vaultStorage.get(path);
            if (data == null) {
                return null;
            }
            VaultResponse response = new VaultResponse();
            response.setData(data);
            return response;
        });
        
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
        
        // When: We store the secret and then retrieve it
        vaultBackend.store(environmentId, secret);
        String retrieved = vaultBackend.retrieve(environmentId);
        
        // Then: The retrieved secret should match the original
        assertThat(retrieved).isEqualTo(secret);
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
