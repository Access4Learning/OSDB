// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.config.HeaderBlacklistProperties;
import org.sifunity.broker.common.util.HeaderSanitizer;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 5: Sanitizer preserves all non-blacklisted, non-SIF-structured headers.
 *
 * <p>For any input header map and for any blacklist configuration, after calling
 * {@link HeaderSanitizer#sanitize(Map)}, every header whose name is not on the
 * effective blacklist and is not a structured SIF header should appear in the
 * output with its original value unchanged.</p>
 *
 * <p><strong>Validates: Requirements 3.2, 8.2</strong></p>
 */
class HeaderSanitizerPreservationPropertyTest {

    /**
     * Case-insensitive set of structured SIF header names that the sanitizer
     * excludes from passthrough (mirrors {@code HeaderSanitizer.STRUCTURED_SIF_HEADERS}).
     */
    private static final Set<String> STRUCTURED_SIF_HEADERS;

    static {
        TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.addAll(List.of(
                "messageid", "messagetype", "servicename", "servicetype",
                "zoneid", "contextid", "requestid", "queueid", "sourceid"
        ));
        STRUCTURED_SIF_HEADERS = Collections.unmodifiableSet(set);
    }

    // ========== Arbitraries ==========

    /**
     * Generates arbitrary header maps with alpha keys and ASCII values.
     */
    @Provide
    Arbitrary<Map<String, String>> headerMaps() {
        Arbitrary<String> keys = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(30);
        Arbitrary<String> values = Arbitraries.strings()
                .ascii()
                .ofMinLength(1)
                .ofMaxLength(50);
        return Arbitraries.maps(keys, values)
                .ofMinSize(0)
                .ofMaxSize(20);
    }

    /**
     * Generates arbitrary custom blacklist sets with alpha header names.
     */
    @Provide
    Arbitrary<Set<String>> customBlacklistSets() {
        Arbitrary<String> headerNames = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(30);
        return headerNames.set().ofMinSize(0).ofMaxSize(15);
    }

    // ========== Properties ==========

    /**
     * For any input header map and any custom blacklist configuration,
     * every input header whose name is NOT in the effective blacklist
     * and NOT a structured SIF header should appear in the sanitized
     * output with its original value.
     */
    @Property(tries = 100)
    @Tag("Feature: header-passthrough-and-sanitization, Property 5: Sanitizer preserves all non-blacklisted, non-SIF-structured headers")
    void sanitizedOutputPreservesAllNonBlacklistedNonSifHeaders(
            @ForAll("headerMaps") Map<String, String> inputHeaders,
            @ForAll("customBlacklistSets") Set<String> customBlacklist) {

        // Construct HeaderBlacklistProperties with the custom blacklist
        HeaderBlacklistProperties props = new HeaderBlacklistProperties();
        props.setBlacklist(new LinkedHashSet<>(customBlacklist));

        // Construct HeaderSanitizer
        HeaderSanitizer sanitizer = new HeaderSanitizer(props);

        // Get the effective blacklist (defaults + custom, case-insensitive)
        Set<String> effectiveBlacklist = props.getEffectiveBlacklist();

        // Sanitize the input
        Map<String, String> result = sanitizer.sanitize(new LinkedHashMap<>(inputHeaders));

        // Assert every non-blacklisted, non-SIF-structured input header is preserved
        for (Map.Entry<String, String> entry : inputHeaders.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key == null || value == null) {
                continue;
            }

            boolean isBlacklisted = effectiveBlacklist.contains(key);
            boolean isSifStructured = STRUCTURED_SIF_HEADERS.contains(key);

            if (!isBlacklisted && !isSifStructured) {
                assertThat(result)
                        .as("Non-blacklisted, non-SIF header '%s' should be preserved in output", key)
                        .containsKey(key);
                assertThat(result.get(key))
                        .as("Value of preserved header '%s' should be unchanged", key)
                        .isEqualTo(value);
            }
        }
    }
}
