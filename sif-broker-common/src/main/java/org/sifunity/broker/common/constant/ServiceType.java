// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

/**
 * Enumeration of service types in the SIF Unity Broker.
 * 
 * <p>Service types classify the category of service being provided or consumed.
 * Maps to the SIF 3.7 {@code serviceType} values defined in the provider schema.</p>
 * 
 * <p>Conforms to SIF 3.7 specification for service classification.</p>
 * 
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public enum ServiceType {
    /**
     * Utility service.
     * Infrastructure services (e.g., Environments, Queues, Subscriptions).
     */
    UTILITY,
    
    /**
     * Data object service.
     * Services that provide access to data objects (e.g., StudentPersonal, SchoolInfo).
     */
    OBJECT,
    
    /**
     * Functional service.
     * Services that perform operations or jobs (e.g., data processing, transformations).
     */
    FUNCTIONAL,
    
    /**
     * Service path service.
     * Services accessed via a hierarchical service path.
     */
    SERVICEPATH,
    
    /**
     * XQuery template service.
     * Services that support XQuery-based data retrieval templates.
     */
    XQUERYTEMPLATE,
    
    /**
     * Generic service.
     * A general-purpose service type.
     */
    SERVICE
}
