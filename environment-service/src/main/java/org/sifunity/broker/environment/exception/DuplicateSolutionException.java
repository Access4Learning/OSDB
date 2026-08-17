// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when attempting to create a solution with a solution ID
 * that already exists within the specified domain.
 */
public class DuplicateSolutionException extends SifBrokerException {
    
    private static final String ERROR_CODE = "SOLUTION_002";
    
    public DuplicateSolutionException(String domainId, String solutionId) {
        super(
            ERROR_CODE,
            HttpStatus.CONFLICT,
            "Solution with ID '" + solutionId + "' already exists in domain '" + domainId + "'"
        );
    }
}
