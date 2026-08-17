// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when attempting to create a domain with a domain ID
 * that already exists.
 * 
 * <p>Domain IDs must be globally unique across all domains. This exception is thrown
 * during domain creation when the provided domain ID conflicts with
 * an existing domain.</p>
 * 
 * <p>This exception results in HTTP 409 Conflict response.</p>
 * 
 * @see org.sifunity.broker.environment.service.DomainService#createDomain
 */
public class DuplicateDomainException extends SifBrokerException {
    
    private static final String ERROR_CODE = "DOMAIN_002";
    
    /**
     * Constructs a new DuplicateDomainException with the specified domain ID.
     * 
     * @param domainId the domain ID that already exists
     */
    public DuplicateDomainException(String domainId) {
        super(
            ERROR_CODE,
            HttpStatus.CONFLICT,
            "Domain with ID '" + domainId + "' already exists"
        );
    }
}
