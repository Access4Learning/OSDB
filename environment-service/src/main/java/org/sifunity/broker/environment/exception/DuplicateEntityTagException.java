// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.domain.EntityType;
import java.util.UUID;

/**
 * Exception thrown when attempting to create a duplicate entity tag.
 */
public class DuplicateEntityTagException extends SifBrokerException {
    private static final String ERROR_CODE = "ENTITY_TAG_002";

    public DuplicateEntityTagException(String solutionId, EntityType entityType,
                                       UUID entityId, String tagKey) {
        super(ERROR_CODE, HttpStatus.CONFLICT,
                String.format("Tag '%s' already exists on %s/%s in solution '%s'",
                        tagKey, entityType, entityId, solutionId));
    }
}
