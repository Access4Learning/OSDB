// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.util.RelativeServicePathFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 9: relativeServicePath format invariant.
 *
 * <p>When queryString is non-null and non-empty, the output contains {@code ?} followed by
 * the query string. When queryString is null or empty, the output does NOT contain {@code ?}.
 * The output always starts with {@code /} followed by the service name.</p>
 *
 * <p><strong>Validates: Requirements 5.4</strong></p>
 */
class RelativeServicePathInvariantPropertyTest {

    // ========== Arbitraries ==========

    /**
     * Generates valid SIF service names: alphanumeric, starting with uppercase letter.
     */
    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(30)
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1));
    }

    /**
     * Generates non-empty query strings (e.g., "where=name%3D'Smith'&limit=10").
     */
    @Provide
    Arbitrary<String> nonEmptyQueryStrings() {
        Arbitrary<String> paramNames = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(2)
                .ofMaxLength(15);

        Arbitrary<String> paramValues = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('%', '+', '.', '-', '_')
                .ofMinLength(1)
                .ofMaxLength(30);

        Arbitrary<String> singleParam = Combinators.combine(paramNames, paramValues)
                .as((name, value) -> name + "=" + value);

        return singleParam.list().ofMinSize(1).ofMaxSize(3)
                .map(params -> String.join("&", params));
    }

    /**
     * Generates zone IDs.
     */
    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(3)
                .ofMaxLength(20);
    }

    /**
     * Generates context IDs.
     */
    @Provide
    Arbitrary<String> contextIds() {
        return Arbitraries.oneOf(
                Arbitraries.just("DEFAULT"),
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .withCharRange('A', 'Z')
                        .withCharRange('0', '9')
                        .withChars('-', '_')
                        .ofMinLength(3)
                        .ofMaxLength(20)
        );
    }

    // ========== Properties ==========

    /**
     * When queryString is non-null and non-empty, output contains '?' followed by the query string.
     * Tests both legacy and modern formats.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 9: relativeServicePath format invariant")
    void whenQueryStringNonNull_outputContainsQuestionMarkFollowedByQueryString(
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll("nonEmptyQueryStrings") String queryString,
            @ForAll boolean legacyFormat) {

        String result = RelativeServicePathFormatter.format(
                serviceName, zoneId, contextId, queryString, legacyFormat);

        // Output must contain '?' followed by the query string
        assertThat(result).contains("?" + queryString);

        // Output always starts with '/' followed by service name
        assertThat(result).startsWith("/" + serviceName);
    }

    /**
     * When queryString is null, output does NOT contain '?'.
     * Tests both legacy and modern formats.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 9: relativeServicePath format invariant")
    void whenQueryStringNull_outputDoesNotContainQuestionMark(
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll boolean legacyFormat) {

        String result = RelativeServicePathFormatter.format(
                serviceName, zoneId, contextId, null, legacyFormat);

        // Output must NOT contain '?'
        assertThat(result).doesNotContain("?");

        // Output always starts with '/' followed by service name
        assertThat(result).startsWith("/" + serviceName);
    }

    /**
     * When queryString is empty, output does NOT contain '?'.
     * Tests both legacy and modern formats.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 9: relativeServicePath format invariant")
    void whenQueryStringEmpty_outputDoesNotContainQuestionMark(
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll boolean legacyFormat) {

        String result = RelativeServicePathFormatter.format(
                serviceName, zoneId, contextId, "", legacyFormat);

        // Output must NOT contain '?'
        assertThat(result).doesNotContain("?");

        // Output always starts with '/' followed by service name
        assertThat(result).startsWith("/" + serviceName);
    }

    /**
     * Output always starts with '/' followed by service name regardless of format or query string.
     * Tests all combinations of legacy/modern and with/without query string.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 9: relativeServicePath format invariant")
    void outputAlwaysStartsWithSlashAndServiceName(
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll boolean legacyFormat) {

        // With null query string
        String resultNull = RelativeServicePathFormatter.format(
                serviceName, zoneId, contextId, null, legacyFormat);
        assertThat(resultNull).startsWith("/" + serviceName);

        // With empty query string
        String resultEmpty = RelativeServicePathFormatter.format(
                serviceName, zoneId, contextId, "", legacyFormat);
        assertThat(resultEmpty).startsWith("/" + serviceName);
    }
}
