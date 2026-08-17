// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.util.MatrixParameterParser;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 2: Parser idempotence on clean paths.
 *
 * <p>For any URL path that contains no semicolons in its last segment,
 * parsing the path SHALL return the path unchanged as the clean path
 * and an empty parameter map.</p>
 *
 * <p><strong>Validates: Requirements 1.3, 8.2</strong></p>
 */
class MatrixParameterParserIdempotencePropertyTest {

    // ========== Arbitraries ==========

    /**
     * Generates path segments that contain no semicolons.
     * Uses alphanumeric characters, hyphens, and underscores — typical SIF service names.
     */
    @Provide
    Arbitrary<String> cleanSegments() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(1)
                .ofMaxLength(40);
    }

    /**
     * Generates path prefixes (zero or more segments separated by '/').
     * Each segment contains no semicolons.
     */
    @Provide
    Arbitrary<String> pathPrefixes() {
        Arbitrary<String> segment = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(1)
                .ofMaxLength(20);

        return segment.list().ofMinSize(0).ofMaxSize(4)
                .map(segments -> {
                    if (segments.isEmpty()) {
                        return "/";
                    }
                    return "/" + String.join("/", segments) + "/";
                });
    }

    /**
     * Generates optional query strings (no semicolons in query portion either,
     * but semicolons in query strings are fine — the parser only looks at the
     * last path segment before '?'). We include query strings with various
     * characters to ensure they are preserved.
     */
    @Provide
    Arbitrary<String> queryStrings() {
        Arbitrary<String> queryContent = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('=', '&', '_', '-', '.', '+', '%')
                .ofMinLength(1)
                .ofMaxLength(50);

        return Arbitraries.oneOf(
                Arbitraries.just(""),
                queryContent.map(q -> "?" + q)
        );
    }

    /**
     * Generates complete clean paths: prefix + last segment (no semicolons) + optional query string.
     */
    @Provide
    Arbitrary<String> cleanPaths() {
        return Combinators.combine(pathPrefixes(), cleanSegments(), queryStrings())
                .as((prefix, lastSegment, query) -> prefix + lastSegment + query);
    }

    // ========== Properties ==========

    /**
     * For any URL path with no semicolons in the last segment, the parser
     * SHALL return the path unchanged as the clean path and an empty map.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 2: Parser idempotence on clean paths")
    void parseReturnsPathUnchangedWhenNoSemicolonsInLastSegment(
            @ForAll("cleanPaths") String path) {

        MatrixParameterParser.ParseResult result = MatrixParameterParser.parse(path);

        assertThat(result.cleanPath())
                .as("Clean path should equal original path when no matrix params present")
                .isEqualTo(path);

        assertThat(result.params())
                .as("Params map should be empty when no matrix params present")
                .isEmpty();
    }

    /**
     * Parsing an empty string returns empty path and empty map.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 2: Parser idempotence on clean paths")
    void parseIsIdempotentOnSimpleServicePaths(
            @ForAll("cleanSegments") String serviceName) {

        // Simple path like "/StudentPersonals"
        String path = "/" + serviceName;

        MatrixParameterParser.ParseResult result = MatrixParameterParser.parse(path);

        assertThat(result.cleanPath()).isEqualTo(path);
        assertThat(result.params()).isEmpty();
    }

    /**
     * Paths with query strings but no semicolons in the last segment
     * are returned unchanged with empty params.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 2: Parser idempotence on clean paths")
    void queryStringsArePreservedOnCleanPaths(
            @ForAll("pathPrefixes") String prefix,
            @ForAll("cleanSegments") String lastSegment) {

        // Build path with a query string containing typical SIF query params
        String path = prefix + lastSegment + "?where=Name='Smith'&order=asc";

        MatrixParameterParser.ParseResult result = MatrixParameterParser.parse(path);

        assertThat(result.cleanPath())
                .as("Path with query string should be returned unchanged")
                .isEqualTo(path);

        assertThat(result.params()).isEmpty();
    }
}
