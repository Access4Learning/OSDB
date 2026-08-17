// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

/**
 * Enumeration of alert exchange classifications for the Alerts Utility Service.
 * 
 * <p>Maps to the SIF 3.7 alert {@code exchange} field. Each value
 * identifies the type of message exchange that generated the alert.</p>
 * 
 * <p>Conforms to SIF 3.7 specification for alert exchange classification.</p>
 * 
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public enum Exchange {
    /** Alert generated during a request exchange. */
    REQUEST,

    /** Alert generated during a response exchange. */
    RESPONSE,

    /** Alert generated during an event exchange. */
    EVENT,

    /** Alert generated due to a timeout condition. */
    TIMEOUT,

    /** Alert generated during an exchange not covered by other values. */
    OTHER
}
