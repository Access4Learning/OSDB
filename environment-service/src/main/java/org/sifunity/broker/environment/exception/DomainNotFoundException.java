// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when attempting to access a domain that does not exist.
 * 
 * <p>This exception is thrown when querying, updating, or deleting a domain
 * by domain ID and the domain cannot be found in the database. It is also thrown
 * when attempting to create a solution in a non-existent domain.</p>
 * 
 * <p>This exception results in HTTP 404 Not Found response.</p>
 * 
 * @see org.sifunity.broker.environment.service.DomainService
 */
public class DomainNotFoundException extends SifBrokerException {
    
    private static final String ERROR_CODE = "DOMAIN_001";
    
    /**
     * Constructs a new DomainNotFoundException with the specified domain ID.
     * 
     * @param domainId the ID of the domain that was not found
     */
    public DomainNotFoundException(String domainId) {
        super(
            ERROR_CODE,
            HttpStatus.NOT_FOUND,
            "Domain with ID '" + domainId + "' not found"
        );
    }
}
