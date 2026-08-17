// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.storage;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interface for pluggable message payload storage providers.
 * Implementations can store messages in S3, filesystem, or other backends.
 */
public interface StorageProvider {

    Logger log = LoggerFactory.getLogger(StorageProvider.class);
    
    /**
     * Store message payload and return storage key
     * 
     * @param storageKey the hierarchical key for storing the message
     * @param payload the message payload to store
     * @return the storage key where the payload was stored
     * @throws StorageException if storage operation fails
     */
    String store(String storageKey, Object payload) throws StorageException;
    
    /**
     * Retrieve message payload by storage key
     * 
     * @param storageKey the hierarchical key for the message
     * @return optional containing the payload if found, empty otherwise
     * @throws StorageException if retrieval operation fails
     */
    Optional<Object> retrieve(String storageKey) throws StorageException;
    
    /**
     * Delete message payload by storage key
     * 
     * @param storageKey the hierarchical key for the message
     * @throws StorageException if deletion operation fails
     */
    void delete(String storageKey) throws StorageException;
    
    /**
     * Check if message payload exists
     * 
     * @param storageKey the hierarchical key for the message
     * @return true if the payload exists, false otherwise
     * @throws StorageException if existence check fails
     */
    boolean exists(String storageKey) throws StorageException;

    /**
     * Delete multiple payloads in a single batch operation.
     * Default implementation falls back to sequential single deletes.
     * Individual failures are logged and do not abort the batch.
     *
     * @param storageKeys list of keys to delete (max 1000)
     */
    default void deleteBatch(List<String> storageKeys) {
        for (String key : storageKeys) {
            try {
                delete(key);
            } catch (StorageException e) {
                log.warn("Failed to delete storage key during batch delete: {}", key, e);
            }
        }
    }

    /**
     * Delete all objects matching the given key prefix.
     * Used for cascade cleanup when a domain or solution is deleted.
     *
     * @param prefix the key prefix to match (e.g., "domainId/" or "domainId/solutionId/")
     * @return the count of objects deleted
     */
    default long deleteByPrefix(String prefix) {
        log.warn("deleteByPrefix not implemented for this provider");
        return 0L;
    }
}
