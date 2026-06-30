// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.service;

import org.sifunity.testprovider.config.ConfigSnapshot;

/**
 * Notification collaborator invoked by {@link RuntimeConfigService} after a successful configure call,
 * so the on-demand poll runner can react to configuration changes (chiefly resetting a stale pending ACK
 * when the {@code queueId} changes).
 *
 * <p><strong>Decoupling note:</strong> the concrete poll runner ({@code DelayedPollRunner}) is built in a
 * parallel task. To avoid a hard compile-time dependency and an initialization race, the service depends
 * only on this small interface and resolves it lazily ({@code Optional}). The poll runner implements this
 * interface when it is introduced; until then the service operates with no notifier and the call is a
 * no-op.</p>
 */
public interface PollRunnerNotifier {

    /**
     * React to a configuration change. Implementations should reset any pending ACK when the
     * {@code queueId} differs between {@code before} and {@code after}, so a stale ACK is never replayed
     * against a different queue.
     *
     * @param before snapshot captured immediately before the change was applied
     * @param after  snapshot captured immediately after the change was applied
     */
    void onConfigChanged(ConfigSnapshot before, ConfigSnapshot after);
}
