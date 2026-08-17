// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.config.HeaderBlacklistProperties;
import org.sifunity.broker.common.util.HeaderSanitizer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 3: Blacklist matching is case-insensitive.
 *
 * <p>For any header name on the effective blacklist and for any case variation
 * of that name (upper, lower, mixed), the {@link HeaderSanitizer} should remove
 * the header from the output.</p>
 *
 * <p><strong>Validates: Requirements 2.4</strong></p>
 */
class HeaderBlacklistCaseInsensitivePropertyTest {

    private static final List<String> DEFAULT_BLACKLISTED =
            List.copyOf(HeaderBlacklistProperties.DEFAULTS);

    // ========== Arbitraries ==========

    /**
     * Produces a pair: a case-varied blacklisted header name and an arbitrary value.
     * The header name is picked from the defaults and then each character is
     * independently randomised to upper or lower case.
     */
    @Provide
    Arbitrary<Map.Entry<String, String>> caseVariedEntries() {
        Arbitrary<String> headerName = Arbitraries.of(DEFAULT_BLACKLISTED)
                .flatMap(this::randomCaseVariation);
        Arbitrary<String> headerValue = Arbitraries.strings()
                .ascii()
                .ofMinLength(1)
                .ofMaxLength(50);
        return Combinators.combine(headerName, headerValue)
                .as(Map::entry);
    }

    // ========== Properties ==========

    /**
     * For any default-blacklisted header name and any random case variation,
     * the sanitizer must remove the header from the output.
     */
    @Property(tries = 100)
    @Tag("Feature: header-passthrough-and-sanitization, Property 3: Blacklist matching is case-insensitive")
    void blacklistedHeaderRemovedForAnyCaseVariation(
            @ForAll("caseVariedEntries") Map.Entry<String, String> entry) {

        HeaderBlacklistProperties props = new HeaderBlacklistProperties();
        HeaderSanitizer sanitizer = new HeaderSanitizer(props);

        String caseVariedName = entry.getKey();
        String value = entry.getValue();

        Map<String, String> input = new LinkedHashMap<>();
        input.put(caseVariedName, value);

        Map<String, String> result = sanitizer.sanitize(input);

        assertThat(result)
                .as("Header '%s' (case variation of blacklisted entry) should be removed",
                        caseVariedName)
                .doesNotContainKey(caseVariedName);
    }

    // ========== Helpers ==========

    /**
     * Generates a random case variation of the given string by independently
     * randomising each character to upper or lower case.
     */
    private Arbitrary<String> randomCaseVariation(String original) {
        if (original.isEmpty()) {
            return Arbitraries.just(original);
        }
        Arbitrary<Boolean> coin = Arbitraries.of(true, false);
        return coin.list().ofSize(original.length()).map(choices -> {
            StringBuilder sb = new StringBuilder(original.length());
            for (int i = 0; i < original.length(); i++) {
                char c = original.charAt(i);
                sb.append(choices.get(i) ? Character.toUpperCase(c) : Character.toLowerCase(c));
            }
            return sb.toString();
        });
    }
}
