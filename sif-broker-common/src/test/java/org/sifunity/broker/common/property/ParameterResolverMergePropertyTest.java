// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.util.ParameterResolver;
import org.sifunity.broker.common.util.ParameterResolver.ResolvedParams;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 4: Resolver deterministic merge.
 *
 * <p>For any parameter name and any combination of presence/absence in matrix params and headers
 * where values agree or only one source provides the value, the resolver SHALL produce a
 * deterministic single resolved value equal to the provided value (matrix-only → matrix value,
 * header-only → header value, both-same → that value, both-absent → null).</p>
 *
 * <p><strong>Validates: Requirements 2.1, 2.3, 2.4, 2.5, 2.6</strong></p>
 */
class ParameterResolverMergePropertyTest {

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

    // ========== zoneId properties ==========

    /**
     * When zoneId is present only in matrix params, resolved zoneId equals the matrix value.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 4: Resolver deterministic merge")
    void zoneIdMatrixOnly_resolvesToMatrixValue(@ForAll("paramValues") String value) {
        Map<String, String> matrixParams = new HashMap<>();
        matrixParams.put("zoneId", value);

        Map<String, String> headers = new HashMap<>();

        ResolvedParams result = ParameterResolver.resolve(matrixParams, headers);

        assertThat(result.zoneId()).isEqualTo(value);
    }

    /**
     * When zoneId is present only in headers, resolved zoneId equals the header value.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 4: Resolver deterministic merge")
    void zoneIdHeaderOnly_resolvesToHeaderValue(@ForAll("paramValues") String value) {
        Map<String, String> matrixParams = new HashMap<>();

        Map<String, String> headers = new HashMap<>();
        headers.put("zoneId", value);

        ResolvedParams result = ParameterResolver.resolve(matrixParams, headers);

        assertThat(result.zoneId()).isEqualTo(value);
    }

    /**
     * When zoneId is present in both sources with the same value, resolved zoneId equals that value.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 4: Resolver deterministic merge")
    void zoneIdBothSame_resolvesToThatValue(@ForAll("paramValues") String value) {
        Map<String, String> matrixParams = new HashMap<>();
        matrixParams.put("zoneId", value);

        Map<String, String> headers = new HashMap<>();
        headers.put("zoneId", value);

        ResolvedParams result = ParameterResolver.resolve(matrixParams, headers);

        assertThat(result.zoneId()).isEqualTo(value);
    }

    /**
     * When zoneId is absent from both sources, resolved zoneId is null.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 4: Resolver deterministic merge")
    void zoneIdBothAbsent_resolvesToNull() {
        Map<String, String> matrixParams = new HashMap<>();
        Map<String, String> headers = new HashMap<>();

        ResolvedParams result = ParameterResolver.resolve(matrixParams, headers);

        assertThat(result.zoneId()).isNull();
    }

    // ========== contextId properties ==========

    /**
     * When contextId is present only in matrix params, resolved contextId equals the matrix value.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 4: Resolver deterministic merge")
    void contextIdMatrixOnly_resolvesToMatrixValue(@ForAll("paramValues") String value) {
        Map<String, String> matrixParams = new HashMap<>();
        matrixParams.put("contextId", value);

        Map<String, String> headers = new HashMap<>();

        ResolvedParams result = ParameterResolver.resolve(matrixParams, headers);

        assertThat(result.contextId()).isEqualTo(value);
    }

    /**
     * When contextId is present only in headers, resolved contextId equals the header value.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 4: Resolver deterministic merge")
    void contextIdHeaderOnly_resolvesToHeaderValue(@ForAll("paramValues") String value) {
        Map<String, String> matrixParams = new HashMap<>();

        Map<String, String> headers = new HashMap<>();
        headers.put("contextId", value);

        ResolvedParams result = ParameterResolver.resolve(matrixParams, headers);

        assertThat(result.contextId()).isEqualTo(value);
    }

    /**
     * When contextId is present in both sources with the same value, resolved contextId equals that value.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 4: Resolver deterministic merge")
    void contextIdBothSame_resolvesToThatValue(@ForAll("paramValues") String value) {
        Map<String, String> matrixParams = new HashMap<>();
        matrixParams.put("contextId", value);

        Map<String, String> headers = new HashMap<>();
        headers.put("contextId", value);

        ResolvedParams result = ParameterResolver.resolve(matrixParams, headers);

        assertThat(result.contextId()).isEqualTo(value);
    }

    /**
     * When contextId is absent from both sources, resolved contextId is null.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 4: Resolver deterministic merge")
    void contextIdBothAbsent_resolvesToNull() {
        Map<String, String> matrixParams = new HashMap<>();
        Map<String, String> headers = new HashMap<>();

        ResolvedParams result = ParameterResolver.resolve(matrixParams, headers);

        assertThat(result.contextId()).isNull();
    }

    // ========== deleteMessageId properties ==========

    /**
     * When deleteMessageId is present only in matrix params (key "messageId"),
     * resolved deleteMessageId equals the matrix value.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 4: Resolver deterministic merge")
    void deleteMessageIdMatrixOnly_resolvesToMatrixValue(@ForAll("paramValues") String value) {
        Map<String, String> matrixParams = new HashMap<>();
        matrixParams.put("messageId", value);

        Map<String, String> headers = new HashMap<>();

        ResolvedParams result = ParameterResolver.resolve(matrixParams, headers);

        assertThat(result.deleteMessageId()).isEqualTo(value);
    }

    /**
     * When deleteMessageId is present only in headers (key "deleteMessageId"),
     * resolved deleteMessageId equals the header value.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 4: Resolver deterministic merge")
    void deleteMessageIdHeaderOnly_resolvesToHeaderValue(@ForAll("paramValues") String value) {
        Map<String, String> matrixParams = new HashMap<>();

        Map<String, String> headers = new HashMap<>();
        headers.put("deleteMessageId", value);

        ResolvedParams result = ParameterResolver.resolve(matrixParams, headers);

        assertThat(result.deleteMessageId()).isEqualTo(value);
    }

    /**
     * When deleteMessageId is present in both sources with the same value,
     * resolved deleteMessageId equals that value.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 4: Resolver deterministic merge")
    void deleteMessageIdBothSame_resolvesToThatValue(@ForAll("paramValues") String value) {
        Map<String, String> matrixParams = new HashMap<>();
        matrixParams.put("messageId", value);

        Map<String, String> headers = new HashMap<>();
        headers.put("deleteMessageId", value);

        ResolvedParams result = ParameterResolver.resolve(matrixParams, headers);

        assertThat(result.deleteMessageId()).isEqualTo(value);
    }

    /**
     * When deleteMessageId is absent from both sources, resolved deleteMessageId is null.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 4: Resolver deterministic merge")
    void deleteMessageIdBothAbsent_resolvesToNull() {
        Map<String, String> matrixParams = new HashMap<>();
        Map<String, String> headers = new HashMap<>();

        ResolvedParams result = ParameterResolver.resolve(matrixParams, headers);

        assertThat(result.deleteMessageId()).isNull();
    }

    // ========== Empty string treated as absent ==========

    /**
     * When matrix param has empty string value, it is treated as absent —
     * header value wins if present.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 4: Resolver deterministic merge")
    void emptyMatrixValueTreatedAsAbsent_headerValueWins(@ForAll("paramValues") String headerValue) {
        Map<String, String> matrixParams = new HashMap<>();
        matrixParams.put("zoneId", "");

        Map<String, String> headers = new HashMap<>();
        headers.put("zoneId", headerValue);

        ResolvedParams result = ParameterResolver.resolve(matrixParams, headers);

        assertThat(result.zoneId()).isEqualTo(headerValue);
    }

    /**
     * When header has empty string value, it is treated as absent —
     * matrix value wins if present.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 4: Resolver deterministic merge")
    void emptyHeaderValueTreatedAsAbsent_matrixValueWins(@ForAll("paramValues") String matrixValue) {
        Map<String, String> matrixParams = new HashMap<>();
        matrixParams.put("zoneId", matrixValue);

        Map<String, String> headers = new HashMap<>();
        headers.put("zoneId", "");

        ResolvedParams result = ParameterResolver.resolve(matrixParams, headers);

        assertThat(result.zoneId()).isEqualTo(matrixValue);
    }

    /**
     * When both sources have empty string values, resolved value is null.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 4: Resolver deterministic merge")
    void bothEmptyValues_resolvesToNull() {
        Map<String, String> matrixParams = new HashMap<>();
        matrixParams.put("zoneId", "");

        Map<String, String> headers = new HashMap<>();
        headers.put("zoneId", "");

        ResolvedParams result = ParameterResolver.resolve(matrixParams, headers);

        assertThat(result.zoneId()).isNull();
    }
}
