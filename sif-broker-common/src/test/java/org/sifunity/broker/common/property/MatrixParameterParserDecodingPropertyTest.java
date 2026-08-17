// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.util.MatrixParameterParser;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 3: Parser URL decoding.
 *
 * <p>For any string value that is URL-encoded and placed as a matrix parameter value,
 * the parser SHALL return the decoded original string in the extracted map.</p>
 *
 * <p><strong>Validates: Requirements 8.3</strong></p>
 */
class MatrixParameterParserDecodingPropertyTest {

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> arbitraryValues() {
        // Use a mix of ASCII printable, whitespace, special chars, and Unicode
        // that can survive URL encoding/decoding round-trip.
        // Avoid lone surrogates which are invalid in UTF-8 encoding.
        return Arbitraries.oneOf(
                // ASCII printable with special chars (spaces, semicolons, equals, slashes, etc.)
                Arbitraries.strings()
                        .withCharRange(' ', '~')
                        .ofMinLength(1)
                        .ofMaxLength(50),
                // Unicode BMP characters (excluding surrogates D800-DFFF)
                Arbitraries.strings()
                        .withCharRange('\u0001', '\uD7FF')
                        .ofMinLength(1)
                        .ofMaxLength(30),
                // Strings with common problematic chars for URL encoding
                Arbitraries.of(
                        "hello world", "a+b", "a=b", "a;b", "a/b", "a&b=c",
                        "100%", "caf\u00E9", "\u65E5\u672C\u8A9E",
                        " ", ";", "=", "+", "%20", "%3B"
                )
        );
    }

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20);
    }

    @Provide
    Arbitrary<String> recognizedKeys() {
        return Arbitraries.of("zoneId", "contextId", "messageId");
    }

    // ========== Properties ==========

    /**
     * For any arbitrary string value that is URL-encoded and placed as a matrix
     * parameter value, parsing the path returns the decoded original string.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 3: Parser URL decoding")
    void urlEncodedMatrixParamValueIsDecodedToOriginal(
            @ForAll("serviceNames") String serviceName,
            @ForAll("recognizedKeys") String key,
            @ForAll("arbitraryValues") String originalValue) {

        String encodedValue = URLEncoder.encode(originalValue, StandardCharsets.UTF_8);
        String path = "/" + serviceName + ";" + key + "=" + encodedValue;

        MatrixParameterParser.ParseResult result = MatrixParameterParser.parse(path);

        assertThat(result.params()).containsKey(key);
        assertThat(result.params().get(key)).isEqualTo(originalValue);
    }

    /**
     * Spaces encoded as '+' are correctly decoded back to spaces.
     */
    @Example
    @Tag("Feature: sif-matrix-param-compat, Property 3: Parser URL decoding")
    void spacesEncodedAsPlusAreDecoded() {
        // URLEncoder encodes spaces as '+'
        String original = "hello world";
        String encoded = URLEncoder.encode(original, StandardCharsets.UTF_8);
        assertThat(encoded).contains("+");

        String path = "/StudentPersonals;zoneId=" + encoded;
        MatrixParameterParser.ParseResult result = MatrixParameterParser.parse(path);

        assertThat(result.params().get("zoneId")).isEqualTo(original);
    }

    /**
     * Special characters (semicolons, equals, slashes) in values are correctly
     * round-tripped through URL encoding and decoding.
     */
    @Example
    @Tag("Feature: sif-matrix-param-compat, Property 3: Parser URL decoding")
    void specialCharactersInValuesAreDecoded() {
        String original = "zone;with=special/chars&more";
        String encoded = URLEncoder.encode(original, StandardCharsets.UTF_8);

        String path = "/Service;zoneId=" + encoded;
        MatrixParameterParser.ParseResult result = MatrixParameterParser.parse(path);

        assertThat(result.params().get("zoneId")).isEqualTo(original);
    }

    /**
     * Unicode characters in values are correctly round-tripped through URL encoding.
     */
    @Example
    @Tag("Feature: sif-matrix-param-compat, Property 3: Parser URL decoding")
    void unicodeCharactersInValuesAreDecoded() {
        String original = "日本語テスト";
        String encoded = URLEncoder.encode(original, StandardCharsets.UTF_8);

        String path = "/Service;contextId=" + encoded;
        MatrixParameterParser.ParseResult result = MatrixParameterParser.parse(path);

        assertThat(result.params().get("contextId")).isEqualTo(original);
    }
}
