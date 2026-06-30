// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.config;

import org.springframework.util.StringUtils;

/**
 * Immutable point-in-time copy of all {@link ProviderConfig} fields, including raw secrets.
 *
 * <p>This record is used internally for change detection (before/after a configure call) and as the
 * source for masked reads. It carries raw secret values and therefore <strong>must never be serialized
 * directly to an HTTP response</strong>; views with masked fields are returned to callers instead.</p>
 *
 * @param gatewayUrl   broker gateway URL
 * @param sessionToken raw session token (secret — never serialized)
 * @param sharedSecret raw HMAC shared secret (secret — never serialized)
 * @param domainId     domain ID this provider operates in
 * @param solutionId   solution ID this provider operates in
 * @param queueId      queue ID for delayed polling, or {@code null}/blank when disabled
 * @param port         port this provider listens on
 */
public record ConfigSnapshot(
        String gatewayUrl,
        String sessionToken,
        String sharedSecret,
        String domainId,
        String solutionId,
        String queueId,
        int port) {

    /**
     * Whether this snapshot holds a full credential set ({@code queueId} not required).
     *
     * @return {@code true} when {@code sessionToken}, {@code sharedSecret}, {@code domainId}, and
     *         {@code solutionId} are all non-blank
     */
    public boolean hasCompleteCredentials() {
        return StringUtils.hasText(sessionToken)
                && StringUtils.hasText(sharedSecret)
                && StringUtils.hasText(domainId)
                && StringUtils.hasText(solutionId);
    }

    /**
     * Whether delayed polling is enabled for this snapshot: a complete credential set plus a non-blank
     * {@code queueId}.
     *
     * @return {@code true} when credentials are complete and {@code queueId} is non-blank
     */
    public boolean delayedPollingEnabled() {
        return hasCompleteCredentials() && StringUtils.hasText(queueId);
    }
}
