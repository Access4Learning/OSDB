// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.exception.InvalidParameterException;
import org.sifunity.broker.common.util.ValidationUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bug Condition Exploration Test for Null UUID Handling.
 *
 * <p><strong>CRITICAL: These tests MUST FAIL on unfixed code — failure confirms the bug exists.</strong></p>
 *
 * <p>Bug: {@code ValidationUtils.validateUuid()} calls {@code UUID.fromString(value)} which
 * throws {@code NullPointerException} for null input — the catch only handles
 * {@code IllegalArgumentException}.</p>
 *
 * <p>Bug Condition: {@code validateUuid(null, "field")} throws {@code InvalidParameterException},
 * not NPE.</p>
 *
 * <p>On UNFIXED code: test FAILS with NullPointerException (instead of InvalidParameterException).</p>
 * <p>On FIXED code: test PASSES because null check added before UUID.fromString().</p>
 *
 * <p><strong>Validates: Requirements 1.12</strong></p>
 */
class NullUuidBugConditionExplorationTest {

    /**
     * Property: validateUuid(null, fieldName) SHALL throw InvalidParameterException, not NPE.
     *
     * <p>On UNFIXED code: UUID.fromString(null) throws NullPointerException which is not
     * caught by the IllegalArgumentException catch block — test FAILS.</p>
     * <p>On FIXED code: null check throws InvalidParameterException — test PASSES.</p>
     */
    @Property(tries = 20)
    @Label("validateUuid(null, fieldName) SHALL throw InvalidParameterException, not NullPointerException")
    void validateUuidWithNullShallThrowInvalidParameterException(
            @ForAll("fieldNames") String fieldName) {

        assertThatThrownBy(() -> ValidationUtils.validateUuid(null, fieldName))
                .as("validateUuid(null, '%s') should throw InvalidParameterException, " +
                    "but on UNFIXED code it throws NullPointerException because " +
                    "UUID.fromString(null) throws NPE which is not caught", fieldName)
                .isInstanceOf(InvalidParameterException.class);
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> fieldNames() {
        return Arbitraries.of(
                "environmentId",
                "solutionId",
                "domainId",
                "zoneId",
                "queueId",
                "subscriptionId",
                "requestId"
        );
    }
}
