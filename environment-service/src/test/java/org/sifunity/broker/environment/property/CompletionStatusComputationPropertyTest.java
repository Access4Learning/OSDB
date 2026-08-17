// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.environment.domain.CompletionStatus;
import org.sifunity.broker.environment.service.ProvisionRequestService;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 2: CompletionStatus computation.
 *
 * <p>For any non-empty set of resolved right assertion values (each either APPROVED or REJECTED),
 * the computed completionStatus SHALL be ACCEPTED when all values are APPROVED, REJECTED when
 * all values are REJECTED, and MIXED otherwise.</p>
 *
 * <p><strong>Validates: Requirements 2.6, 5.2</strong></p>
 */
@Tag("Feature: provision-requests, Property 2: CompletionStatus computation")
class CompletionStatusComputationPropertyTest {

    /**
     * All APPROVED values → ACCEPTED.
     *
     * <p><strong>Validates: Requirements 2.6, 5.2</strong></p>
     */
    @Property(tries = 20)
    void allApproved_returnsAccepted(
            @ForAll("approvedOnlyLists") List<String> rightValues) {

        CompletionStatus result = ProvisionRequestService.computeCompletionStatus(rightValues);

        assertThat(result)
                .as("All APPROVED values must produce ACCEPTED, got %s for input %s", result, rightValues)
                .isEqualTo(CompletionStatus.ACCEPTED);
    }

    /**
     * All REJECTED values → REJECTED.
     *
     * <p><strong>Validates: Requirements 2.6, 5.2</strong></p>
     */
    @Property(tries = 20)
    void allRejected_returnsRejected(
            @ForAll("rejectedOnlyLists") List<String> rightValues) {

        CompletionStatus result = ProvisionRequestService.computeCompletionStatus(rightValues);

        assertThat(result)
                .as("All REJECTED values must produce REJECTED, got %s for input %s", result, rightValues)
                .isEqualTo(CompletionStatus.REJECTED);
    }

    /**
     * Mixed APPROVED and REJECTED values → MIXED.
     *
     * <p><strong>Validates: Requirements 2.6, 5.2</strong></p>
     */
    @Property(tries = 20)
    void mixedValues_returnsMixed(
            @ForAll("mixedLists") List<String> rightValues) {

        CompletionStatus result = ProvisionRequestService.computeCompletionStatus(rightValues);

        assertThat(result)
                .as("Mixed values must produce MIXED, got %s for input %s", result, rightValues)
                .isEqualTo(CompletionStatus.MIXED);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<List<String>> approvedOnlyLists() {
        return Arbitraries.integers().between(1, 20)
                .map(size -> {
                    List<String> list = new ArrayList<>();
                    for (int i = 0; i < size; i++) {
                        list.add("APPROVED");
                    }
                    return list;
                });
    }

    @Provide
    Arbitrary<List<String>> rejectedOnlyLists() {
        return Arbitraries.integers().between(1, 20)
                .map(size -> {
                    List<String> list = new ArrayList<>();
                    for (int i = 0; i < size; i++) {
                        list.add("REJECTED");
                    }
                    return list;
                });
    }

    @Provide
    Arbitrary<List<String>> mixedLists() {
        // Generate at least 1 APPROVED and 1 REJECTED, then fill rest randomly
        return Arbitraries.integers().between(2, 20).flatMap(size ->
                Arbitraries.of("APPROVED", "REJECTED").list().ofSize(size)
                        .filter(list -> list.contains("APPROVED") && list.contains("REJECTED"))
        );
    }
}
