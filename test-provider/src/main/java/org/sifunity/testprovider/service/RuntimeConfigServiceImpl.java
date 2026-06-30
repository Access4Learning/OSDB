// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.service;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.testprovider.config.ConfigSnapshot;
import org.sifunity.testprovider.config.ProviderConfig;
import org.sifunity.testprovider.dto.ConfigureRequest;
import org.sifunity.testprovider.dto.ProviderConfigView;
import org.sifunity.testprovider.dto.ProviderStatusView;
import org.sifunity.testprovider.exception.ValidationException;
import org.sifunity.testprovider.util.SecretMasker;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Default {@link RuntimeConfigService} implementation backed by the live {@link ProviderConfig} singleton.
 *
 * <p>All mutation happens under a {@link ReentrantLock} ({@code configLock}) so a configure call on the web
 * thread and field reads on a poll-trigger thread observe consistent values. Validation runs before the
 * lock is acquired and before any mutation, guaranteeing atomicity with respect to validation: if a request
 * is invalid the configuration is untouched.</p>
 *
 * <p>The poll runner is decoupled via the small {@link PollRunnerNotifier} interface, injected lazily as an
 * {@link Optional}. The concrete {@code DelayedPollRunner} (built in a parallel task) implements that
 * interface; until it exists the notifier is absent and the change callback is skipped. The callback is
 * invoked <em>after</em> the lock is released.</p>
 */
@Service
@Slf4j
public class RuntimeConfigServiceImpl implements RuntimeConfigService {

    private final ProviderConfig config;
    private final Optional<PollRunnerNotifier> pollRunnerNotifier;
    private final ReentrantLock configLock = new ReentrantLock();

    /**
     * @param config             the live provider configuration store
     * @param pollRunnerNotifier the poll runner notification collaborator, if present
     */
    public RuntimeConfigServiceImpl(ProviderConfig config, Optional<PollRunnerNotifier> pollRunnerNotifier) {
        this.config = config;
        this.pollRunnerNotifier = pollRunnerNotifier;
    }

    @Override
    public ProviderConfigView getMaskedConfig() {
        // Snapshot under the lock so we never observe a torn write while a configure is in progress.
        ConfigSnapshot snapshot;
        configLock.lock();
        try {
            snapshot = config.snapshot();
        } finally {
            configLock.unlock();
        }
        return toMaskedView(snapshot, snapshot.hasCompleteCredentials());
    }

    @Override
    public ProviderStatusView getStatus() {
        // Snapshot under the lock so we never observe a torn write while a configure is in progress.
        ConfigSnapshot snapshot;
        configLock.lock();
        try {
            snapshot = config.snapshot();
        } finally {
            configLock.unlock();
        }
        return new ProviderStatusView(
                snapshot.hasCompleteCredentials(),
                snapshot.delayedPollingEnabled(),
                snapshot.gatewayUrl(),
                SecretMasker.mask(snapshot.sessionToken()),
                SecretMasker.mask(snapshot.sharedSecret()),
                snapshot.domainId(),
                snapshot.solutionId(),
                StringUtils.hasText(snapshot.queueId()));
    }

    @Override
    public ProviderConfigView configure(ConfigureRequest request) {
        // Step 1: validate without mutating. Throws before the lock is acquired.
        validate(request);

        // Step 2-3: apply values under the lock, capturing before/after snapshots for change detection.
        ConfigSnapshot before;
        ConfigSnapshot after;
        configLock.lock();
        try {
            before = config.snapshot();

            // gatewayUrl retained when omitted; otherwise trimmed and stored.
            if (StringUtils.hasText(request.gatewayUrl())) {
                config.setGatewayUrl(request.gatewayUrl().trim());
            }
            config.setSessionToken(request.sessionToken());
            config.setSharedSecret(request.sharedSecret());
            config.setDomainId(request.domainId().trim());
            config.setSolutionId(request.solutionId().trim());
            config.setQueueId(emptyToNull(request.queueId()));

            after = config.snapshot();
        } finally {
            configLock.unlock();
        }

        // Step 4: notify the runner outside the lock so a stale ACK is reset on queue change.
        pollRunnerNotifier.ifPresent(notifier -> notifier.onConfigChanged(before, after));

        log.info("Runtime configuration applied: domainId={}, solutionId={}, gatewayUrl={}, queueIdConfigured={}, delayedPollingEnabled={}",
                after.domainId(), after.solutionId(), after.gatewayUrl(),
                StringUtils.hasText(after.queueId()), after.delayedPollingEnabled());

        // Step 5: return masked view with configured = true.
        return toMaskedView(after, true);
    }

    /**
     * Validate the request, collecting a per-field error for each problem. Required fields must be non-blank;
     * {@code gatewayUrl}, when present, must be a syntactically valid http/https URL.
     *
     * @param request the request to validate
     * @throws ValidationException if any field is invalid
     */
    private void validate(ConfigureRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (!StringUtils.hasText(request.sessionToken())) {
            errors.put("sessionToken", "is required");
        }
        if (!StringUtils.hasText(request.sharedSecret())) {
            errors.put("sharedSecret", "is required");
        }
        if (!StringUtils.hasText(request.domainId())) {
            errors.put("domainId", "is required");
        }
        if (!StringUtils.hasText(request.solutionId())) {
            errors.put("solutionId", "is required");
        }
        if (request.gatewayUrl() != null && !isValidHttpUrl(request.gatewayUrl())) {
            errors.put("gatewayUrl", "must be a valid http/https URL");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /**
     * Whether the given string is a syntactically valid absolute http/https URL with a host.
     *
     * @param value the candidate URL (may have surrounding whitespace)
     * @return {@code true} when the trimmed value parses as an http/https URI with a host
     */
    private boolean isValidHttpUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            return scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && StringUtils.hasText(uri.getHost());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Normalize a queueId: trim, and treat blank/absent as {@code null} (delayed polling disabled).
     *
     * @param value the raw queueId from the request
     * @return the trimmed queueId, or {@code null} when blank/absent
     */
    private String emptyToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * Build a masked view from a snapshot. Never carries raw secret values.
     *
     * @param snapshot   the snapshot to render
     * @param configured the {@code configured} flag to surface
     * @return a masked {@link ProviderConfigView}
     */
    private ProviderConfigView toMaskedView(ConfigSnapshot snapshot, boolean configured) {
        return new ProviderConfigView(
                configured,
                snapshot.gatewayUrl(),
                SecretMasker.mask(snapshot.sessionToken()),
                SecretMasker.mask(snapshot.sharedSecret()),
                snapshot.domainId(),
                snapshot.solutionId(),
                StringUtils.hasText(snapshot.queueId()),
                snapshot.port(),
                snapshot.delayedPollingEnabled());
    }
}
