// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.dto;

/**
 * Response body for {@code POST /test-control/configure}.
 *
 * <p>A read model that confirms the applied runtime configuration without ever exposing raw
 * secret values. {@code sessionTokenMasked} and {@code sharedSecretMasked} carry only masked
 * representations (e.g. {@code "abcd****wxyz"} or {@code "(not set)"}); the {@code queueId} is
 * surfaced only as a boolean {@code queueIdConfigured} state. {@code delayedPollingEnabled} is
 * derived: true exactly when a complete credential set is present and {@code queueId} is
 * non-blank.</p>
 */
public record ProviderConfigView(
        boolean configured,
        String gatewayUrl,
        String sessionTokenMasked,
        String sharedSecretMasked,
        String domainId,
        String solutionId,
        boolean queueIdConfigured,
        int port,
        boolean delayedPollingEnabled
) {
}
