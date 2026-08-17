// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HeaderBlacklistProperties}.
 *
 * Validates Requirement 2.1: default blacklist contains the required security/transport headers.
 */
class HeaderBlacklistPropertiesTest {

    @Test
    void defaultBlacklistContainsAllRequiredHeaders() {
        HeaderBlacklistProperties props = new HeaderBlacklistProperties();
        Set<String> effective = props.getEffectiveBlacklist();

        // The effective blacklist is a case-insensitive TreeSet, so
        // contains() matches regardless of case.
        assertThat(effective.contains("Authorization")).isTrue();
        assertThat(effective.contains("timestamp")).isTrue();
        assertThat(effective.contains("X-Environment-Id")).isTrue();
        assertThat(effective.contains("X-Environment-Name")).isTrue();
        assertThat(effective.contains("fingerprint")).isTrue();
        assertThat(effective.contains("sharedSecret")).isTrue();
    }

    @Test
    void defaultBlacklistDoesNotContainCorrelationId() {
        HeaderBlacklistProperties props = new HeaderBlacklistProperties();
        Set<String> effective = props.getEffectiveBlacklist();

        // X-Correlation-Id is a custom tracing header — must NOT be blacklisted
        assertThat(effective.contains("X-Correlation-Id")).isFalse();
    }

    @Test
    void defaultBlacklistHasCorrectSize() {
        HeaderBlacklistProperties props = new HeaderBlacklistProperties();
        Set<String> effective = props.getEffectiveBlacklist();

        assertThat(effective).hasSize(HeaderBlacklistProperties.DEFAULTS.size());
    }

    @Test
    void emptyCustomConfigStillReturnsAllDefaults() {
        HeaderBlacklistProperties props = new HeaderBlacklistProperties();
        Set<String> effective = props.getEffectiveBlacklist();

        assertThat(effective.contains("Authorization")).isTrue();
        assertThat(effective.contains("timestamp")).isTrue();
        assertThat(effective.contains("X-Environment-Id")).isTrue();
        assertThat(effective.contains("X-Environment-Name")).isTrue();
        assertThat(effective.contains("fingerprint")).isTrue();
        assertThat(effective.contains("sharedSecret")).isTrue();
        // Also verify transport headers are included
        assertThat(effective.contains("content-length")).isTrue();
        assertThat(effective.contains("host")).isTrue();
        assertThat(effective.contains("accept")).isTrue();
        assertThat(effective).hasSize(HeaderBlacklistProperties.DEFAULTS.size());
    }
}
