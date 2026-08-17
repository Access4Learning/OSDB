// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.config.HeaderBlacklistProperties;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 2: Custom blacklist merges with defaults.
 *
 * <p>For any set of custom blacklist entries provided via configuration,
 * the effective blacklist returned by {@link HeaderBlacklistProperties#getEffectiveBlacklist()}
 * should be a superset of the default blacklist and should contain every custom entry.</p>
 *
 * <p><strong>Validates: Requirements 2.3</strong></p>
 */
class HeaderBlacklistMergingPropertyTest {

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<Set<String>> customHeaderSets() {
        Arbitrary<String> headerNames = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(30);
        return headerNames.set().ofMinSize(0).ofMaxSize(15);
    }

    // ========== Properties ==========

    /**
     * For any set of custom header names, the effective blacklist contains
     * all default entries and all custom entries.
     *
     * <p>Because the effective blacklist is a case-insensitive TreeSet,
     * we use the set's own {@code contains()} for membership checks rather
     * than AssertJ's literal string matching.</p>
     */
    @Property(tries = 100)
    @Tag("Feature: header-passthrough-and-sanitization, Property 2: Custom blacklist merges with defaults")
    void effectiveBlacklistContainsAllDefaultsAndCustomEntries(
            @ForAll("customHeaderSets") Set<String> customEntries) {

        HeaderBlacklistProperties props = new HeaderBlacklistProperties();
        props.setBlacklist(new LinkedHashSet<>(customEntries));

        Set<String> effective = props.getEffectiveBlacklist();

        // Effective blacklist must contain every default entry (case-insensitive)
        for (String defaultEntry : HeaderBlacklistProperties.DEFAULTS) {
            assertThat(effective.contains(defaultEntry))
                    .as("Effective blacklist should contain default entry '%s'", defaultEntry)
                    .isTrue();
        }

        // Effective blacklist must contain every custom entry (case-insensitive)
        for (String customEntry : customEntries) {
            assertThat(effective.contains(customEntry))
                    .as("Effective blacklist should contain custom entry '%s'", customEntry)
                    .isTrue();
        }
    }
}
