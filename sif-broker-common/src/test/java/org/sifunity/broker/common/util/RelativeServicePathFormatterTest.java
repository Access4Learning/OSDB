// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RelativeServicePathFormatter}.
 *
 * Validates Requirements 5.1, 5.2, 5.4.
 */
class RelativeServicePathFormatterTest {

    @Test
    void legacyFormat_includesMatrixParams() {
        String result = RelativeServicePathFormatter.format(
                "StudentPersonals", "zone1", "DEFAULT", null, true);

        assertThat(result).isEqualTo("/StudentPersonals;contextId=DEFAULT;zoneId=zone1");
    }

    @Test
    void modernFormat_noMatrixParams() {
        String result = RelativeServicePathFormatter.format(
                "StudentPersonals", "zone1", "DEFAULT", null, false);

        assertThat(result).isEqualTo("/StudentPersonals");
    }

    @Test
    void legacyFormat_withQueryString() {
        String result = RelativeServicePathFormatter.format(
                "StudentPersonals", "zone1", "DEFAULT", "where=LastName%3D'Smith'", true);

        assertThat(result).isEqualTo("/StudentPersonals;contextId=DEFAULT;zoneId=zone1?where=LastName%3D'Smith'");
    }

    @Test
    void modernFormat_withQueryString() {
        String result = RelativeServicePathFormatter.format(
                "StudentPersonals", "zone1", "DEFAULT", "where=LastName%3D'Smith'", false);

        assertThat(result).isEqualTo("/StudentPersonals?where=LastName%3D'Smith'");
    }

    @Test
    void pathAlwaysStartsWithSlash() {
        String legacy = RelativeServicePathFormatter.format("Svc", "z", "c", null, true);
        String modern = RelativeServicePathFormatter.format("Svc", "z", "c", null, false);

        assertThat(legacy).startsWith("/");
        assertThat(modern).startsWith("/");
    }

    @Test
    void nullQueryString_noQuestionMark() {
        String result = RelativeServicePathFormatter.format(
                "StudentPersonals", "zone1", "DEFAULT", null, true);

        assertThat(result).doesNotContain("?");
    }

    @Test
    void emptyQueryString_noQuestionMark() {
        String result = RelativeServicePathFormatter.format(
                "StudentPersonals", "zone1", "DEFAULT", "", true);

        assertThat(result).doesNotContain("?");
    }

    @Test
    void legacyFormat_contextIdBeforeZoneId() {
        String result = RelativeServicePathFormatter.format(
                "SchoolInfos", "myZone", "myContext", null, true);

        int contextIdx = result.indexOf(";contextId=");
        int zoneIdx = result.indexOf(";zoneId=");
        assertThat(contextIdx).isLessThan(zoneIdx);
    }

    @Test
    void nullServiceName_stillStartsWithSlash() {
        String result = RelativeServicePathFormatter.format(null, "z", "c", null, false);

        assertThat(result).isEqualTo("/");
    }

    @Test
    void nullZoneAndContext_legacyFormat_emptyValues() {
        String result = RelativeServicePathFormatter.format(
                "StudentPersonals", null, null, null, true);

        assertThat(result).isEqualTo("/StudentPersonals;contextId=;zoneId=");
    }
}
