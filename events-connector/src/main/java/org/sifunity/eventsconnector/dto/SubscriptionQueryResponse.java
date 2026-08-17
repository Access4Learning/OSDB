// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.sifunity.broker.common.dto.SubscriptionDTO;

import java.util.List;
import java.util.Map;

/**
 * DTO for deserializing the SIF collection response from Subscription Service.
 *
 * <p>The subscription-service returns SIF collection format:</p>
 * <pre>{@code
 * {
 *   "subscriptions": {
 *     "subscription": [ {...}, {...} ]
 *   }
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubscriptionQueryResponse(
    @jakarta.validation.Valid
    @JsonProperty("subscriptions")
    SubscriptionsWrapper subscriptions
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubscriptionsWrapper(
        @jakarta.validation.Valid
        @JsonProperty("subscription")
        List<SubscriptionDTO> subscription
    ) {
        public SubscriptionsWrapper {
            if (subscription == null) {
                subscription = List.of();
            }
        }
    }

    public SubscriptionQueryResponse {
        if (subscriptions == null) {
            subscriptions = new SubscriptionsWrapper(List.of());
        }
    }

    /**
     * Returns the flat list of subscriptions.
     */
    public List<SubscriptionDTO> asList() {
        return subscriptions.subscription();
    }

    public boolean isEmpty() {
        return subscriptions.subscription().isEmpty();
    }

    public int size() {
        return subscriptions.subscription().size();
    }

    public static SubscriptionQueryResponse empty() {
        return new SubscriptionQueryResponse(new SubscriptionsWrapper(List.of()));
    }

    /**
     * Creates a response from a flat list of subscriptions.
     * Convenience factory for tests and internal use.
     */
    public static SubscriptionQueryResponse of(List<SubscriptionDTO> subscriptions) {
        return new SubscriptionQueryResponse(new SubscriptionsWrapper(subscriptions != null ? subscriptions : List.of()));
    }
}
