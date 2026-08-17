// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

import java.util.UUID;

/**
 * Exception thrown when an ACL entry is not found.
 * 
 * <p>This exception is thrown when attempting to retrieve, update, or delete
 * an ACL entry that does not exist in the system.</p>
 * 
 * <p>Returns HTTP 404 Not Found status code.</p>
 * 
 * @see org.sifunity.broker.environment.service.AclService
 */
public class AclEntryNotFoundException extends SifBrokerException {
    
    private static final String ERROR_CODE = "ACL_001";
    private static final String ERROR_MESSAGE = "ACL entry not found";
    
    /**
     * Creates a new AclEntryNotFoundException for a specific ACL entry ID.
     * 
     * @param aclEntryId the ID of the ACL entry that was not found
     */
    public AclEntryNotFoundException(UUID aclEntryId) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND, 
              String.format("ACL entry with ID %s was not found", aclEntryId));
    }
    
    /**
     * Creates a new AclEntryNotFoundException with a custom message.
     * 
     * @param message the custom error message
     */
    public AclEntryNotFoundException(String message) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND, message);
    }
}