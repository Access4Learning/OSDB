// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

/**
 * Enumeration of subscription types in the SIF Unity Broker.
 * 
 * <p>Subscription types define what kind of messages a queue subscription
 * will receive.</p>
 * 
 * <p>Conforms to SIF 3.7 specification for subscription management.</p>
 * 
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
public enum SubscriptionType {
    /**
     * Event subscription.
     * The subscription receives event notifications published by providers.
     */
    EVENT,
    
    /**
     * DELAYED request subscription (for Providers).
     * The subscription receives DELAYED pattern requests that the provider
     * should process and respond to.
     */
    DELAYED_REQUEST
}
