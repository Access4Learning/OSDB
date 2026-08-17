// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

/**
 * Enumeration of message types in the SIF Unity Broker.
 * 
 * <p>Message types classify the purpose of a message in the broker system.</p>
 * 
 * <p>Conforms to SIF 3.7 specification for message classification.</p>
 * 
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public enum MessageType {
    /**
     * Request message.
     * A message requesting data or an action from a provider.
     */
    REQUEST,
    
    /**
     * Response message.
     * A message containing the response to a previous request.
     */
    RESPONSE,
    
    /**
     * Event notification.
     * A message notifying subscribers of an event occurrence.
     */
    EVENT
}
