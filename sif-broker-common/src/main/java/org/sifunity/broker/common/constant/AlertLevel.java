// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

/**
 * Enumeration of alert severity levels for the Alerts Utility Service.
 * 
 * <p>Maps to the SIF 3.7 alert {@code level} field. Each value
 * represents the severity or nature of the condition being reported.</p>
 * 
 * <p>Conforms to SIF 3.7 specification for alert severity levels.</p>
 * 
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public enum AlertLevel {
    /** Informational alert requiring no action. */
    INFO,

    /** Alert indicating a state change in the reporting application. */
    STATECHANGE,

    /** Alert related to an event processing condition. */
    EVENT,

    /** Warning condition that may require attention. */
    WARNING,

    /** Error condition requiring immediate attention. */
    ERROR
}
