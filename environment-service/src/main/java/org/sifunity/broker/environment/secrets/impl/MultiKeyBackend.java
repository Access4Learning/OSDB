// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.impl;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.secrets.ConfigurationException;
import org.sifunity.broker.environment.secrets.EncryptionException;
import org.sifunity.broker.environment.secrets.SecretNotFoundException;
import org.sifunity.broker.environment.secrets.SecretsBackend;
import org.sifunity.broker.environment.secrets.config.MultiKeyProperties;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Multi-key encryption backend using AES-256-GCM.
 * 
 * <p>This backend provides application-level encryption for secrets with support
 * for multiple key versions, enabling key rotation without downtime. Secrets are
 * encrypted using AES-256-GCM (Galois/Counter Mode) which provides both
 * confidentiality and authenticity.
 * 
 * <p><b>Encryption Format:</b> Encrypted secrets are stored with a version prefix:
 * <pre>YYYYMMDD:base64_encrypted_data</pre>
 * 
 * <p>The version prefix allows the backend to identify which key was used for
 * encryption, enabling decryption of secrets encrypted with different key versions.
 * 
 * <p><b>Encryption Process:</b>
 * <ol>
 *   <li>Generate random 12-byte IV (Initialization Vector)</li>
 *   <li>Encrypt plaintext using AES-256-GCM with current key</li>
 *   <li>Combine IV + ciphertext + authentication tag</li>
 *   <li>Base64 encode the combined result</li>
 *   <li>Prefix with current key version (YYYYMMDD format)</li>
 * </ol>
 * 
 * <p><b>Decryption Process:</b>
 * <ol>
 *   <li>Extract version prefix (YYYYMMDD)</li>
 *   <li>Look up corresponding encryption key</li>
 *   <li>Base64 decode the encrypted data</li>
 *   <li>Extract IV, ciphertext, and authentication tag</li>
 *   <li>Decrypt using AES-256-GCM</li>
 *   <li>Return plaintext secret</li>
 * </ol>
 * 
 * <p><b>Key Rotation:</b> This backend supports seamless key rotation:
 * <ul>
 *   <li>Multiple key versions can be active simultaneously</li>
 *   <li>New secrets are encrypted with the current key version</li>
 *   <li>Old secrets can be decrypted with their original key version</li>
 *   <li>Migration job re-encrypts secrets to the current key version</li>
 * </ul>
 * 
 * <p><b>Security Properties:</b>
 * <ul>
 *   <li>AES-256-GCM provides authenticated encryption</li>
 *   <li>Random IV for each encryption (no IV reuse)</li>
 *   <li>128-bit authentication tag prevents tampering</li>
 *   <li>Keys loaded from environment variables (not hardcoded)</li>
 *   <li>Keys never logged or exposed in error messages</li>
 * </ul>
 * 
 * <p><b>Performance Characteristics:</b>
 * <ul>
 *   <li>Encryption: &lt; 50ms for typical secrets</li>
 *   <li>Decryption: &lt; 50ms for typical secrets</li>
 *   <li>No external service dependencies</li>
 * </ul>
 * 
 * <p><b>Configuration Example:</b>
 * <pre>
 * secrets:
 *   backend:
 *     type: multi-key
 *     multi-key:
 *       current-version: "20240115"
 *       keys:
 *         "20240115": "${ENCRYPTION_KEY_20240115}"
 *         "20240101": "${ENCRYPTION_KEY_20240101}"
 * </pre>
 * 
 * <p><b>Thread Safety:</b> This implementation is thread-safe. The SecureRandom
 * instance is thread-safe, and the key version map is immutable after construction.
 * 
 * @see SecretsBackend
 * @see MultiKeyProperties
 * @since 1.0
 */
@Slf4j
public class MultiKeyBackend implements SecretsBackend {
    
    /**
     * Cipher algorithm specification for AES-256-GCM.
     * 
     * <p>GCM (Galois/Counter Mode) provides authenticated encryption, combining
     * confidentiality and authenticity in a single operation. NoPadding is used
     * because GCM is a stream cipher mode that doesn't require padding.
     */
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    
    /**
     * GCM authentication tag length in bits.
     * 
     * <p>The 128-bit tag provides strong authentication, making it computationally
     * infeasible for an attacker to forge or tamper with encrypted data.
     */
    private static final int GCM_TAG_LENGTH = 128;
    
