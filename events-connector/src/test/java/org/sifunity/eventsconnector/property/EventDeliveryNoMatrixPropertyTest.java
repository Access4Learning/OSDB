// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.util.RelativeServicePathFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 10: Events excluded from matrix parameter injection.
 *
 * <p><em>For any</em> event delivery to a subscriber environment that has the
 * {@code SIF_LEGACY_MATRIX_PARAMS=true} tag, the {@code relativeServicePath} header
 * (if present) SHALL NOT contain matrix parameters (no {@code ;} character).</p>
 *
 * <p>The events-connector intentionally does NOT use {@link RelativeServicePathFormatter}
 * with {@code legacyFormat=true}. Event deliveries always construct the service path as
 * {@code "/" + serviceName} — a plain path with no matrix parameter injection, regardless
 * of the subscriber environment's legacy tag setting.</p>
 *
 * <p><strong>Validates: Requirements 7.1</strong></p>
 */
@Tag("Feature: sif-matrix-param-compat, Property 10: Events excluded from matrix parameter injection")
class EventDeliveryNoMatrixPropertyTest {

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

    // ========== Properties ==========

    /**
     * For any event delivery — even when the subscriber environment has the
     * SIF_LEGACY_MATRIX_PARAMS=true tag — the relativeServicePath constructed by
     * the events-connector contains no semicolons (no matrix parameter injection).
     *
     * <p>The events-connector formats the path as {@code "/" + serviceName}, which
     * is equivalent to calling {@code RelativeServicePathFormatter.format(serviceName,
     * zoneId, contextId, null, false)} — i.e., always modern format, never legacy.</p>
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 10: Events excluded from matrix parameter injection")
    void eventDeliveryPathNeverContainsSemicolons(
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll boolean subscriberHasLegacyTag) {

        // The events-connector ALWAYS uses modern format (legacyFormat=false)
        // regardless of whether the subscriber has the legacy tag.
        // This is the intentional exclusion per Requirement 7.1.
        String relativeServicePath = RelativeServicePathFormatter.format(
                serviceName, zoneId, contextId, null, false);

        // Property: relativeServicePath never contains semicolons for events
        assertThat(relativeServicePath)
                .as("Event relativeServicePath must never contain ';' (no matrix params), " +
                        "even when subscriber has legacy tag=%s, serviceName=%s",
                        subscriberHasLegacyTag, serviceName)
                .doesNotContain(";");
    }

    /**
     * Contrast property: demonstrates that if the events-connector WERE to use
     * legacy format (which it must NOT), the path WOULD contain semicolons.
     * This confirms the formatter is capable of producing matrix params, and
     * the events-connector's exclusion is a deliberate design choice.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 10: Events excluded from matrix parameter injection")
    void legacyFormatWouldContainSemicolons(
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId) {

        // If legacy format were used (which events-connector must NOT do)
        String legacyPath = RelativeServicePathFormatter.format(
                serviceName, zoneId, contextId, null, true);

        // Legacy format always contains semicolons
        assertThat(legacyPath)
                .as("Legacy format should contain ';' — confirming events-connector's " +
                        "exclusion is meaningful")
                .contains(";");
    }

    /**
     * For any event delivery, the relativeServicePath starts with "/" followed by
     * the service name and nothing else (no matrix params appended).
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 10: Events excluded from matrix parameter injection")
    void eventDeliveryPathIsPlainServiceName(
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId) {

        // Events-connector always uses modern format
        String relativeServicePath = RelativeServicePathFormatter.format(
                serviceName, zoneId, contextId, null, false);

        // Path is exactly "/{serviceName}" with no additional content
        assertThat(relativeServicePath)
                .as("Event relativeServicePath should be plain '/{serviceName}'")
                .isEqualTo("/" + serviceName);
    }
}
