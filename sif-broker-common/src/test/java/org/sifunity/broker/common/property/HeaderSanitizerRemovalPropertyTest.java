// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.config.HeaderBlacklistProperties;
import org.sifunity.broker.common.util.HeaderSanitizer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 4: Sanitizer removes all blacklisted headers.
 *
 * <p>For any input header map and for any blacklist configuration, after calling
 * {@link HeaderSanitizer#sanitize(Map)}, no key in the output map should match
 * (case-insensitively) any entry in the effective blacklist.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.3, 9.1, 9.2</strong></p>
 */
class HeaderSanitizerRemovalPropertyTest {

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
     * no key in the sanitized output should match (case-insensitive)
     * any entry in the effective blacklist.
     */
    @Property(tries = 100)
    @Tag("Feature: header-passthrough-and-sanitization, Property 4: Sanitizer removes all blacklisted headers")
    void sanitizedOutputContainsNoBlacklistedHeaders(
            @ForAll("headerMaps") Map<String, String> inputHeaders,
            @ForAll("customBlacklistSets") Set<String> customBlacklist) {

        // Construct HeaderBlacklistProperties with the custom blacklist
        HeaderBlacklistProperties props = new HeaderBlacklistProperties();
        props.setBlacklist(new LinkedHashSet<>(customBlacklist));

        // Construct HeaderSanitizer
        HeaderSanitizer sanitizer = new HeaderSanitizer(props);

        // Get the effective blacklist (defaults + custom)
        Set<String> effectiveBlacklist = props.getEffectiveBlacklist();

        // Sanitize the input
        Map<String, String> result = sanitizer.sanitize(new LinkedHashMap<>(inputHeaders));

        // Assert no key in the output matches any blacklist entry (case-insensitive)
        for (String outputKey : result.keySet()) {
            assertThat(effectiveBlacklist.contains(outputKey))
                    .as("Output key '%s' should not match any entry in the effective blacklist",
                            outputKey)
                    .isFalse();
        }
    }
}