    /**
     * GCM initialization vector (IV) length in bytes.
     * 
     * <p>The 12-byte (96-bit) IV is the recommended size for GCM mode. A unique
     * random IV is generated for each encryption operation to ensure security.
     */
    private static final int GCM_IV_LENGTH = 12;
    
    /**
     * Repository for accessing environment entities in the database.
     */
    private final EnvironmentRepository environmentRepository;
    
    /**
     * Map of key versions to encryption keys.
     * 
     * <p>This map is immutable after construction and contains all configured
     * key versions. Keys are indexed by their version string (YYYYMMDD format).
     */
    private final Map<String, SecretKey> keyVersions;
    
    /**
     * Current key version for encrypting new secrets.
     * 
     * <p>This version is used as a prefix when storing encrypted secrets. It must
     * exist in the {@code keyVersions} map.
     */
    private final String currentKeyVersion;
    
    /**
     * Cryptographically strong random number generator for IV generation.
     * 
     * <p>This instance is thread-safe and used to generate unique random IVs
     * for each encryption operation.
     */
    private final SecureRandom secureRandom;
    
    /**
     * Constructs a new MultiKeyBackend with the specified dependencies.
     * 
     * <p>This constructor initializes the backend by loading encryption keys
     * from the provided properties and validating the configuration. If the
     * configuration is invalid, a {@link ConfigurationException} is thrown.
     * 
     * <p><b>Validation Performed:</b>
     * <ul>
     *   <li>At least one encryption key is configured</li>
     *   <li>Current key version exists in the keys map</li>
     *   <li>All keys are valid base64-encoded 256-bit values</li>
     * </ul>
     * 
     * @param environmentRepository the repository for accessing environment entities
     * @param properties the configuration properties containing encryption keys
     * @throws ConfigurationException if the configuration is invalid
     */
    public MultiKeyBackend(
            EnvironmentRepository environmentRepository,
            MultiKeyProperties properties) {
        this.environmentRepository = environmentRepository;
        this.keyVersions = loadKeys(properties);
        this.currentKeyVersion = properties.getCurrentVersion();
        this.secureRandom = new SecureRandom();
        
        validateConfiguration();
    }
    
