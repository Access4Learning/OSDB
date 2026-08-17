// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.domain.EntityType;
import java.util.UUID;

/**
 * Exception thrown when attempting to access an entity tag that does not exist.
 */
public class EntityTagNotFoundException extends SifBrokerException {
    private static final String ERROR_CODE = "ENTITY_TAG_001";

    public EntityTagNotFoundException(String solutionId, EntityType entityType,
                                      UUID entityId, String tagKey) {
        super(ERROR_CODE, HttpStatus.NOT_FOUND,
                String.format("Tag '%s' not found on %s/%s in solution '%s'",
                        tagKey, entityType, entityId, solutionId));
    }
}
