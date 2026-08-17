// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

import java.util.UUID;

/**
 * Exception thrown when attempting to reference a context that does not exist.
 * 
 * <p>This exception is thrown when creating an ACL entry with a non-null
 * contextUuid that does not correspond to an existing context in the database.</p>
 * 
 * <p>This exception results in HTTP 404 Not Found response.</p>
 * 
 * @see org.sifunity.broker.environment.service.AclService#createAclEntry
 */
public class ContextNotFoundException extends SifBrokerException {
    
    private static final String ERROR_CODE = "CTX_001";
    
    /**
     * Constructs a new ContextNotFoundException with the specified context UUID.
     * 
     * @param contextUuid the UUID of the context that was not found
     */
    public ContextNotFoundException(UUID contextUuid) {
        super(
            ERROR_CODE,
            HttpStatus.NOT_FOUND,
            "Context not found: " + contextUuid
        );
    }

    /**
     * Constructs a new ContextNotFoundException with the specified context ID string.
     *
     * @param contextId the context ID that was not found
     */
    public ContextNotFoundException(String contextId) {
        super(
            ERROR_CODE,
            HttpStatus.NOT_FOUND,
            "Context with ID '" + contextId + "' not found"
        );
    }
}
