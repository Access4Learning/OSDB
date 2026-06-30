// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.dto;

/**
 * Response body for {@code GET /test-control/status}.
 *
 * <p>A read model for inspecting the live provider state without exposing raw secret values.
 * {@code configured} reflects whether a complete credential set is present;
 * {@code delayedPollingEnabled} is true exactly when a complete credential set is present and
 * {@code queueId} is non-blank. {@code sessionTokenMasked} and {@code sharedSecretMasked} carry
 * only masked representations, and the {@code queueId} is surfaced only as the boolean
 * {@code queueIdConfigured} state.</p>
 */
public record ProviderStatusView(
        boolean configured,
        boolean delayedPollingEnabled,
        String gatewayUrl,
        String sessionTokenMasked,
        String sharedSecretMasked,
        String domainId,
        String solutionId,
        boolean queueIdConfigured
) {
}
