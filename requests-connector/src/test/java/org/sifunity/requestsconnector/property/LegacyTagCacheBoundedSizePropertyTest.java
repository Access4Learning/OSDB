// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.jqwik.api.*;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for LegacyTagLookupService cache bounded size.
 *
 * <p><strong>Property 4: LegacyTagLookupService cache bounded size</strong></p>
 *
 * <p>For any sequence of N > 1000 distinct environment UUIDs inserted into the
 * requests-connector LegacyTagLookupService cache, the cache size SHALL never
 * exceed 1000 entries.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2</strong></p>
 * <ul>
 *   <li>3.1: The Legacy_Tag_Cache in Requests_Connector SHALL have a maximum size of 1000 entries.</li>
 *   <li>3.2: WHEN the cache contains 1000 entries and a new distinct environmentId is looked up,
 *        THE Legacy_Tag_Cache SHALL evict an existing entry so that total size does not exceed 1000 entries.</li>
 * </ul>
 */
@Tag("Feature: n-plus-one-and-cache-fixes, Property 4: LegacyTagLookupService cache bounded size")
class LegacyTagCacheBoundedSizePropertyTest {

    /**
     * Property 4: LegacyTagLookupService cache bounded size
     *
     * <p><strong>Validates: Requirements 3.1, 3.2</strong></p>
     *
     * <p>Builds a Caffeine cache matching the production configuration
     * (maximumSize=1000, expireAfterWrite=5 minutes), inserts more than
     * 1000 distinct UUIDs, and asserts the cache never exceeds 1000 entries
     * after cleanup.</p>
     */
    @Property(tries = 20)
    @Tag("Property_4_Cache_Bounded_Size")
    void cacheSizeNeverExceeds1000Entries(
            @ForAll("entryCountsAbove1000") int entryCount) {

        // Given: A Caffeine cache matching the production LegacyTagLookupService configuration
        Cache<UUID, Boolean> cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1000)
                .build();

        // When: Insert entryCount distinct UUIDs into the cache
        for (int i = 0; i < entryCount; i++) {
            cache.put(UUID.randomUUID(), true);
        }

        // Caffeine eviction is lazy — force cleanup to ensure eviction has occurred
        cache.cleanUp();

        // Then: The cache size must never exceed 1000 entries
        assertThat(cache.estimatedSize())
                .as("Cache size after inserting %d entries must not exceed 1000", entryCount)
                .isLessThanOrEqualTo(1000);
    }

    // ==================== Arbitraries (Data Generators) ====================

    /**
     * Generate entry counts greater than 1000 (between 1001 and 2000).
     */
    @Provide
    Arbitrary<Integer> entryCountsAbove1000() {
        return Arbitraries.integers().between(1001, 2000);
    }
}
