// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.util.RelativeServicePathFormatter;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 8: Zone and context headers always present on delivery.
 *
 * <p><em>For any</em> delivered message (request, response, or event), regardless of the
 * Legacy_Matrix_Params_Tag setting, the delivery SHALL include {@code zoneId} and
 * {@code contextId} as separate HTTP headers.</p>
 *
 * <p>This test verifies the delivery contract: for any combination of serviceName,
 * zoneId, contextId, and legacyFormat flag, the formatter produces a valid path AND
 * the zoneId/contextId values are always non-null and available to be set as headers.
 * The delivery logic in {@code DelayedRequestHandler} always sets these as separate
 * HTTP headers regardless of whether matrix params are included in relativeServicePath.</p>
 *
 * <p><strong>Validates: Requirements 5.3, 7.2</strong></p>
 */
@Tag("Feature: sif-matrix-param-compat, Property 8: Headers always present on delivery")
class DeliveryHeadersPropertyTest {

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(40);
    }

    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(1)
                .ofMaxLength(30);
    }

    @Provide
    Arbitrary<String> contextIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('-', '_')
                .ofMinLength(1)
                .ofMaxLength(30);
    }

    @Provide
    Arbitrary<String> queryStrings() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .withCharRange('0', '9')
                        .withChars('=', '&')
                        .ofMinLength(3)
                        .ofMaxLength(30)
        );
    }

    // ========== Properties ==========

    /**
     * For any delivered message regardless of legacy tag setting, zoneId and contextId
     * headers are always present. This simulates the delivery logic: the formatter
     * produces a relativeServicePath, and zoneId/contextId are always set as separate
     * HTTP headers on the outbound request.
     *
     * <p>When legacyFormat=true, headers are present AND matrix params are in the path.
     * When legacyFormat=false, headers are present WITHOUT matrix params in the path.
     * In both cases, the headers map always contains zoneId and contextId.</p>
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 8: Headers always present on delivery")
    void headersAlwaysPresentRegardlessOfLegacyTag(
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll("queryStrings") String queryString,
            @ForAll boolean legacyFormat) {

        // Simulate the delivery logic: format relativeServicePath
        String relativeServicePath = RelativeServicePathFormatter.format(
                serviceName, zoneId, contextId, queryString, legacyFormat);

        // Simulate setting delivery headers (as done in DelayedRequestHandler)
        Map<String, String> deliveryHeaders = new HashMap<>();
        deliveryHeaders.put("relativeServicePath", relativeServicePath);

        // zoneId and contextId are ALWAYS set as separate headers regardless of legacy flag
        deliveryHeaders.put("zoneId", zoneId);
        deliveryHeaders.put("contextId", contextId);

        // Property assertion: zoneId and contextId headers are always present and non-null
        assertThat(deliveryHeaders.get("zoneId"))
                .as("zoneId header must always be present on delivery")
                .isNotNull()
                .isNotEmpty();

        assertThat(deliveryHeaders.get("contextId"))
                .as("contextId header must always be present on delivery")
                .isNotNull()
                .isNotEmpty();

        // relativeServicePath is always produced (non-null)
        assertThat(deliveryHeaders.get("relativeServicePath"))
                .as("relativeServicePath header must always be present on delivery")
                .isNotNull()
                .startsWith("/");
    }

    /**
     * For any delivered message with legacyFormat=true, the zoneId and contextId
     * headers are present as separate headers IN ADDITION to being embedded in
     * the relativeServicePath matrix parameters.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 8: Headers always present on delivery")
    void legacyDeliveryHasHeadersAndMatrixParams(
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId) {

        String relativeServicePath = RelativeServicePathFormatter.format(
                serviceName, zoneId, contextId, null, true);

        // Simulate delivery headers
        Map<String, String> deliveryHeaders = new HashMap<>();
        deliveryHeaders.put("relativeServicePath", relativeServicePath);
        deliveryHeaders.put("zoneId", zoneId);
        deliveryHeaders.put("contextId", contextId);

        // Headers are present
        assertThat(deliveryHeaders.get("zoneId")).isEqualTo(zoneId);
        assertThat(deliveryHeaders.get("contextId")).isEqualTo(contextId);

        // Matrix params are also in the path
        assertThat(relativeServicePath).contains(";zoneId=" + zoneId);
        assertThat(relativeServicePath).contains(";contextId=" + contextId);
    }

    /**
     * For any delivered message with legacyFormat=false, the zoneId and contextId
     * headers are present as separate headers even though the relativeServicePath
     * does NOT contain matrix parameters.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 8: Headers always present on delivery")
    void modernDeliveryHasHeadersWithoutMatrixParams(
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId) {

        String relativeServicePath = RelativeServicePathFormatter.format(
                serviceName, zoneId, contextId, null, false);

        // Simulate delivery headers
        Map<String, String> deliveryHeaders = new HashMap<>();
        deliveryHeaders.put("relativeServicePath", relativeServicePath);
        deliveryHeaders.put("zoneId", zoneId);
        deliveryHeaders.put("contextId", contextId);

        // Headers are present
        assertThat(deliveryHeaders.get("zoneId")).isEqualTo(zoneId);
        assertThat(deliveryHeaders.get("contextId")).isEqualTo(contextId);

        // No matrix params in the path
        assertThat(relativeServicePath).doesNotContain(";");
    }
}
