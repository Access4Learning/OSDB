// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.Size;
import org.sifunity.broker.common.config.HeaderBlacklistProperties;
import org.sifunity.broker.common.util.HeaderSanitizer;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 2: Header sanitization preserves allowed headers.
 *
 * <p>For any set of HTTP headers on an incoming request, the services-connector
 * SHALL forward exactly those headers that are not in the blacklist and not
 * structured SIF headers, and SHALL exclude all blacklisted and structured SIF
 * headers.</p>
 *
 * <p>This test exercises the {@link HeaderSanitizer} as used in the
 * services-connector's {@code FunctionalServiceController}: incoming headers
 * are sanitized before being forwarded to the provider via the routing service.</p>
 *
 * <p><strong>Validates: Requirements 2.4, 5.8</strong></p>
 */
@PropertyDefaults(tries = 20)
class HeaderSanitizationPropertyTest {

    /**
     * Blacklisted header names (security-sensitive, always stripped).
     * Sourced from {@link HeaderBlacklistProperties#DEFAULTS}.
     */
    private static final List<String> BLACKLISTED_HEADERS = List.of(
            "authorization",
            "timestamp",
            "x-environment-id",
            "x-environment-name",
            "fingerprint",
            "sharedsecret",
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
     * Structured SIF header names that are carried as first-class fields on
     * {@code SifMessageEnvelope} and excluded from passthrough.
     * Sourced from {@link HeaderSanitizer#STRUCTURED_SIF_HEADERS}.
     */
    private static final List<String> STRUCTURED_SIF_HEADERS = List.of(
            "messageid",
            "messagetype",
            "servicename",
            "servicetype",
            "zoneid",
            "contextid",
            "requestid",
            "queueid",
            "sourceid"
    );

    /**
     * All header names that should be stripped (union of blacklisted + SIF-structured).
     * Stored in a case-insensitive set for lookup.
     */
    private static final Set<String> ALL_STRIPPED;

    static {
        TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        set.addAll(BLACKLISTED_HEADERS);
        set.addAll(STRUCTURED_SIF_HEADERS);
        ALL_STRIPPED = Collections.unmodifiableSet(set);
    }

    /** HeaderSanitizer instance with default blacklist (no custom entries). */
    private final HeaderSanitizer sanitizer;

    HeaderSanitizationPropertyTest() {
        HeaderBlacklistProperties props = new HeaderBlacklistProperties();
        this.sanitizer = new HeaderSanitizer(props);
    }

    // ========== Arbitraries ==========

    /**
     * Generates allowed header names that do NOT collide with blacklisted or
     * SIF-structured headers. Uses a prefix "x-custom-" to guarantee no overlap.
     */
    @Provide
    Arbitrary<String> allowedHeaderNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(15)
                .map(s -> "x-custom-" + s.toLowerCase());
    }

    /**
     * Generates a random blacklisted header name, optionally with varied casing
     * to test case-insensitive matching.
     */
    @Provide
    Arbitrary<String> blacklistedHeaders() {
        return Arbitraries.of(BLACKLISTED_HEADERS)
                .flatMap(this::randomizeCasing);
    }

    /**
     * Generates a random SIF-structured header name, optionally with varied casing.
     */
    @Provide
    Arbitrary<String> sifStructuredHeaders() {
        return Arbitraries.of(STRUCTURED_SIF_HEADERS)
                .flatMap(this::randomizeCasing);
    }

    /** Generates a random header value. */
    @Provide
    Arbitrary<String> headerValues() {
        return Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(50);
    }

    /**
     * Generates a mixed header map containing a blend of allowed, blacklisted,
     * and SIF-structured headers.
     */
    @Provide
    Arbitrary<Map<String, String>> mixedHeaderMaps() {
        Arbitrary<String> values = Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(50);

        // Allowed headers (0-5)
        Arbitrary<Map<String, String>> allowedPart = Arbitraries.maps(
                allowedHeaderNames(), values
        ).ofMinSize(0).ofMaxSize(5);

        // Blacklisted headers (0-3)
        Arbitrary<Map<String, String>> blacklistedPart = Arbitraries.maps(
                blacklistedHeaders(), values
        ).ofMinSize(0).ofMaxSize(3);

        // SIF-structured headers (0-3)
        Arbitrary<Map<String, String>> sifPart = Arbitraries.maps(
                sifStructuredHeaders(), values
        ).ofMinSize(0).ofMaxSize(3);

        return Combinators.combine(allowedPart, blacklistedPart, sifPart)
                .as((allowed, blacklisted, sif) -> {
                    Map<String, String> combined = new LinkedHashMap<>();
                    combined.putAll(allowed);
                    combined.putAll(blacklisted);
                    combined.putAll(sif);
                    return combined;
                });
    }

    // ========== Properties ==========

    /**
     * Sanitized output SHALL contain no blacklisted headers, regardless of casing.
     *
     * <p><strong>Validates: Requirements 2.4, 5.8</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 2: Header sanitization preserves allowed headers")
    void sanitizedOutputContainsNoBlacklistedHeaders(
            @ForAll("mixedHeaderMaps") Map<String, String> headers) {

        Map<String, String> sanitized = sanitizer.sanitize(headers);

        for (String key : sanitized.keySet()) {
            assertThat(isBlacklisted(key))
                    .as("Blacklisted header '%s' should have been stripped", key)
                    .isFalse();
        }
    }

    /**
     * Sanitized output SHALL contain no structured SIF headers, regardless of casing.
     *
     * <p><strong>Validates: Requirements 2.4, 5.8</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 2: Header sanitization preserves allowed headers")
    void sanitizedOutputContainsNoSifStructuredHeaders(
            @ForAll("mixedHeaderMaps") Map<String, String> headers) {

        Map<String, String> sanitized = sanitizer.sanitize(headers);

        for (String key : sanitized.keySet()) {
            assertThat(isSifStructured(key))
                    .as("SIF-structured header '%s' should have been stripped", key)
                    .isFalse();
        }
    }

    /**
     * Every allowed header in the input SHALL be preserved in the sanitized output
     * with its original value unchanged.
     *
     * <p><strong>Validates: Requirements 2.4, 5.8</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 2: Header sanitization preserves allowed headers")
    void allowedHeadersArePreservedWithOriginalValues(
            @ForAll("mixedHeaderMaps") Map<String, String> headers) {

        Map<String, String> sanitized = sanitizer.sanitize(headers);

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (!ALL_STRIPPED.contains(key)) {
                assertThat(sanitized)
                        .as("Allowed header '%s' should be preserved", key)
                        .containsEntry(key, value);
            }
        }
    }

    /**
     * The sanitized output SHALL contain exactly the allowed headers from the input —
     * no more, no less. The size of the sanitized map must equal the count of
     * non-stripped headers in the input.
     *
     * <p><strong>Validates: Requirements 2.4, 5.8</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 2: Header sanitization preserves allowed headers")
    void sanitizedOutputSizeMatchesAllowedHeaderCount(
            @ForAll("mixedHeaderMaps") Map<String, String> headers) {

        Map<String, String> sanitized = sanitizer.sanitize(headers);

        long expectedCount = headers.keySet().stream()
                .filter(k -> !ALL_STRIPPED.contains(k))
                .count();

        assertThat(sanitized).hasSize((int) expectedCount);
    }

    /**
     * Sanitizing a null input SHALL return an empty map (no NPE).
     *
     * <p><strong>Validates: Requirements 2.4</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 2: Header sanitization preserves allowed headers")
    void nullInputReturnsEmptyMap() {
        Map<String, String> sanitized = sanitizer.sanitize((Map<String, String>) null);
        assertThat(sanitized).isEmpty();
    }

    /**
     * Blacklisted headers with varied casing (e.g., "Authorization", "AUTHORIZATION")
     * SHALL still be stripped — the blacklist is case-insensitive.
     *
     * <p><strong>Validates: Requirements 2.4, 5.8</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 2: Header sanitization preserves allowed headers")
    void caseInsensitiveBlacklistStripping(
            @ForAll("blacklistedHeaders") String blacklistedHeader,
            @ForAll("headerValues") String value) {

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(blacklistedHeader, value);

        Map<String, String> sanitized = sanitizer.sanitize(headers);

        assertThat(sanitized)
                .as("Blacklisted header '%s' (any casing) should be stripped", blacklistedHeader)
                .isEmpty();
    }

    /**
     * SIF-structured headers with varied casing SHALL still be stripped —
     * the SIF header set is case-insensitive.
     *
     * <p><strong>Validates: Requirements 2.4, 5.8</strong></p>
     */
    @Property
    @Tag("Feature: sif-functional-services-redesign, Property 2: Header sanitization preserves allowed headers")
    void caseInsensitiveSifHeaderStripping(
            @ForAll("sifStructuredHeaders") String sifHeader,
            @ForAll("headerValues") String value) {

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(sifHeader, value);

        Map<String, String> sanitized = sanitizer.sanitize(headers);

        assertThat(sanitized)
                .as("SIF-structured header '%s' (any casing) should be stripped", sifHeader)
                .isEmpty();
    }

    // ========== Helpers ==========

    private boolean isBlacklisted(String headerName) {
        return BLACKLISTED_HEADERS.stream()
                .anyMatch(b -> b.equalsIgnoreCase(headerName));
    }

    private boolean isSifStructured(String headerName) {
        return STRUCTURED_SIF_HEADERS.stream()
                .anyMatch(s -> s.equalsIgnoreCase(headerName));
    }

    /**
     * Randomly changes the casing of each character in the given string to test
     * case-insensitive matching.
     */
    private Arbitrary<String> randomizeCasing(String input) {
        return Arbitraries.of(
                input,
                input.toUpperCase(),
                input.toLowerCase(),
                mixCase(input)
        );
    }

    private static String mixCase(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            sb.append(i % 2 == 0 ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
