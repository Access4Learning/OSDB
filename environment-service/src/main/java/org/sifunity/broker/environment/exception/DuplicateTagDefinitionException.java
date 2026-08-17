// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when attempting to create a tag definition with a name that already exists (case-insensitive).
 */
public class DuplicateTagDefinitionException extends SifBrokerException {
    private static final String ERROR_CODE = "TAG_DEF_002";

    public DuplicateTagDefinitionException(String tagName) {
        super(ERROR_CODE, HttpStatus.CONFLICT,
                String.format("Tag definition already exists with name: %s", tagName));
    }
}
