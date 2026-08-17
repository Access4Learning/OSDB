// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when the storage backend is temporarily unavailable.
 * This exception maps to HTTP 503 Service Unavailable status.
 * 
 * <p>Common scenarios:
 * <ul>
 *   <li>Storage backend (S3, database, file system) is down or unreachable</li>
 *   <li>Storage service is undergoing maintenance</li>
 *   <li>Storage backend is overloaded and rejecting requests</li>
 *   <li>Network connectivity issues preventing access to storage</li>
 *   <li>Storage quota exceeded or capacity limits reached</li>
 * </ul>
 * 
 * <p>This exception indicates a temporary service unavailability. The client
 * may retry the request after a delay, as the storage backend may become
 * available again. This differs from {@link StorageException} which indicates
 * a permanent failure requiring investigation.
 * 
 * @see SifBrokerException
 * @see StorageException
 */
public class StorageUnavailableException extends SifBrokerException {
    
    /**
     * Constructs a new StorageUnavailableException with the specified message.
     * The error code is set to "STORAGE_002" and HTTP status to 503 Service Unavailable.
     * 
     * @param message the detail message explaining why storage is unavailable
     */
    public StorageUnavailableException(String message) {
        super("STORAGE_002", HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
