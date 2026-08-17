// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.config.HeaderBlacklistProperties;
import org.sifunity.broker.common.util.HeaderSanitizer;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 6: Sanitizer excludes structured SIF headers.
 *
 * <p>For any input header map that contains keys matching structured SIF header
 * names ({@code messageId}, {@code serviceName}, {@code serviceType},
 * {@code messageType}, {@code zoneId}, {@code contextId}, {@code requestId},
 * {@code queueId}, {@code sourceId}), the {@link HeaderSanitizer} should exclude
 * those keys from the output regardless of blacklist contents.</p>
 *
 * <p><strong>Validates: Requirements 8.3</strong></p>
 */
class HeaderSanitizerSifExclusionPropertyTest {

    /**
     * Canonical lowercase structured SIF header names used for generation.
     */
    private static final List<String> SIF_HEADER_NAMES = List.of(
            "messageid", "messagetype", "servicename", "servicetype",
            "zoneid", "contextid", "requestid", "queueid", "sourceid"
    );

    /**
     * Case-insensitive set of structured SIF header names for assertion checks.
     */
    private static final Set<String> STRUCTURED_SIF_HEADERS;

    static {
        TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.addAll(SIF_HEADER_NAMES);
        STRUCTURED_SIF_HEADERS = Collections.unmodifiableSet(set);
    }

    // ========== Arbitraries ==========

    /**
     * Generates a random case variation of the given string.
     */
    private static Arbitrary<String> randomCaseVariation(String name) {
        return Arbitraries.integers().between(0, 2).map(strategy -> {
            switch (strategy) {
                case 0: return name.toLowerCase();
                case 1: return name.toUpperCase();
                default:
                    // Mixed case: toggle each character randomly
                    StringBuilder sb = new StringBuilder(name.length());
                    for (char c : name.toCharArray()) {
                        sb.append(Character.isLetter(c) && (c % 2 == 0)
                                ? Character.toUpperCase(c)
                                : Character.toLowerCase(c));
                    }
                    return sb.toString();
            }
        });
    }

    /**
     * Generates header maps that always include at least one structured SIF
     * header name (with random case variation) plus some non-SIF headers.
     */
    @Provide
    Arbitrary<Map<String, String>> headerMapsWithSifHeaders() {
        // Generate 1-5 SIF headers with random case variations and random values
        Arbitrary<List<Map.Entry<String, String>>> sifEntries =
                Arbitraries.of(SIF_HEADER_NAMES)
                        .flatMap(name -> randomCaseVariation(name)
                                .flatMap(casedName -> Arbitraries.strings()
                                        .ascii()
                                        .ofMinLength(1)
                                        .ofMaxLength(50)
                                        .map(value -> Map.entry(casedName, value))))
                        .list().ofMinSize(1).ofMaxSize(5);

        // Generate 0-10 non-SIF headers
        Arbitrary<String> nonSifKeys = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(30)
                .filter(k -> !STRUCTURED_SIF_HEADERS.contains(k));
        Arbitrary<String> values = Arbitraries.strings()
                .ascii()
                .ofMinLength(1)
                .ofMaxLength(50);
        Arbitrary<Map<String, String>> nonSifMap = Arbitraries.maps(nonSifKeys, values)
                .ofMinSize(0)
                .ofMaxSize(10);

        // Combine SIF entries and non-SIF entries into a single map
        return Combinators.combine(sifEntries, nonSifMap).as((sifList, nonSif) -> {
            LinkedHashMap<String, String> combined = new LinkedHashMap<>(nonSif);
            for (Map.Entry<String, String> entry : sifList) {
                combined.put(entry.getKey(), entry.getValue());
            }
            return combined;
        });
    }

    // ========== Properties ==========

    /**
     * For any input header map that includes structured SIF header names
     * (in any case variation), after calling {@code sanitize()}, none of
     * the structured SIF header names should appear in the output.
     */
    @Property(tries = 100)
    @Tag("Feature: header-passthrough-and-sanitization, Property 6: Sanitizer excludes structured SIF headers")
    void sanitizedOutputExcludesAllStructuredSifHeaders(
            @ForAll("headerMapsWithSifHeaders") Map<String, String> inputHeaders) {

        // Construct HeaderBlacklistProperties with default config (no custom entries)
        HeaderBlacklistProperties props = new HeaderBlacklistProperties();
        HeaderSanitizer sanitizer = new HeaderSanitizer(props);

        // Sanitize the input
        Map<String, String> result = sanitizer.sanitize(new LinkedHashMap<>(inputHeaders));

        // Assert none of the structured SIF header names appear in the output
        for (String key : result.keySet()) {
            assertThat(STRUCTURED_SIF_HEADERS.contains(key))
                    .as("Structured SIF header '%s' should not appear in sanitized output", key)
                    .isFalse();
        }
    }
}
