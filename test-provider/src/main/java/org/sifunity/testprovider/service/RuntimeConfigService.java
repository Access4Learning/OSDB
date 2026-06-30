// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.service;

import org.sifunity.testprovider.dto.ConfigureRequest;
import org.sifunity.testprovider.dto.ProviderConfigView;
import org.sifunity.testprovider.dto.ProviderStatusView;
import org.sifunity.testprovider.exception.ValidationException;

/**
 * The single component permitted to mutate {@link org.sifunity.testprovider.config.ProviderConfig}.
 *
 * <p>Validates configure requests, applies them atomically (nothing is mutated when validation fails),
 * masks secrets on every read path, and notifies the poll runner of changes so a stale ACK is reset when
 * the {@code queueId} changes. All operations are thread-safe: a configure call on the web thread and
 * reads on a poll-trigger thread run concurrently.</p>
 */
public interface RuntimeConfigService {

    /**
     * Return the current configuration with {@code sessionToken} and {@code sharedSecret} masked.
     *
     * @return a masked, side-effect-free view of the live configuration
     */
    ProviderConfigView getMaskedConfig();

    /**
     * Return a masked inspection view of the live provider state for {@code GET /test-control/status}.
     *
     * <p>Reports whether a complete credential set is present ({@code configured}) and whether delayed
     * polling is enabled ({@code delayedPollingEnabled}: a complete credential set plus a non-blank
     * {@code queueId}). {@code sessionToken} and {@code sharedSecret} are masked, and {@code queueId} is
     * surfaced only as a boolean configured state. Has no side effects.</p>
     *
     * @return a masked, side-effect-free status view of the live configuration
     */
    ProviderStatusView getStatus();

    /**
     * Apply a configure request.
     *
     * <p>Requires {@code sessionToken}, {@code sharedSecret}, {@code domainId}, and {@code solutionId} to be
     * present and non-blank; {@code gatewayUrl} is validated for syntax when present and retained when
     * omitted; {@code queueId} is optional (blank/absent disables delayed polling). Validation runs before
     * any mutation, so the configuration is left unchanged if validation fails.</p>
     *
     * @param request the configure request (non-null; Spring guarantees a deserialized body)
     * @return the updated configuration, masked, with {@code configured = true}
     * @throws ValidationException if a required field is missing/blank or {@code gatewayUrl} is invalid
     */
    ProviderConfigView configure(ConfigureRequest request);
}
