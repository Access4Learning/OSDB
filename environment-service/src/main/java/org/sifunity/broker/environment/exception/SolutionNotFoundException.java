// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when attempting to access a solution that does not exist.
 */
public class SolutionNotFoundException extends SifBrokerException {
    
    private static final String ERROR_CODE = "SOLUTION_001";
    
    public SolutionNotFoundException(String solutionId) {
        super(
            ERROR_CODE,
            HttpStatus.NOT_FOUND,
            "Solution with ID '" + solutionId + "' not found"
        );
    }
}
