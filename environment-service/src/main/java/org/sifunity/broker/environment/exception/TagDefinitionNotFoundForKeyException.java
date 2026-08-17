// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when no tag definition exists for a given tag key during entity tag creation.
 */
public class TagDefinitionNotFoundForKeyException extends SifBrokerException {
    private static final String ERROR_CODE = "TAG_DEF_004";

    public TagDefinitionNotFoundForKeyException(String tagKey) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST,
                String.format("No tag definition found for key: %s", tagKey));
    }
}
