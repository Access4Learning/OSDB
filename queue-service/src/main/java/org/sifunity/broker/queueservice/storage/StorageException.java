// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.storage;

/**
 * Exception thrown when storage operations fail.
 * Wraps underlying storage provider exceptions.
 */
public class StorageException extends RuntimeException {
    
    public StorageException(String message) {
        super(message);
    }
    
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
