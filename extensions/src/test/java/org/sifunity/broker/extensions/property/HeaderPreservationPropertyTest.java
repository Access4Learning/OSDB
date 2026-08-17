// Feature: message-sequence-extension, Property 5: Existing headers are preserved
package org.sifunity.broker.extensions.property;

import net.jqwik.api.*;
import org.sifunity.broker.extensions.sequencing.MutableHttpServletRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 5: Existing headers are preserved.
 *
 * <p>For any set of existing HTTP request headers, after wrapping with
 * {@link MutableHttpServletRequest} and adding a header, all original headers
 * are still present with their original values.</p>
 *
 * <p><strong>Validates: Requirements 3.4, 4.4, 5.4, 6.4</strong></p>
 */
@PropertyDefaults(tries = 10)
class HeaderPreservationPropertyTest {

    // ========== Arbitraries ==========

    /**
     * Generates a lowercase alphabetic header name. We use lowercase to avoid
     * case-sensitivity issues — HTTP headers are case-insensitive per RFC 7230,
     * and MockHttpServletRequest normalizes names to lowercase internally.
     */
    @Provide
    Arbitrary<String> headerNames() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(20);
    }

    /**
     * Generates an alphanumeric header value.
     */
    @Provide
    Arbitrary<String> headerValues() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    /**
     * Generates a list of 1-10 header entries with unique lowercase names.
     * Using a list of entries with distinct names avoids duplicate-key issues.
     */
    @Provide
    Arbitrary<List<Map.Entry<String, String>>> existingHeaderEntries() {
        return headerNames().flatMap(name ->
                headerValues().map(value -> Map.entry(name, value))
        ).list().ofMinSize(1).ofMaxSize(10).uniqueElements(Map.Entry::getKey);
    }

    /**
     * Generates a header name guaranteed to not collide with existing headers.
     * Uses a fixed prefix "x-new-" to avoid collisions with generated lowercase names.
     */
    @Provide
    Arbitrary<String> newHeaderName() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(10)
                .map(s -> "x-new-" + s);
    }

    // ========== Properties ==========

    /**
     * After wrapping a request and adding a new header, all original headers
     * SHALL still be retrievable via {@code getHeader(name)} with their original values.
     */
    @Property
    void allOriginalHeadersPreservedAfterAddingNewHeader(
            @ForAll("existingHeaderEntries") List<Map.Entry<String, String>> originalHeaders,
            @ForAll("newHeaderName") String newHeaderName,
            @ForAll("headerValues") String newHeaderValue) {

        // Set up a mock request with original headers
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        for (Map.Entry<String, String> entry : originalHeaders) {
            mockRequest.addHeader(entry.getKey(), entry.getValue());
        }

        // Wrap and add a new header
        MutableHttpServletRequest wrapped = new MutableHttpServletRequest(mockRequest);
        wrapped.addHeader(newHeaderName, newHeaderValue);

        // Verify all original headers are still present with original values
        for (Map.Entry<String, String> entry : originalHeaders) {
            String name = entry.getKey();
            // Skip if the new header has the same name (it would override in the wrapper)
            if (name.equalsIgnoreCase(newHeaderName)) {
                continue;
            }
            assertThat(wrapped.getHeader(name))
                    .as("Original header '%s' should be preserved", name)
                    .isEqualTo(entry.getValue());
        }
    }

    /**
     * After wrapping a request and adding a new header, all original header names
     * SHALL still appear in {@code getHeaderNames()}.
     */
    @Property
    void allOriginalHeaderNamesAppearInGetHeaderNames(
            @ForAll("existingHeaderEntries") List<Map.Entry<String, String>> originalHeaders,
            @ForAll("newHeaderName") String newHeaderName,
            @ForAll("headerValues") String newHeaderValue) {

        // Set up a mock request with original headers
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        for (Map.Entry<String, String> entry : originalHeaders) {
            mockRequest.addHeader(entry.getKey(), entry.getValue());
        }

        // Wrap and add a new header
        MutableHttpServletRequest wrapped = new MutableHttpServletRequest(mockRequest);
        wrapped.addHeader(newHeaderName, newHeaderValue);

        // Collect all header names from the wrapped request (case-insensitive)
        Set<String> headerNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Enumeration<String> names = wrapped.getHeaderNames();
        while (names.hasMoreElements()) {
            headerNames.add(names.nextElement());
        }

        // All original header names must be present (case-insensitive check)
        for (Map.Entry<String, String> entry : originalHeaders) {
            assertThat(headerNames)
                    .as("Header name '%s' should be in getHeaderNames()", entry.getKey())
                    .contains(entry.getKey());
        }
    }

    /**
     * Wrapping a request without adding any header SHALL preserve all original
     * headers exactly as they were.
     */
    @Property
    void wrappingWithoutAddingPreservesAllHeaders(
            @ForAll("existingHeaderEntries") List<Map.Entry<String, String>> originalHeaders) {

        // Set up a mock request with original headers
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        for (Map.Entry<String, String> entry : originalHeaders) {
            mockRequest.addHeader(entry.getKey(), entry.getValue());
        }

        // Wrap without adding anything
        MutableHttpServletRequest wrapped = new MutableHttpServletRequest(mockRequest);

        // All original headers must be preserved
        for (Map.Entry<String, String> entry : originalHeaders) {
            assertThat(wrapped.getHeader(entry.getKey()))
                    .as("Header '%s' should be preserved when no new headers added", entry.getKey())
                    .isEqualTo(entry.getValue());
        }

        // Header names enumeration must contain all originals
        Set<String> headerNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Enumeration<String> names = wrapped.getHeaderNames();
        while (names.hasMoreElements()) {
            headerNames.add(names.nextElement());
        }
        for (Map.Entry<String, String> entry : originalHeaders) {
            assertThat(headerNames).contains(entry.getKey());
        }
    }
}
