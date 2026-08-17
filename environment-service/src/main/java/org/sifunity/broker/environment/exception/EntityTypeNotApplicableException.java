// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Exception thrown when an entity tag's entity type is not in the tag definition's applicable entity types.
 */
public class EntityTypeNotApplicableException extends SifBrokerException {
    private static final String ERROR_CODE = "TAG_DEF_005";

    public EntityTypeNotApplicableException(String tagName, String entityType) {
        super(ERROR_CODE, HttpStatus.BAD_REQUEST,
                String.format("Tag '%s' is not applicable to entity type: %s",
                        tagName, entityType));
    }
}
