// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.util.RelativeServicePathFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 7: Outbound formatting controlled by legacy tag.
 *
 * <p><em>For any</em> service name, zone ID, and context ID, when legacyFormat=true the
 * {@code relativeServicePath} SHALL contain {@code ;contextId=} and {@code ;zoneId=}.
 * When legacyFormat=false, the output SHALL contain no semicolons.</p>
 *
 * <p><strong>Validates: Requirements 5.1, 5.2, 6.2</strong></p>
 */
class RelativeServicePathFormatterPropertyTest {

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
     * When legacyFormat=true, output contains ;contextId= and ;zoneId=.
     * Output always starts with /.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 7: Outbound formatting controlled by legacy tag")
    void legacyFormatContainsMatrixParameters(
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId) {

        String result = RelativeServicePathFormatter.format(serviceName, zoneId, contextId, null, true);

        assertThat(result).startsWith("/");
        assertThat(result).contains(";contextId=");
        assertThat(result).contains(";zoneId=");
    }

    /**
     * When legacyFormat=false, output has no semicolons.
     * Output always starts with /.
     */
    @Property(tries = 50)
    @Tag("Feature: sif-matrix-param-compat, Property 7: Outbound formatting controlled by legacy tag")
    void modernFormatContainsNoSemicolons(
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId) {

        String result = RelativeServicePathFormatter.format(serviceName, zoneId, contextId, null, false);

        assertThat(result).startsWith("/");
        assertThat(result).doesNotContain(";");
    }
}
