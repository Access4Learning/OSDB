// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.config.HeaderBlacklistProperties;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HeaderSanitizer}.
 *
 * Validates Requirements 2.1, 2.4, 3.1, 3.2, 8.3.
 */
class HeaderSanitizerTest {

    private HeaderSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        HeaderBlacklistProperties props = new HeaderBlacklistProperties();
        sanitizer = new HeaderSanitizer(props);
    }

    @Test
    void emptyInputReturnsEmptyMap() {
        Map<String, String> result = sanitizer.sanitize(Map.of());
        assertThat(result).isEmpty();
    }

    @Test
    void nullInputReturnsEmptyMap() {
        Map<String, String> result = sanitizer.sanitize((Map<String, String>) null);
        assertThat(result).isEmpty();
    }

    @Test
    void allBlacklistedInputReturnsEmptyMap() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("Authorization", "Bearer token");
        input.put("fingerprint", "abc123");

        Map<String, String> result = sanitizer.sanitize(input);
        assertThat(result).isEmpty();
    }

    @Test
    void defaultBlacklistEntriesAreStripped() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("Authorization", "Bearer token");
        input.put("timestamp", "2024-01-01T00:00:00Z");
        input.put("X-Environment-Id", "env-1");
        input.put("X-Environment-Name", "production");
        input.put("X-Correlation-Id", "corr-1");
        input.put("fingerprint", "fp-1");
        input.put("sharedSecret", "secret");
        input.put("x-vendor-name", "TestVendor");

        Map<String, String> result = sanitizer.sanitize(input);

        assertThat(result).doesNotContainKey("Authorization");
        assertThat(result).doesNotContainKey("timestamp");
        assertThat(result).doesNotContainKey("X-Environment-Id");
        assertThat(result).doesNotContainKey("X-Environment-Name");
        assertThat(result).doesNotContainKey("fingerprint");
        assertThat(result).doesNotContainKey("sharedSecret");
        // X-Correlation-Id is a custom tracing header — it passes through
        assertThat(result).containsEntry("X-Correlation-Id", "corr-1");
        assertThat(result).containsEntry("x-vendor-name", "TestVendor");
    }

    @Test
    void mixedCaseBlacklistMatchingWorks() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("AUTHORIZATION", "Bearer token");
        input.put("Fingerprint", "fp-1");
        input.put("TIMESTAMP", "2024-01-01");
        input.put("x-vendor-name", "TestVendor");

        Map<String, String> result = sanitizer.sanitize(input);

        assertThat(result).doesNotContainKey("AUTHORIZATION");
        assertThat(result).doesNotContainKey("Fingerprint");
        assertThat(result).doesNotContainKey("TIMESTAMP");
        assertThat(result).containsEntry("x-vendor-name", "TestVendor");
    }

    @Test
    void nonBlacklistedHeadersArePreserved() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("x-vendor-name", "TestVendor");
        input.put("debugToken", "abc123");
        input.put("x-custom-header", "customValue");

        Map<String, String> result = sanitizer.sanitize(input);

        assertThat(result).containsEntry("x-vendor-name", "TestVendor");
        assertThat(result).containsEntry("debugToken", "abc123");
        assertThat(result).containsEntry("x-custom-header", "customValue");
        assertThat(result).hasSize(3);
    }

    @Test
    void structuredSifHeadersAreExcluded() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("messageId", "msg-1");
        input.put("serviceName", "StudentPersonal");
        input.put("messageType", "EVENT");
        input.put("serviceType", "OBJECT");
        input.put("zoneId", "zone-1");
        input.put("contextId", "ctx-1");
        input.put("requestId", "req-1");
        input.put("queueId", "queue-1");
        input.put("sourceId", "src-1");
        input.put("x-vendor-name", "TestVendor");

        Map<String, String> result = sanitizer.sanitize(input);

        assertThat(result).doesNotContainKey("messageId");
        assertThat(result).doesNotContainKey("serviceName");
        assertThat(result).doesNotContainKey("messageType");
        assertThat(result).doesNotContainKey("serviceType");
        assertThat(result).doesNotContainKey("zoneId");
        assertThat(result).doesNotContainKey("contextId");
        assertThat(result).doesNotContainKey("requestId");
        assertThat(result).doesNotContainKey("queueId");
        assertThat(result).doesNotContainKey("sourceId");
        assertThat(result).containsEntry("x-vendor-name", "TestVendor");
        assertThat(result).hasSize(1);
    }
}
