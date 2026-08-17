// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an environment attempts to access a zone it is not
 * authorized to access. This exception maps to HTTP 403 Forbidden status.
 * 
 * <p>Common scenarios:
 * <ul>
 *   <li>Environment attempts to access a service in a zone not in its ACL</li>
 *   <li>Environment attempts to create a queue in an unauthorized zone</li>
 *   <li>Environment attempts to subscribe to events in a restricted zone</li>
 *   <li>Zone-level access control policy denies access</li>
 * </ul>
 * 
 * <p>This exception is specific to zone-based access control, which is a key
 * concept in SIF 3.7 specification. Zones provide logical grouping and isolation
 * of services and data within the broker.
 * 
 * @see SifBrokerException
 * @see InsufficientPermissionsException
 */
public class ZoneAccessDeniedException extends SifBrokerException {
    
    /**
     * Constructs a new ZoneAccessDeniedException with the specified message.
     * The error code is set to "AUTHZ_002" and HTTP status to 403 Forbidden.
     * 
     * @param message the detail message explaining which zone access was denied
     */
    public ZoneAccessDeniedException(String message) {
        super("AUTHZ_002", HttpStatus.FORBIDDEN, message);
    }
}
