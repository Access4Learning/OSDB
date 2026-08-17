// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

/**
 * Enumeration of CRUD operations that a provider supports for a given request type.
 * 
 * <p>Maps to the SIF 3.7 {@code operation} values defined in the
 * {@code supportedRequest} schema object. Each operation represents a
 * specific action a provider can perform on a service.</p>
 * 
 * <p>Conforms to SIF 3.7 specification for operation classification.</p>
 * 
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public enum Operation {
    /**
     * Create operation.
     * The provider supports creating new resources.
     */
    CREATE,
    
    /**
     * Query operation.
     * The provider supports querying/reading resources.
     */
    QUERY,
    
    /**
     * Update operation.
     * The provider supports updating existing resources.
     */
    UPDATE,
    
    /**
     * Delete operation.
     * The provider supports deleting resources.
     */
    DELETE
}