    @Override
        public void store(UUID environmentId, String secret) {
            try {
                // Encrypt secret using current key version
                String encrypted = encrypt(secret, currentKeyVersion);

                // Prefix encrypted value with version in YYYYMMDD: format
                String versionedValue = currentKeyVersion + ":" + encrypted;

                // Load Environment entity from repository
                org.sifunity.broker.environment.domain.Environment env = environmentRepository.findById(environmentId)
                    .orElseThrow(() -> new org.sifunity.broker.environment.secrets.SecretNotFoundException(environmentId));

                // Set sharedSecret field with versioned value
                env.setSharedSecret(versionedValue);

                // Save entity
                environmentRepository.save(env);

            } catch (org.sifunity.broker.environment.secrets.SecretNotFoundException e) {
                // Re-throw SecretNotFoundException as-is
                throw e;
            } catch (Exception e) {
                // Wrap all other exceptions in SecretsBackendException
                throw new org.sifunity.broker.environment.secrets.SecretsBackendException(
                    "Failed to store encrypted secret for environment: " + environmentId, e);
            }
        }

    
    @Override
    public String retrieve(UUID environmentId) {
        try {
            Environment env = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new SecretNotFoundException(environmentId));

            String storedValue = env.getSharedSecret();

            // Handle legacy plaintext values (no colon) with warning log
            if (!storedValue.contains(":")) {
                log.warn("Found plaintext secret for environment {}, consider migration", 
                    environmentId);
                return storedValue;
            }

            // Check if the prefix matches YYYYMMDD format (8 digits)
            String[] parts = storedValue.split(":", 2);
            String potentialVersion = parts[0];
            
            // If prefix is exactly 8 digits, treat as encrypted with version prefix
            if (potentialVersion.matches("\\d{8}")) {
                // Check if we have the key for this version
                if (!keyVersions.containsKey(potentialVersion)) {
                    throw new IllegalStateException(
                        "Key version not found: " + potentialVersion);
                }
                String encrypted = parts[1];
                // Decrypt using version-specific key
                return decrypt(encrypted, potentialVersion);
            }
            
            // Otherwise, treat as plaintext secret that happens to contain a colon
            log.warn("Found plaintext secret with colon for environment {}, consider migration", 
                environmentId);
            return storedValue;

        } catch (SecretNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptionException("Failed to retrieve secret", e);
        }
    }

    
    @Override
    public void delete(UUID environmentId) {
        Environment env = environmentRepository.findById(environmentId)
            .orElseThrow(() -> new SecretNotFoundException(environmentId));
        env.setSharedSecret(null);
        environmentRepository.save(env);
    }

    
    @Override
    public boolean isHealthy() {
        return !keyVersions.isEmpty() && keyVersions.containsKey(currentKeyVersion);
    }

    
    @Override
    public String getBackendType() {
        return "multi-key";
    }

    
    /**
     * Loads encryption keys from configuration properties.
     * 
     * <p>This method converts the base64-encoded key strings from the configuration
     * into {@link SecretKey} objects suitable for use with the AES cipher.
     * 
     * <p><b>Key Validation:</b>
     * <ul>
     *   <li>Each key must be base64-encoded</li>
     *   <li>Each key must be exactly 256 bits (32 bytes) after decoding</li>
     *   <li>Invalid keys cause a {@link ConfigurationException}</li>
     * </ul>
     * 
     * @param properties the configuration properties containing key strings
     * @return a map of key versions to SecretKey objects
     * @throws ConfigurationException if any key is invalid
     */
    private Map<String, SecretKey> loadKeys(MultiKeyProperties properties) {
        Map<String, SecretKey> keyMap = new HashMap<>();
        
        if (properties.getKeys() == null || properties.getKeys().isEmpty()) {
            return keyMap;
        }
        
        for (Map.Entry<String, String> entry : properties.getKeys().entrySet()) {
            String version = entry.getKey();
            String base64Key = entry.getValue();
            
            try {
                // Base64 decode the key string
                byte[] keyBytes = java.util.Base64.getDecoder().decode(base64Key);
                
                // Validate key length is 256 bits (32 bytes)
                if (keyBytes.length != 32) {
                    throw new ConfigurationException(
                        "Invalid key length for version " + version + ": expected 32 bytes (256 bits), got " + keyBytes.length + " bytes");
                }
                
                // Create SecretKeySpec for AES algorithm
                SecretKey secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
                keyMap.put(version, secretKey);
                
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException(
                    "Invalid base64 encoding for key version " + version, e);
            }
        }
        
        return keyMap;
    }
    
    /**
     * Validates the backend configuration.
     * 
     * <p>This method ensures that the configuration is valid and complete before
     * the backend is used. It checks that:
     * <ul>
     *   <li>At least one encryption key is configured</li>
     *   <li>The current key version exists in the keys map</li>
     * </ul>
     * 
     * <p>If validation fails, a {@link ConfigurationException} is thrown with a
     * descriptive error message indicating what is wrong with the configuration.
     * 
     * @throws ConfigurationException if the configuration is invalid
     */
    private void validateConfiguration() {
        if (keyVersions.isEmpty()) {
            throw new ConfigurationException("No encryption keys configured");
        }
        if (!keyVersions.containsKey(currentKeyVersion)) {
            throw new ConfigurationException(
                "Current key version not found in configured keys: " + currentKeyVersion);
        }
    }
    
    /**
     * Encrypts a plaintext secret using AES-256-GCM with the specified key version.
     * 
     * <p>This method performs authenticated encryption, providing both confidentiality
     * and integrity protection. The encryption process:
     * <ol>
     *   <li>Generates a random 12-byte IV using {@link SecureRandom}</li>
     *   <li>Encrypts the plaintext using AES-256-GCM with the key for the specified version</li>
     *   <li>Combines IV + ciphertext + authentication tag into a single byte array</li>
     *   <li>Base64 encodes the combined result</li>
     * </ol>
     * 
     * <p><b>Security Properties:</b>
     * <ul>
     *   <li>Each encryption uses a unique random IV (no IV reuse)</li>
     *   <li>GCM mode provides authenticated encryption (AEAD)</li>
     *   <li>128-bit authentication tag prevents tampering</li>
     *   <li>No padding required (GCM is a stream cipher mode)</li>
     * </ul>
     * 
     * <p><b>Output Format:</b> The returned string is a base64-encoded byte array
     * containing: {@code [IV (12 bytes)][Ciphertext (variable)][Auth Tag (16 bytes)]}
     * 
     * <p><b>Thread Safety:</b> This method is thread-safe. The {@link SecureRandom}
     * instance is thread-safe, and each invocation uses a new IV.
     * 
     * @param plaintext the secret to encrypt (must not be null)
     * @param version the key version to use for encryption (must exist in keyVersions map)
     * @return base64-encoded string containing IV + ciphertext + authentication tag
     * @throws IllegalStateException if the key version is not found
     * @throws Exception if encryption fails (e.g., invalid key, cipher initialization failure)
     */
    private String encrypt(String plaintext, String version) throws Exception {
        SecretKey key = keyVersions.get(version);
        if (key == null) {
            throw new IllegalStateException("Key version not found: " + version);
        }
        
        // Generate random IV for this encryption operation
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);
        
        // Initialize cipher for encryption with GCM parameters
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(ALGORITHM);
        javax.crypto.spec.GCMParameterSpec spec = new javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, spec);
        
        // Encrypt plaintext (GCM automatically appends authentication tag)
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        
        // Combine IV + ciphertext (which includes auth tag)
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        
        // Base64 encode the combined result
        return java.util.Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Decrypts an encrypted secret using AES-256-GCM with the specified key version.
     *
     * <p>This method performs authenticated decryption, verifying both the integrity
     * and authenticity of the encrypted data. The decryption process:
     * <ol>
     *   <li>Base64 decodes the encrypted string</li>
     *   <li>Extracts the IV (first 12 bytes)</li>
     *   <li>Extracts the ciphertext + authentication tag (remaining bytes)</li>
     *   <li>Decrypts using AES-256-GCM with the key for the specified version</li>
     *   <li>Returns the plaintext secret</li>
     * </ol>
     *
     * <p><b>Security Properties:</b>
     * <ul>
     *   <li>GCM mode verifies the authentication tag before decryption</li>
     *   <li>Tampering or corruption causes decryption to fail</li>
     *   <li>Wrong key version causes decryption to fail</li>
     * </ul>
     *
     * <p><b>Input Format:</b> The encrypted string must be a base64-encoded byte array
     * containing: {@code [IV (12 bytes)][Ciphertext (variable)][Auth Tag (16 bytes)]}
     *
     * <p><b>Thread Safety:</b> This method is thread-safe. Each invocation operates
     * on independent data and cipher instances.
     *
     * @param encrypted the base64-encoded encrypted data (must not be null)
     * @param version the key version to use for decryption (must exist in keyVersions map)
     * @return the decrypted plaintext secret
     * @throws IllegalStateException if the key version is not found
     * @throws Exception if decryption fails (e.g., invalid data, authentication failure, wrong key)
     */
    private String decrypt(String encrypted, String version) throws Exception {
        SecretKey key = keyVersions.get(version);
        if (key == null) {
            throw new IllegalStateException("Key version not found: " + version);
        }

        // Base64 decode the encrypted data
        byte[] combined = java.util.Base64.getDecoder().decode(encrypted);

        // Extract IV (first 12 bytes) and ciphertext (remaining bytes)
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];

        System.arraycopy(combined, 0, iv, 0, iv.length);
        System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

        // Initialize cipher for decryption with GCM parameters
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(ALGORITHM);
        javax.crypto.spec.GCMParameterSpec spec = new javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, spec);

        // Decrypt ciphertext (GCM automatically verifies authentication tag)
        byte[] plaintext = cipher.doFinal(ciphertext);

        // Convert bytes to string using UTF-8 encoding
        return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
    }

}
