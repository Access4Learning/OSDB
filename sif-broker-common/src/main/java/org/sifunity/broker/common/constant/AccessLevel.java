// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

/**
 * Enumeration of access right values for ACL (Access Control List) entries.
 * 
 * <p>Maps to the SIF 3.7 {@code rightValueType} enumeration. Each value
 * represents the status of a particular access right for a service.</p>
 * 
 * <p>Conforms to SIF 3.7 specification for access control.</p>
 * 
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public enum AccessLevel {
    /**
     * Permission granted.
     * The environment is authorized to perform the requested access type.
     */
    APPROVED,

    /**
     * Capability supported.
     * The provider supports this operation (used in provider-side provisioning).
     */
    SUPPORTED,

    /**
     * Permission denied.
     * The environment is not authorized to perform the requested access type.
     */
    REJECTED,

    /**
     * Capability not supported.
     * The provider does not support this operation.
     */
    UNSUPPORTED,

    /**
     * Awaiting approval.
     * The access request has been submitted and is pending review by an administrator.
     */
    REQUESTED,

    /**
     * Pending approval.
     * Used when a non-admin environment self-provisions an ACL entry.
     * The entry awaits admin review before becoming active.
     */
    PENDING
}
