// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets;

/**
 * Exception thrown when encryption or decryption operations fail.
 * 
 * <p>This exception is thrown by the
 * {@link org.sifunity.broker.environment.secrets.impl.MultiKeyBackend MultiKeyBackend}
 * implementation when cryptographic operations fail. It indicates a problem with the
 * encryption or decryption process itself, not with backend availability or configuration.
 * 
 * <p><b>Common Scenarios:</b>
 * <ul>
 *   <li>Decryption fails due to corrupted ciphertext</li>
 *   <li>Authentication tag verification fails (GCM mode)</li>
 *   <li>Key version prefix is present but the corresponding key is not configured</li>
 *   <li>Invalid base64 encoding in stored encrypted value</li>
 *   <li>Cipher initialization fails due to invalid key or parameters</li>
 *   <li>Encrypted data format is invalid (wrong IV length, missing components)</li>
 * </ul>
 * 
 * <p><b>Error Handling:</b> This exception typically indicates a non-recoverable error
 * that requires investigation:
 * <ul>
 *   <li>Data corruption in the database</li>
 *   <li>Mismatch between encryption key used to encrypt and key used to decrypt</li>
 *   <li>Missing encryption key version in configuration</li>
 *   <li>Tampering with encrypted data</li>
 * </ul>
 * 
 * <p>Unlike {@link BackendUnavailableException}, this exception should not trigger
 * automatic retries as the operation is unlikely to succeed without manual intervention.
 * 
 * <p><b>Security Implications:</b> When GCM authentication fails, it may indicate:
 * <ul>
 *   <li>Data tampering or corruption</li>
 *   <li>Wrong decryption key being used</li>
 *   <li>Bit flips in storage or transmission</li>
 * </ul>
 * 
 * <p>The system should log these failures for security auditing purposes, as repeated
 * failures may indicate an attack or system compromise.
 * 
 * <p><b>Example Usage:</b>
 * <pre>
 * try {
 *     byte[] plaintext = cipher.doFinal(ciphertext);
 * } catch (AEADBadTagException e) {
 *     throw new EncryptionException("Decryption failed: authentication tag mismatch", e);
 * } catch (IllegalBlockSizeException | BadPaddingException e) {
 *     throw new EncryptionException("Decryption failed: invalid ciphertext", e);
 * }
 * </pre>
 * 
 * @see SecretsBackend
 * @since 1.0
 */
public class EncryptionException extends SecretsBackendException {
    
    /**
     * Constructs a new encryption exception with the specified detail message.
     * 
     * <p>The message should describe what encryption or decryption operation failed
     * but should not include sensitive information such as encryption keys, plaintext
     * secrets, or detailed cryptographic parameters.
     * 
     * @param message the detail message explaining the encryption failure. Should not
     *                contain sensitive data such as keys or plaintext values.
     */
    public EncryptionException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new encryption exception with the specified detail message and cause.
     * 
     * <p>This constructor is useful for wrapping lower-level cryptographic exceptions
     * (such as {@link javax.crypto.AEADBadTagException}, {@link javax.crypto.BadPaddingException},
     * or {@link javax.crypto.IllegalBlockSizeException}) while providing additional
     * context about the operation that failed.
     * 
     * <p><b>Example:</b>
     * <pre>
     * try {
     *     Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
     *     cipher.init(Cipher.DECRYPT_MODE, key, spec);
     *     return cipher.doFinal(ciphertext);
     * } catch (GeneralSecurityException e) {
     *     throw new EncryptionException("Failed to decrypt secret", e);
     * }
     * </pre>
     * 
     * @param message the detail message explaining the encryption failure. Should not
     *                contain sensitive data such as keys or plaintext values.
     * @param cause the underlying cryptographic exception that caused this failure.
     *              May be null if no underlying cause exists.
     */
    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
