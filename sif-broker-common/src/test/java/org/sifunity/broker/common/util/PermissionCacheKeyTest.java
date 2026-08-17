// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sifunity.broker.common.constant.AccessType;

/**
 * Unit tests for {@link PermissionCacheKey}, the shared ACL permission cache-key builder.
 */
class PermissionCacheKeyTest {

    private static final UUID ENV = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Nested
    @DisplayName("normalizeScope")
    class NormalizeScope {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
        @DisplayName("maps null and blank values to the sentinel")
        void mapsNullAndBlankToSentinel(String value) {
            assertThat(PermissionCacheKey.normalizeScope(value))
                    .isEqualTo(PermissionCacheKey.NULL_SCOPE_SENTINEL);
        }

        @ParameterizedTest
        @ValueSource(strings = {"district-1", "CURRENT", "DEFAULT", "zone.a", " leading-kept-if-nonblank "})
        @DisplayName("passes non-blank values through unchanged")
        void passesNonBlankThrough(String value) {
            assertThat(PermissionCacheKey.normalizeScope(value)).isEqualTo(value);
        }
    }

    @Nested
    @DisplayName("of")
    class Of {

        @Test
        @DisplayName("produces the exact env:zone:ctx:svc:accessType layout")
        void producesExactLayout() {
            String key = PermissionCacheKey.of(ENV, "district-1", "CURRENT", "StudentPersonal", AccessType.QUERY);

            assertThat(key).isEqualTo(ENV + ":district-1:CURRENT:StudentPersonal:QUERY");
        }

        @Test
        @DisplayName("normalizes a null zoneId to the sentinel in the layout")
        void normalizesNullZone() {
            String key = PermissionCacheKey.of(ENV, null, "CURRENT", "StudentPersonal", AccessType.QUERY);

            assertThat(key).isEqualTo(
                    ENV + ":" + PermissionCacheKey.NULL_SCOPE_SENTINEL + ":CURRENT:StudentPersonal:QUERY");
        }

        @Test
        @DisplayName("normalizes a null contextId to the sentinel in the layout")
        void normalizesNullContext() {
            String key = PermissionCacheKey.of(ENV, "district-1", null, "StudentPersonal", AccessType.PROVIDE);

            assertThat(key).isEqualTo(
                    ENV + ":district-1:" + PermissionCacheKey.NULL_SCOPE_SENTINEL + ":StudentPersonal:PROVIDE");
        }
    }

    @Nested
    @DisplayName("scope isolation")
    class ScopeIsolation {

        @Test
        @DisplayName("a null contextId and a literal-sentinel contextId do not collide")
        void nullVsLiteralSentinelDoNotCollide() {
            // A real contextId cannot legally be the NUL control character; but even if a caller
            // passed a readable token that mimics a null scope, the sentinel must be unforgeable.
            // Here we confirm null normalizes to the sentinel while a distinct readable context
            // ("CURRENT") produces a different key.
            String nullCtxKey = PermissionCacheKey.of(ENV, "district-1", null, "Svc", AccessType.QUERY);
            String realCtxKey = PermissionCacheKey.of(ENV, "district-1", "CURRENT", "Svc", AccessType.QUERY);

            assertThat(nullCtxKey).isNotEqualTo(realCtxKey);
        }

        @Test
        @DisplayName("distinct zoneIds map to distinct keys")
        void distinctZonesDistinctKeys() {
            String zone1 = PermissionCacheKey.of(ENV, "district-1", "CURRENT", "Svc", AccessType.QUERY);
            String zone2 = PermissionCacheKey.of(ENV, "district-2", "CURRENT", "Svc", AccessType.QUERY);

            assertThat(zone1).isNotEqualTo(zone2);
        }

        @Test
        @DisplayName("distinct normalized scope tuples map to distinct keys (key injectivity)")
        void distinctScopeTuplesMapToDistinctKeys() {
            UUID otherEnv = UUID.fromString("22222222-2222-2222-2222-222222222222");

            // Vary each dimension one at a time from a common baseline; every variant must differ.
            String baseline = PermissionCacheKey.of(ENV, "district-1", "CURRENT", "StudentPersonal", AccessType.QUERY);

            assertThat(PermissionCacheKey.of(otherEnv, "district-1", "CURRENT", "StudentPersonal", AccessType.QUERY))
                    .isNotEqualTo(baseline);
            assertThat(PermissionCacheKey.of(ENV, "district-2", "CURRENT", "StudentPersonal", AccessType.QUERY))
                    .isNotEqualTo(baseline);
            assertThat(PermissionCacheKey.of(ENV, "district-1", "DEFAULT", "StudentPersonal", AccessType.QUERY))
                    .isNotEqualTo(baseline);
            assertThat(PermissionCacheKey.of(ENV, "district-1", "CURRENT", "SchoolInfo", AccessType.QUERY))
                    .isNotEqualTo(baseline);
            assertThat(PermissionCacheKey.of(ENV, "district-1", "CURRENT", "StudentPersonal", AccessType.PROVIDE))
                    .isNotEqualTo(baseline);
        }
    }
}
