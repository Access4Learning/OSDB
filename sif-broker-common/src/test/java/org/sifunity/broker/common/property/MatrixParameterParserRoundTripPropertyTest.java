// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import net.jqwik.api.*;
import net.jqwik.api.constraints.NotEmpty;
import org.sifunity.broker.common.util.MatrixParameterParser;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 1: Parser round-trip.
 *
 * <p>For any valid URL path containing matrix parameters with recognized keys
 * ({@code zoneId}, {@code contextId}, {@code messageId}), parsing the path to extract
 * parameters and then reconstructing the path by appending the extracted key-value pairs
 * (in {@code ;key=value} format) to the stripped path SHALL produce a string equivalent
 * to the original path.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.4, 1.5, 8.1</strong></p>
 */
class MatrixParameterParserRoundTripPropertyTest {

    private static final List<String> RECOGNIZED_KEYS = List.of("zoneId", "contextId", "messageId");

    // ========== Arbitraries ==========

    /**
     * Generates a valid service name: alphanumeric, starting with uppercase letter.
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
     * Generates parameter values that are safe for URL round-trip (alphanumeric + hyphens + underscores).
     * These values survive URL encode/decode without change.
     */
    @Provide
    Arbitrary<String> safeParamValues() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('-', '_', '.')
                .ofMinLength(1)
                .ofMaxLength(40);
    }

    /**
     * Generates a non-empty subset of recognized keys with associated values.
     */
    @Provide
    Arbitrary<Map<String, String>> recognizedParams() {
        return Arbitraries.of(RECOGNIZED_KEYS)
                .set()
                .ofMinSize(1)
                .ofMaxSize(3)
                .flatMap(keys -> {
                    List<String> keyList = new ArrayList<>(keys);
                    return safeParamValues().list().ofSize(keyList.size())
                            .map(values -> {
                                Map<String, String> map = new LinkedHashMap<>();
                                for (int i = 0; i < keyList.size(); i++) {
                                    map.put(keyList.get(i), values.get(i));
                                }
                                return map;
                            });
                });
    }

    /**
     * Generates an optional path prefix (e.g., "/api/v1" or just "/").
     */
    @Provide
    Arbitrary<String> pathPrefixes() {
        return Arbitraries.oneOf(
                Arbitraries.just("/"),
                Arbitraries.just("/api/"),
                Arbitraries.just("/api/v1/"),
                Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(10)
                        .map(s -> "/" + s.toLowerCase() + "/")
        );
    }

    // ========== Properties ==========

    /**
     * For any valid URL path with recognized matrix params (using safe values),
     * parse then reconstruct produces a string equivalent to the original path.
     *
     * The equivalence check compares the reconstructed service name + params against
     * the original last segment, accounting for parameter ordering.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 1: Parser round-trip")
    void parseAndReconstructProducesEquivalentPath(
            @ForAll("serviceNames") String serviceName,
            @ForAll("recognizedParams") Map<String, String> params) {

        // Build input path: /{serviceName};key1=value1;key2=value2
        StringBuilder inputPath = new StringBuilder("/");
        inputPath.append(serviceName);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            inputPath.append(';')
                    .append(entry.getKey())
                    .append('=')
                    .append(entry.getValue());
        }

        String originalPath = inputPath.toString();

        // Parse
        MatrixParameterParser.ParseResult result = MatrixParameterParser.parse(originalPath);

        // Verify clean path has no matrix params
        assertThat(result.cleanPath()).isEqualTo("/" + serviceName);

        // Verify all params were extracted
        assertThat(result.params()).containsExactlyInAnyOrderEntriesOf(params);

        // Reconstruct using service name extracted from clean path
        String reconstructedServiceSegment = MatrixParameterParser.reconstruct(
                serviceName, result.params());

        // The reconstructed segment should contain the service name and all params
        // Parse the reconstructed segment to verify equivalence
        MatrixParameterParser.ParseResult reparsed = MatrixParameterParser.parse(
                "/" + reconstructedServiceSegment);

        assertThat(reparsed.cleanPath()).isEqualTo("/" + serviceName);
        assertThat(reparsed.params()).containsExactlyInAnyOrderEntriesOf(params);
    }

    /**
     * For paths with a prefix before the service name, round-trip still works
     * on the last segment containing matrix params.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 1: Parser round-trip")
    void parseAndReconstructWorksWithPathPrefix(
            @ForAll("pathPrefixes") String prefix,
            @ForAll("serviceNames") String serviceName,
            @ForAll("recognizedParams") Map<String, String> params) {

        // Build input path: {prefix}{serviceName};key1=value1;key2=value2
        StringBuilder inputPath = new StringBuilder(prefix);
        inputPath.append(serviceName);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            inputPath.append(';')
                    .append(entry.getKey())
                    .append('=')
                    .append(entry.getValue());
        }

        String originalPath = inputPath.toString();

        // Parse
        MatrixParameterParser.ParseResult result = MatrixParameterParser.parse(originalPath);

        // Clean path should be prefix + serviceName
        assertThat(result.cleanPath()).isEqualTo(prefix + serviceName);

        // All params extracted
        assertThat(result.params()).containsExactlyInAnyOrderEntriesOf(params);

        // Reconstruct and re-parse to verify equivalence
        String reconstructed = MatrixParameterParser.reconstruct(serviceName, result.params());
        MatrixParameterParser.ParseResult reparsed = MatrixParameterParser.parse(
                prefix + reconstructed);

        assertThat(reparsed.cleanPath()).isEqualTo(prefix + serviceName);
        assertThat(reparsed.params()).containsExactlyInAnyOrderEntriesOf(params);
    }

    /**
     * For URL-encoded values in matrix params, parse decodes them and reconstruct
     * re-encodes them, producing a valid round-trip.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 1: Parser round-trip")
    void parseAndReconstructHandlesUrlEncodedValues(
            @ForAll("serviceNames") String serviceName,
            @ForAll("safeParamValues") String value) {

        // URL-encode the value in the input (simulating what a client would send)
        String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8);

        String inputPath = "/" + serviceName + ";zoneId=" + encodedValue;

        // Parse — should decode the value
        MatrixParameterParser.ParseResult result = MatrixParameterParser.parse(inputPath);
        assertThat(result.params().get("zoneId")).isEqualTo(value);

        // Reconstruct — should re-encode the value
        String reconstructed = MatrixParameterParser.reconstruct(serviceName, result.params());

        // Re-parse the reconstructed path
        MatrixParameterParser.ParseResult reparsed = MatrixParameterParser.parse(
                "/" + reconstructed);

        // Values should match after full round-trip
        assertThat(reparsed.params().get("zoneId")).isEqualTo(value);
    }
}
