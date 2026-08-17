// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.GlobalAdminConstants;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 5: isGlobalAdmin Correctness.
 *
 * <p>For any pair of strings {@code (domainId, solutionId)},
 * {@link GlobalAdminConstants#isGlobalAdmin(String, String)} returns {@code true}
 * if and only if {@code domainId} equals {@link GlobalAdminConstants#GLOBAL_DOMAIN}
 * AND {@code solutionId} equals {@link GlobalAdminConstants#GLOBAL_SOLUTION}.
 * For all other combinations (including null values), it returns {@code false}.</p>
 *
 * <p><strong>Validates: Requirements 4.3</strong></p>
 */
class IsGlobalAdminPropertyTest {

    // ========== Arbitraries ==========

    /**
     * Generates arbitrary nullable strings for domain/solution IDs.
     * Includes null, empty, whitespace, and random alphanumeric strings,
     * as well as the exact constant values to ensure edge coverage.
     */
    @Provide
    Arbitrary<String> nullableStrings() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.just(" "),
                Arbitraries.just(GlobalAdminConstants.GLOBAL_DOMAIN),
                Arbitraries.just(GlobalAdminConstants.GLOBAL_SOLUTION),
                Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(50)
        );
    }

    // ========== Properties ==========

    /**
     * For any pair of nullable strings, isGlobalAdmin returns true
     * if and only if both match the global admin constants exactly.
     */
    @Property(tries = 200)
    @Tag("Feature: global-admin-bootstrap, Property 5: isGlobalAdmin Correctness")
    void isGlobalAdminReturnsTrueIffBothMatch(
            @ForAll("nullableStrings") String domainId,
            @ForAll("nullableStrings") String solutionId) {

        boolean result = GlobalAdminConstants.isGlobalAdmin(domainId, solutionId);

        boolean expectedMatch =
                GlobalAdminConstants.GLOBAL_DOMAIN.equals(domainId)
                        && GlobalAdminConstants.GLOBAL_SOLUTION.equals(solutionId);

        assertThat(result)
                .as("isGlobalAdmin(%s, %s) should be %s", domainId, solutionId, expectedMatch)
                .isEqualTo(expectedMatch);
    }

    /**
     * Null domainId always yields false, regardless of solutionId.
     */
    @Property(tries = 100)
    @Tag("Feature: global-admin-bootstrap, Property 5: isGlobalAdmin Correctness")
    void nullDomainAlwaysReturnsFalse(
            @ForAll("nullableStrings") String solutionId) {

        assertThat(GlobalAdminConstants.isGlobalAdmin(null, solutionId))
                .as("isGlobalAdmin(null, %s) should be false", solutionId)
                .isFalse();
    }

    /**
     * Null solutionId always yields false, regardless of domainId.
     */
    @Property(tries = 100)
    @Tag("Feature: global-admin-bootstrap, Property 5: isGlobalAdmin Correctness")
    void nullSolutionAlwaysReturnsFalse(
            @ForAll("nullableStrings") String domainId) {

        assertThat(GlobalAdminConstants.isGlobalAdmin(domainId, null))
                .as("isGlobalAdmin(%s, null) should be false", domainId)
                .isFalse();
    }

    /**
     * The exact constant pair always returns true.
     */
    @Example
    @Tag("Feature: global-admin-bootstrap, Property 5: isGlobalAdmin Correctness")
    void exactConstantsAlwaysReturnTrue() {
        assertThat(GlobalAdminConstants.isGlobalAdmin(
                GlobalAdminConstants.GLOBAL_DOMAIN,
                GlobalAdminConstants.GLOBAL_SOLUTION))
                .isTrue();
    }
}
