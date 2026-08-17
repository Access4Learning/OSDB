// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.impl;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.BackendUnavailableException;
import org.sifunity.broker.environment.secrets.SecretNotFoundException;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.sifunity.broker.environment.secrets.SecretsBackendException;
import org.sifunity.broker.environment.secrets.config.VaultProperties;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.util.Map;
import java.util.UUID;

/**
 * Vault backend - integrates with HashiCorp Vault.
 * 
 * <p>Stores only reference IDs in the database, actual secrets
 * are stored in Vault.
 */
@Slf4j
public class VaultBackend implements SecretsBackend {
    
    private final EnvironmentRepository environmentRepository;
    private final VaultTemplate vaultTemplate;
    private final String secretsPath;
    
    public VaultBackend(
            EnvironmentRepository environmentRepository,
            VaultTemplate vaultTemplate,
            VaultProperties properties) {
        this.environmentRepository = environmentRepository;
        this.vaultTemplate = vaultTemplate;
        this.secretsPath = properties.getSecretsPath();
        
        validateVaultConnection();
    }
    
    @Override
    public void store(UUID environmentId, String secret) {
        try {
            String vaultPath = buildVaultPath(environmentId);
            
            vaultTemplate.write(vaultPath, Map.of("value", secret));
            
            // Store reference ID in database
            Environment env = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new SecretNotFoundException(environmentId));
            env.setSharedSecret(vaultPath); // Store path as reference
            environmentRepository.save(env);
            
        } catch (SecretNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to store secret in Vault for environment {}", environmentId, e);
            throw new SecretsBackendException("Failed to store secret in Vault", e);
        }
    }
    
    @Override
    public String retrieve(UUID environmentId) {
        try {
            Environment env = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new SecretNotFoundException(environmentId));
            
            String vaultPath = env.getSharedSecret();
            VaultResponse response = vaultTemplate.read(vaultPath);
            
            if (response == null || response.getData() == null) {
                throw new SecretNotFoundException(environmentId);
            }
            
            return (String) response.getData().get("value");
            
        } catch (SecretNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve secret from Vault for environment {}", environmentId, e);
            throw new SecretsBackendException("Failed to retrieve secret from Vault", e);
        }
    }
    
    @Override
    public void delete(UUID environmentId) {
        try {
            Environment env = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new SecretNotFoundException(environmentId));
            
            String vaultPath = env.getSharedSecret();
            if (vaultPath != null) {
                vaultTemplate.delete(vaultPath);
            }
            
            env.setSharedSecret(null);
            environmentRepository.save(env);
            
        } catch (SecretNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete secret from Vault for environment {}", environmentId, e);
            throw new SecretsBackendException("Failed to delete secret from Vault", e);
        }
    }
    
    @Override
    public boolean isHealthy() {
        try {
            // Attempt to read Vault health endpoint
            vaultTemplate.opsForSys().health();
            return true;
        } catch (Exception e) {
            log.error("Vault health check failed", e);
            return false;
        }
    }
    
    @Override
    public String getBackendType() {
        return "vault";
    }
    
    private String buildVaultPath(UUID environmentId) {
        return secretsPath + "/environment/" + environmentId;
    }
    
    private void validateVaultConnection() {
        if (!isHealthy()) {
            throw new BackendUnavailableException(
                "Cannot connect to Vault - check configuration and network connectivity");
        }
    }
}
