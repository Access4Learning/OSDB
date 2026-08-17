// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;
import java.util.List;

/**
 * Exception thrown when an entity tag value is not in the tag definition's allowed values list.
 */
public class InvalidTagValueException extends SifBrokerException {
    private static final String ERROR_CODE = "TAG_DEF_003";

    public InvalidTagValueException(String value, String tagName, List<String> allowedValues) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST,
                String.format("Invalid tag value '%s' for tag '%s'. Allowed values: %s",
                        value, tagName, allowedValues));
    }
}
