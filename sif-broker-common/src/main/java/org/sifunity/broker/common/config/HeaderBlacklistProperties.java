// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Configuration properties for the header blacklist used by the Header Sanitizer.
 *
 * <p>Loads custom blacklist entries from {@code sif.headers.blacklist} in
 * {@code application.yml} and merges them with a hardcoded set of
 * security-sensitive defaults. The effective blacklist is always
 * case-insensitive.</p>
 *
 * <p>Example configuration:</p>
 * <pre>{@code
 * sif:
 *   headers:
 *     blacklist:
 *       - "X-Internal-Debug"
 * }</pre>
 *
 * @see HeaderBlacklistAutoConfiguration
 */
@ConfigurationProperties(prefix = "sif.headers")
public class HeaderBlacklistProperties {

    /**
     * Default header names that are always blacklisted (security-sensitive).
     * These are merged with any custom entries from configuration.
     */
    public static final Set<String> DEFAULTS = Set.of(
            // Security-sensitive
            "authorization",
            "timestamp",
            "x-environment-id",
            "x-environment-name",
            "fingerprint",
            "sharedsecret",
            "x-internal-service-token",
            // HTTP transport (must not be stored/forwarded through queues)
            "content-length",
            "content-type",
            "transfer-encoding",
            "connection",
            "host",
            "cookie",
            "set-cookie",
            "accept",
            "accept-encoding",
            "accept-language",
            "user-agent",
            "forwarded",
            "x-forwarded-for",
            "x-forwarded-host",
            "x-forwarded-port",
            "x-forwarded-proto",
            "x-forwarded-prefix",
            "connectionid",
            "request-start-time"
    );

    /**
     * Custom header names to strip, loaded from {@code sif.headers.blacklist}.
     * Merged with {@link #DEFAULTS} at runtime.
     */
    private Set<String> blacklist = new LinkedHashSet<>();

    public Set<String> getBlacklist() {
        return blacklist;
    }

    public void setBlacklist(Set<String> blacklist) {
        this.blacklist = blacklist;
    }

    /**
     * Returns the effective blacklist: the union of {@link #DEFAULTS} and any
     * custom entries, stored in a case-insensitive {@link TreeSet}.
     *
     * @return an unmodifiable, case-insensitive set of blacklisted header names
     */
    public Set<String> getEffectiveBlacklist() {
        TreeSet<String> merged = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        merged.addAll(DEFAULTS);
        merged.addAll(blacklist);
        return Collections.unmodifiableSet(merged);
    }
}
