// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.exception.InvalidParameterException;
import org.sifunity.broker.common.util.ValidationUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Preservation Property Test for UUID Validation.
 *
 * <p><strong>IMPORTANT: These tests encode current CORRECT behavior that must survive the fix.</strong></p>
 *
 * <p>Property: For all non-null strings, validateUuid behavior unchanged — valid UUIDs pass,
 * invalid throw InvalidParameterException.</p>
 *
 * <p>These tests MUST PASS on both unfixed and fixed code, confirming that the null guard
 * addition does not change behavior for non-null inputs.</p>
 *
 * <p><strong>Validates: Requirements 3.7, 3.8</strong></p>
 */
class UuidValidationPreservationTest {

    /**
     * Property: For all valid UUID strings, validateUuid succeeds without exception.
     */
    @Property(tries = 20)
    @Label("validateUuid SHALL pass for all valid UUID strings (non-null)")
    void validateUuidShallPassForValidUuids(
            @ForAll("validUuids") String validUuid) {

        // Act - should not throw
        ValidationUtils.validateUuid(validUuid, "testField");

        // If we get here, validation passed — that's the expected behavior
        assertThat(validUuid)
                .as("Valid UUID '%s' should pass validation without exception", validUuid)
                .isNotNull();
    }

    /**
     * Property: For all invalid (non-null, non-UUID) strings, validateUuid throws
     * InvalidParameterException.
     */
    @Property(tries = 20)
    @Label("validateUuid SHALL throw InvalidParameterException for invalid non-null strings")
    void validateUuidShallRejectInvalidStrings(
            @ForAll("invalidUuids") String invalidUuid) {

        assertThatThrownBy(() -> ValidationUtils.validateUuid(invalidUuid, "testField"))
                .as("Invalid UUID string '%s' should throw InvalidParameterException", invalidUuid)
                .isInstanceOf(InvalidParameterException.class);
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> validUuids() {
        // Generate valid UUID strings
        return Arbitraries.randomValue(random -> UUID.randomUUID().toString());
    }

    @Provide
    Arbitrary<String> invalidUuids() {
        // Mix of various invalid UUID strings (all non-null)
        return Arbitraries.of(
                "not-a-uuid",
                "12345",
                "abc-def-ghi",
                "",
                "550e8400-e29b-41d4-a716",
                "ZZZZZZZZ-ZZZZ-ZZZZ-ZZZZ-ZZZZZZZZZZZZ",
                "hello world",
                "550e8400e29b41d4a716446655440000",
                "550e8400-e29b-41d4-a716-44665544000g",
                "this-is-definitely-not-valid",
                "   ",
                "null",
                "undefined"
        );
    }
}
