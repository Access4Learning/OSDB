// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.dto;

import jakarta.validation.Valid;
import org.sifunity.broker.common.dto.SubscriptionDTO;

/**
 * Request wrapper for subscription creation.
 *
 * <p>Wraps the subscription creation body under a {@code subscription} key,
 * matching the SIF 3.7 request envelope pattern.</p>
 *
 * @param subscription the subscription creation body
 * @see SubscriptionDTO
 */
public record SubscriptionCreateRequest(
    @Valid SubscriptionDTO subscription
) {}
