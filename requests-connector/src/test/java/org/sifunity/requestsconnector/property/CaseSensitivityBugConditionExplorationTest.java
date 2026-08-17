// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import net.jqwik.api.*;
import org.sifunity.requestsconnector.client.EnvironmentServiceClient;
import org.sifunity.requestsconnector.service.LegacyTagLookupService;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug Condition Exploration Test for Case Sensitivity in LegacyTagLookupService.
 *
 * <p><strong>CRITICAL: These tests MUST FAIL on unfixed code — failure confirms the bug exists.</strong></p>
 *
 * <p>Bug: {@code LegacyTagLookupService} at line 83 uses {@code "true"::equals}
 * (case-sensitive) while responses-connector uses {@code "true"::equalsIgnoreCase},
 * causing inconsistent behavior for mixed-case tag names like "TRUE" or "True".</p>
 *
 * <p>Bug Condition: For tag values that are a case-insensitive match to "true" (e.g.,
 * "TRUE", "True"), LegacyTagLookupService returns true.</p>
 *
 * <p>On UNFIXED code: test FAILS because {@code "true"::equals} is case-sensitive
 * and rejects "TRUE", "True", etc.</p>
 * <p>On FIXED code: test PASSES because {@code "true"::equalsIgnoreCase} matches all variants.</p>
 *
 * <p><strong>Validates: Requirements 1.13</strong></p>
 */
class CaseSensitivityBugConditionExplorationTest {

    /**
     * Property: For tag values that are a case-insensitive match to "true" (but NOT
     * exactly "true"), isLegacyEnvironment SHALL return true.
     *
     * <p>On UNFIXED code: "TRUE", "True", "TrUe" etc. are not matched by
     * {@code "true"::equals} — returns false — test FAILS.</p>
     * <p>On FIXED code: {@code "true"::equalsIgnoreCase} matches all variants — test PASSES.</p>
     */
    @Property(tries = 20)
    @Label("isLegacyEnvironment SHALL return true for all case-insensitive variants of 'true'")
    void isLegacyEnvironmentShallBeCaseInsensitive(
            @ForAll("mixedCaseTrueValues") String tagValue) {

        // Mock the EnvironmentServiceClient to return the mixed-case tag value
        EnvironmentServiceClient client = mock(EnvironmentServiceClient.class);
        UUID environmentId = UUID.randomUUID();
        String domainId = "test-domain";
        String solutionId = "test-solution";

        when(client.getEntityTagValue(
                eq(domainId), eq(solutionId), eq("ENVIRONMENT"), eq(environmentId), eq("SIF_LEGACY_MATRIX_PARAMS")))
                .thenReturn(Optional.of(tagValue));

        LegacyTagLookupService service = new LegacyTagLookupService(client, Duration.ofMinutes(5));

        boolean result = service.isLegacyEnvironment(domainId, solutionId, environmentId);

        assertThat(result)
                .as("isLegacyEnvironment should return true for tag value '%s' " +
                    "(case-insensitive match to 'true'), but on UNFIXED code " +
                    "\"true\"::equals is case-sensitive and returns false", tagValue)
                .isTrue();
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> mixedCaseTrueValues() {
        // Generate mixed-case variants of "true" that are NOT exactly "true"
        return Arbitraries.of(
                "TRUE",
                "True",
                "TrUe",
                "tRUE",
                "truE",
                "tRue",
                "TRue",
                "trUE"
        );
    }
}
