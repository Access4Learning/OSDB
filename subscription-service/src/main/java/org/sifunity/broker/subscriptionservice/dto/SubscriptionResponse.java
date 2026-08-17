// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.dto;

import org.sifunity.broker.common.dto.SubscriptionDTO;

/**
 * Response wrapper for a single subscription.
 *
 * <p>Wraps the subscription DTO under a {@code subscription} key per SIF 3.7 convention.</p>
 *
 * @param subscription the subscription data
 * @see SubscriptionDTO
 */
public record SubscriptionResponse(
    SubscriptionDTO subscription
) {}
