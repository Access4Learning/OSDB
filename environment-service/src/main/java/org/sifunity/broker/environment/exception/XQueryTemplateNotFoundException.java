// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when a Named Query Template cannot be found by its query token
 * within a given solution.
 *
 * <p>This exception results in HTTP 404 Not Found response.</p>
 *
 * @see org.sifunity.broker.environment.service.XQueryTemplateService
 */
public class XQueryTemplateNotFoundException extends SifBrokerException {

    private static final String ERROR_CODE = "XQUERY_002";

    /**
     * Constructs a new XQueryTemplateNotFoundException.
     *
     * @param queryToken the query token that was not found
     * @param solutionId the solution where the template was sought
     */
    public XQueryTemplateNotFoundException(String queryToken, String solutionId) {
        super(
            ERROR_CODE,
            HttpStatus.NOT_FOUND,
            "Named Query Template with token '" + queryToken +
            "' not found in solution '" + solutionId + "'"
        );
    }
}
