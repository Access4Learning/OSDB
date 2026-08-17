// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when attempting to create a Named Query Template with a
 * query token that already exists within the same solution.
 *
 * <p>This exception enforces the business rule that query tokens must be unique
 * within a solution (database constraint uk_xquery_token_solution).</p>
 *
 * <p>This exception results in HTTP 409 Conflict response.</p>
 *
 * @see org.sifunity.broker.environment.service.XQueryTemplateService
 */
public class DuplicateQueryTokenException extends SifBrokerException {

    private static final String ERROR_CODE = "XQUERY_001";

    /**
     * Constructs a new DuplicateQueryTokenException.
     *
     * @param queryToken the duplicate query token
     * @param solutionId the solution where the duplicate was found
     */
    public DuplicateQueryTokenException(String queryToken, String solutionId) {
        super(
            ERROR_CODE,
            HttpStatus.CONFLICT,
            "Named Query Template with token '" + queryToken +
            "' already exists in solution '" + solutionId + "'"
        );
    }
}
