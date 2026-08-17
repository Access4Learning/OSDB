// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an authenticated environment lacks the required permissions
 * to perform a requested operation. This exception maps to HTTP 403 Forbidden status.
 * 
 * <p>Common scenarios:
 * <ul>
 *   <li>Environment attempts to access a service without PROVIDE/CONSUME permission</li>
 *   <li>Environment attempts to subscribe to events without SUBSCRIBE permission</li>
 *   <li>ACL entry exists but has REJECTED or PENDING status</li>
 *   <li>Environment attempts to perform an operation outside its granted access type</li>
 * </ul>
 * 
 * <p>This exception indicates that the request was authenticated successfully,
 * but the authenticated environment does not have authorization to perform the
 * requested action. This is distinct from authentication failures (HTTP 401).
 * 
 * @see SifBrokerException
 * @see ZoneAccessDeniedException
 */
public class InsufficientPermissionsException extends SifBrokerException {
    
    /**
     * Constructs a new InsufficientPermissionsException with the specified message.
     * The error code is set to "AUTHZ_001" and HTTP status to 403 Forbidden.
     * 
     * @param message the detail message explaining what permission is missing
     */
    public InsufficientPermissionsException(String message) {
        super("AUTHZ_001", HttpStatus.FORBIDDEN, message);
    }
}
