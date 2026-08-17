// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a storage operation fails due to an internal error.
 * This exception maps to HTTP 500 Internal Server Error status.
 * 
 * <p>Common scenarios:
 * <ul>
 *   <li>Failed to write message payload to storage backend</li>
 *   <li>Failed to read message payload from storage backend</li>
 *   <li>Storage backend returned an unexpected error</li>
 *   <li>Data corruption or integrity issues in storage</li>
 *   <li>Storage operation timeout or connection failure</li>
 * </ul>
 * 
 * <p>This exception indicates an internal server error that prevented the storage
 * operation from completing successfully. The client should not retry immediately
 * as the issue is likely on the server side. The underlying cause is typically
 * included to aid in debugging.
 * 
 * @see SifBrokerException
 * @see StorageUnavailableException
 */
public class StorageException extends SifBrokerException {
    
    /**
     * Constructs a new StorageException with the specified message and cause.
     * The error code is set to "STORAGE_001" and HTTP status to 500 Internal Server Error.
     * 
     * @param message the detail message explaining the storage failure
     * @param cause   the underlying cause of the storage failure
     */
    public StorageException(String message, Throwable cause) {
        super("STORAGE_001", HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }
}
