// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.dto;

/**
 * Response body for {@code POST /test-control/poll}.
 *
 * <p>Reports the outcome of an on-demand DELAYED poll cycle. {@code polled} is true when a poll
 * cycle actually ran (a complete credential set is present and {@code queueId} is non-blank);
 * {@code messageProcessed} is true when a message was found and a response was submitted during
 * the cycle. Carries no secret values.</p>
 */
public record PollResult(
        boolean polled,
        boolean messageProcessed
) {
}
