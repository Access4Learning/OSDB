// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.exception;

import org.springframework.http.HttpStatus;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.exception.SifBrokerException;

import java.util.UUID;

/**
 * Exception thrown when attempting to create a duplicate ACL entry.
 * 
 * <p>This exception is thrown when trying to create an ACL entry with
 * a combination of environment ID, service name, contextUuid,
 * and access type that already exists in the system.</p>
 * 
 * <p>Returns HTTP 409 Conflict status code.</p>
 * 
 * @see org.sifunity.broker.environment.service.AclService#createAclEntry
 */
public class DuplicateAclEntryException extends SifBrokerException {
    
    private static final String ERROR_CODE = "ACL_002";
    
    /**
     * Creates a new DuplicateAclEntryException for a specific ACL entry combination.
     * 
     * @param environmentId the environment ID
     * @param serviceName the service name
     * @param contextUuid the context UUID (may be null for wildcard)
     * @param accessType the access type
     */
    public DuplicateAclEntryException(UUID environmentId, String serviceName,
                                     UUID contextUuid, AccessType accessType) {
        super(ERROR_CODE, HttpStatus.CONFLICT,
              String.format("ACL entry already exists for environment %s, service %s, contextUuid %s, access type %s",
                          environmentId, serviceName,
                          contextUuid != null ? contextUuid : "null (wildcard)", accessType));
    }
}
