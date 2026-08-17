// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.exception.ParameterConflictException;
import org.sifunity.broker.common.util.ParameterResolver;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Property 5: Resolver conflict detection.
 *
 * <p>For any parameter name where the matrix parameter value and the HTTP header value
 * are both present and differ, the resolver SHALL reject with a ParameterConflictException
 * identifying the parameter name, matrix value, and header value.</p>
 *
 * <p><strong>Validates: Requirements 2.2, 4.3</strong></p>
 */
class ParameterResolverConflictPropertyTest {

    // ========== Arbitraries ==========

    /**
     * Generates non-empty string values suitable for parameter values.
     */
    @Provide
    Arbitrary<String> paramValues() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('-', '_', '.')
                .ofMinLength(1)
                .ofMaxLength(40);
    }

    // ========== Properties ==========

    /**
     * When zoneId has different values in matrix params (key "zoneId") and headers (key "zoneId"),
     * resolver throws ParameterConflictException with correct fields.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 5: Resolver conflict detection")
    void conflictingZoneIdThrowsExceptionWithCorrectFields(
            @ForAll("paramValues") String matrixValue,
            @ForAll("paramValues") String headerValue) {

        Assume.that(!matrixValue.equals(headerValue));

        Map<String, String> matrixParams = new HashMap<>();
        matrixParams.put("zoneId", matrixValue);

        Map<String, String> headers = new HashMap<>();
        headers.put("zoneId", headerValue);

        ParameterConflictException exception = catchThrowableOfType(
                () -> ParameterResolver.resolve(matrixParams, headers),
                ParameterConflictException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getParameterName()).isEqualTo("zoneId");
        assertThat(exception.getMatrixValue()).isEqualTo(matrixValue);
        assertThat(exception.getHeaderValue()).isEqualTo(headerValue);
    }

    /**
     * When contextId has different values in matrix params (key "contextId") and headers (key "contextId"),
     * resolver throws ParameterConflictException with correct fields.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 5: Resolver conflict detection")
    void conflictingContextIdThrowsExceptionWithCorrectFields(
            @ForAll("paramValues") String matrixValue,
            @ForAll("paramValues") String headerValue) {

        Assume.that(!matrixValue.equals(headerValue));

        Map<String, String> matrixParams = new HashMap<>();
        matrixParams.put("contextId", matrixValue);

        Map<String, String> headers = new HashMap<>();
        headers.put("contextId", headerValue);

        ParameterConflictException exception = catchThrowableOfType(
                () -> ParameterResolver.resolve(matrixParams, headers),
                ParameterConflictException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getParameterName()).isEqualTo("contextId");
        assertThat(exception.getMatrixValue()).isEqualTo(matrixValue);
        assertThat(exception.getHeaderValue()).isEqualTo(headerValue);
    }

    /**
     * When deleteMessageId has different values in matrix params (key "messageId") and
     * headers (key "deleteMessageId"), resolver throws ParameterConflictException with correct fields.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 5: Resolver conflict detection")
    void conflictingDeleteMessageIdThrowsExceptionWithCorrectFields(
            @ForAll("paramValues") String matrixValue,
            @ForAll("paramValues") String headerValue) {

        Assume.that(!matrixValue.equals(headerValue));

        Map<String, String> matrixParams = new HashMap<>();
        matrixParams.put("messageId", matrixValue);

        Map<String, String> headers = new HashMap<>();
        headers.put("deleteMessageId", headerValue);

        ParameterConflictException exception = catchThrowableOfType(
                () -> ParameterResolver.resolve(matrixParams, headers),
                ParameterConflictException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getParameterName()).isEqualTo("deleteMessageId");
        assertThat(exception.getMatrixValue()).isEqualTo(matrixValue);
        assertThat(exception.getHeaderValue()).isEqualTo(headerValue);
    }
}
