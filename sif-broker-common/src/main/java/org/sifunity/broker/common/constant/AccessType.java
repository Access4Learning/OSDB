// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

/**
 * Enumeration of access right types for ACL (Access Control List) entries.
 * 
 * <p>Maps to the SIF 3.7 {@code rightTypeType} enumeration. Each right type
 * represents a specific operation that can be granted or denied for a service
 * within a zone/context.</p>
 * 
 * <p>Conforms to SIF 3.7 specification for access control.</p>
 * 
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public enum AccessType {
    /**
     * Query (read) access to a service.
     * The environment can issue GET/query requests against the service.
     */
    QUERY,

    /**
     * Create access to a service.
     * The environment can issue POST/create requests against the service.
     */
    CREATE,

    /**
     * Update access to a service.
     * The environment can issue PUT/update requests against the service.
     */
    UPDATE,

    /**
     * Delete access to a service.
     * The environment can issue DELETE requests against the service.
     */
    DELETE,

    /**
     * Subscribe to events from a service.
     * The environment can subscribe to event notifications published by the service provider.
     */
    SUBSCRIBE,

    /**
     * Provide a service.
     * The environment can register as a provider of this service.
     */
    PROVIDE
}
