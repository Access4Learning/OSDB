// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

/**
 * Enumeration of environment types in the SIF Unity Broker.
 * 
 * <p>Maps to the SIF 3.7 {@code environmentTypeType} enumeration. The environment
 * type describes the topology of the environment — whether the consumer communicates
 * directly with providers or through a broker.</p>
 * 
 * <p>Note: Consumer/provider roles are expressed through ACL rights (AccessType),
 * not through the environment type. An environment of either type can hold any
 * combination of access rights.</p>
 * 
 * <p>Conforms to SIF 3.7 specification for environment classification.</p>
 * 
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public enum EnvironmentType {
    /**
     * Direct environment.
     * The consumer communicates directly with service providers without
     * broker intermediation for request/response operations.
     */
    DIRECT,

    /**
     * Brokered environment.
     * All communication between consumers and providers is mediated
     * by the broker infrastructure.
     */
    BROKERED
}
