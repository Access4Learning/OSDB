// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

/**
 * Enumeration of actions that can be applied to a code item within a Code Set.
 * 
 * <p>Maps to the SIF 3.7 code item {@code action} field. Each value classifies
 * the last action taken on a code item entry in the Code Sets Registry.</p>
 * 
 * <p>Conforms to SIF 3.7 specification for code item action classification.</p>
 * 
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public enum CodeItemAction {
    /** A new code item has been added to the code set. */
    ADD,

    /** An existing code item has been modified. */
    CHANGE,

    /** A code item has been marked as deprecated but not yet removed. */
    DEPRECATED,

    /** A code item has been removed from the code set. */
    DELETE
}
