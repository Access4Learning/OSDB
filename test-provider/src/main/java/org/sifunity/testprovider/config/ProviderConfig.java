// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Configuration properties for the test provider.
 * Bound from the {@code provider.*} namespace in application.yml.
 *
 * <p>This bean is the single in-memory source of truth for all provider settings. It is bound once
 * from {@code application.yml}/environment variables at startup (so startup configuration keeps working)
 * and may be mutated at runtime by the {@code RuntimeConfigService}. Every component reads its live
 * values on each operation, so changes take effect on the next outbound call / triggered poll.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "provider")
@Getter
@Setter
public class ProviderConfig {

    /** Broker gateway URL (e.g. http://localhost:8080) */
    private String gatewayUrl = "http://localhost:8080";

    /** Session token obtained after environment registration */
    private String sessionToken;

    /** Shared secret obtained after environment registration */
    private String sharedSecret;

    /** Domain ID this provider operates in */
    private String domainId;

    /** Solution ID this provider operates in */
    private String solutionId;

    /** Queue ID for delayed request polling — if null, delayed polling is disabled */
    private String queueId;

    /** Port this provider listens on (mirrors server.port for banner display) */
    private int port = 9091;

    /**
     * Capture an immutable point-in-time copy of all settings (including raw secrets).
     * Used for change detection and as the source for masked reads. The snapshot is never serialized
     * directly to a response.
     *
     * @return an immutable {@link ConfigSnapshot} of the current field values
     */
    public ConfigSnapshot snapshot() {
        return new ConfigSnapshot(gatewayUrl, sessionToken, sharedSecret, domainId, solutionId, queueId, port);
    }

    /**
     * Whether a full credential set is present. The {@code queueId} is intentionally not required here —
     * complete credentials are needed for any authenticated outbound call, while {@code queueId} only
     * gates delayed polling.
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
}
