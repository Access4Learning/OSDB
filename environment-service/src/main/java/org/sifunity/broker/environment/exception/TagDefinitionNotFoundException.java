// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;
import java.util.UUID;

/**
 * Exception thrown when a tag definition cannot be found by its ID.
 */
public class TagDefinitionNotFoundException extends SifBrokerException {
    private static final String ERROR_CODE = "TAG_DEF_001";

    public TagDefinitionNotFoundException(UUID id) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND,
                String.format("Tag definition not found with id: %s", id));
    }
}
