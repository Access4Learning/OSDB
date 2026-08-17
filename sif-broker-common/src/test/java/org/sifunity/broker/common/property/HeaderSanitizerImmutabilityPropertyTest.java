// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.config.HeaderBlacklistProperties;
import org.sifunity.broker.common.util.HeaderSanitizer;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 7: Sanitizer does not mutate input.
 *
 * <p>For any input header map, calling {@link HeaderSanitizer#sanitize(Map)}
 * should leave the original map unchanged (same size, same entries).</p>
 *
 * <p><strong>Validates: Requirements 3.4</strong></p>
 */
class HeaderSanitizerImmutabilityPropertyTest {

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

    // ========== Properties ==========

    /**
     * For any input header map, calling {@code sanitize()} should not
     * modify the original map — it must still equal a snapshot taken
     * before the call.
     */
    @Property(tries = 100)
    @Tag("Feature: header-passthrough-and-sanitization, Property 7: Sanitizer does not mutate input")
    void sanitizeDoesNotMutateInputMap(
            @ForAll("headerMaps") Map<String, String> inputHeaders) {

        // Construct HeaderBlacklistProperties with default config (no custom entries)
        HeaderBlacklistProperties props = new HeaderBlacklistProperties();
        HeaderSanitizer sanitizer = new HeaderSanitizer(props);

        // Take a deep copy (snapshot) of the input map before calling sanitize
        Map<String, String> snapshot = new LinkedHashMap<>(inputHeaders);

        // Call sanitize on the original map
        sanitizer.sanitize(inputHeaders);

        // Assert the original map still equals the snapshot
        assertThat(inputHeaders)
                .as("sanitize() must not mutate the input map")
                .hasSize(snapshot.size())
                .containsExactlyInAnyOrderEntriesOf(snapshot);
    }
}
