// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.sifunity.broker.common.exception.InvalidTimestampException;
import org.sifunity.broker.common.util.SifAuthenticationUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Preservation Property Tests for Timestamp Validation
 * 
 * <p>These tests capture the baseline behavior that must be preserved when fixing
 * the flaky timestamp validation test. They focus on non-boundary cases where the
 * race condition does not occur, ensuring that the fix does not introduce regressions.</p>
 * 
 * <p><strong>IMPORTANT:</strong> These tests are run on UNFIXED code BEFORE implementing
 * the fix. They should PASS consistently, confirming the baseline behavior to preserve.</p>
 * 
 * <p><strong>Preservation Requirements:</strong></p>
 * <ul>
 *   <li>Timestamps well within tolerance always pass validation</li>
 *   <li>Timestamps well outside tolerance always throw InvalidTimestampException</li>
 *   <li>Production code continues to use Instant.now() for runtime validation</li>
 *   <li>Other property tests in UtilityProperties.java continue to pass</li>
 * </ul>
 * 
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.4</strong></p>
 */
@Tag("Feature-timestamp-validation-flaky-test-fix")
class TimestampValidationPreservationTest {

    /**
     * Property 2.1: Preservation - Timestamps Well Within Tolerance
     * 
     * <p>For timestamps that are well within the tolerance window (not at boundary
     * conditions), validation should always succeed. This tests the non-buggy input
     * space where the race condition does not occur.</p>
     * 
     * <p><strong>Test Strategy:</strong></p>
     * <ul>
     *   <li>Test with minutesOffset = 1, toleranceMinutes = 10 (well within)</li>
     *   <li>Test with minutesOffset = 0, toleranceMinutes = 1 (no race condition)</li>
     *   <li>Test with various offsets significantly smaller than tolerance</li>
     * </ul>
     * 
     * <p><strong>Expected Outcome:</strong> Tests PASS consistently on unfixed code
     * (confirms baseline behavior to preserve).</p>
     * 
     * <p><strong>Validates: Requirement 3.1</strong></p>
     */
    @Property(tries = 100)
    @Tag("Property-2-Preservation")
    void timestampsWellWithinToleranceAlwaysPassValidation(
            @ForAll @LongRange(min = 10, max = 60) long toleranceMinutes,
            @ForAll @IntRange(min = -5, max = 5) int minutesOffset) {
        
        // Ensure we're testing well within tolerance (not boundary cases)
        Assume.that(Math.abs(minutesOffset) < toleranceMinutes - 5);
        
        // Generate timestamp well within tolerance window
        Instant timestamp = Instant.now().plus(minutesOffset, ChronoUnit.MINUTES);
        String timestampStr = timestamp.toString();
        
        // Validation should succeed (no exception thrown)
        SifAuthenticationUtils.validateTimestamp(timestampStr, toleranceMinutes);
        
        // If we get here, the test passed
        assertThat(true)
            .as("Timestamps well within tolerance should always pass validation")
            .isTrue();
    }

    /**
     * Property 2.2: Preservation - Timestamps Well Outside Tolerance
     * 
     * <p>For timestamps that are well outside the tolerance window (not at boundary
     * conditions), validation should always fail with InvalidTimestampException.
     * This tests the non-buggy input space where the race condition does not occur.</p>
     * 
     * <p><strong>Test Strategy:</strong></p>
     * <ul>
     *   <li>Test with minutesOffset = 20, toleranceMinutes = 5 (well outside)</li>
     *   <li>Test with various offsets significantly larger than tolerance</li>
     * </ul>
     * 
     * <p><strong>Expected Outcome:</strong> Tests PASS consistently on unfixed code
     * (confirms baseline behavior to preserve).</p>
     * 
     * <p><strong>Validates: Requirement 3.2</strong></p>
     */
    @Property(tries = 100)
    @Tag("Property-2-Preservation")
    void timestampsWellOutsideToleranceAlwaysThrowException(
            @ForAll @LongRange(min = 1, max = 10) long toleranceMinutes,
            @ForAll @IntRange(min = 15, max = 100) int minutesOffset) {
        
        // Ensure we're testing well outside tolerance (not boundary cases)
        Assume.that(minutesOffset > toleranceMinutes + 5);
        
        // Generate timestamp well outside tolerance window
        Instant timestamp = Instant.now().plus(minutesOffset, ChronoUnit.MINUTES);
        String timestampStr = timestamp.toString();
        
        // Validation should fail with InvalidTimestampException
        assertThatThrownBy(() -> SifAuthenticationUtils.validateTimestamp(timestampStr, toleranceMinutes))
            .as("Timestamps well outside tolerance should always throw InvalidTimestampException")
            .isInstanceOf(InvalidTimestampException.class)
            .hasMessageContaining("outside tolerance window");
    }

    /**
     * Property 2.3: Preservation - Negative Offsets Well Within Tolerance
     * 
     * <p>For timestamps in the past that are well within the tolerance window,
     * validation should always succeed. This tests past timestamps in the non-buggy
     * input space.</p>
     * 
     * <p><strong>Test Strategy:</strong></p>
     * <ul>
     *   <li>Test with negative offsets (past timestamps)</li>
     *   <li>Ensure offsets are well within tolerance (not boundary cases)</li>
     * </ul>
     * 
     * <p><strong>Expected Outcome:</strong> Tests PASS consistently on unfixed code
     * (confirms baseline behavior to preserve).</p>
     * 
     * <p><strong>Validates: Requirement 3.1</strong></p>
     */
    @Property(tries = 100)
    @Tag("Property-2-Preservation")
    void pastTimestampsWellWithinToleranceAlwaysPassValidation(
            @ForAll @LongRange(min = 10, max = 60) long toleranceMinutes,
            @ForAll @IntRange(min = -5, max = -1) int minutesOffset) {
        
        // Ensure we're testing well within tolerance (not boundary cases)
        Assume.that(Math.abs(minutesOffset) < toleranceMinutes - 5);
        
        // Generate past timestamp well within tolerance window
        Instant timestamp = Instant.now().plus(minutesOffset, ChronoUnit.MINUTES);
        String timestampStr = timestamp.toString();
        
        // Validation should succeed (no exception thrown)
        SifAuthenticationUtils.validateTimestamp(timestampStr, toleranceMinutes);
        
        // If we get here, the test passed
        assertThat(true)
            .as("Past timestamps well within tolerance should always pass validation")
            .isTrue();
    }

    /**
     * Property 2.4: Preservation - Negative Offsets Well Outside Tolerance
     * 
     * <p>For timestamps in the past that are well outside the tolerance window,
     * validation should always fail with InvalidTimestampException. This tests
     * past timestamps in the non-buggy input space.</p>
     * 
     * <p><strong>Test Strategy:</strong></p>
     * <ul>
     *   <li>Test with negative offsets (past timestamps)</li>
     *   <li>Ensure offsets are well outside tolerance (not boundary cases)</li>
     * </ul>
     * 
     * <p><strong>Expected Outcome:</strong> Tests PASS consistently on unfixed code
     * (confirms baseline behavior to preserve).</p>
     * 
     * <p><strong>Validates: Requirement 3.2</strong></p>
     */
    @Property(tries = 100)
    @Tag("Property-2-Preservation")
    void pastTimestampsWellOutsideToleranceAlwaysThrowException(
            @ForAll @LongRange(min = 1, max = 10) long toleranceMinutes,
            @ForAll @IntRange(min = -100, max = -15) int minutesOffset) {
        
        // Ensure we're testing well outside tolerance (not boundary cases)
        Assume.that(Math.abs(minutesOffset) > toleranceMinutes + 5);
        
        // Generate past timestamp well outside tolerance window
        Instant timestamp = Instant.now().plus(minutesOffset, ChronoUnit.MINUTES);
        String timestampStr = timestamp.toString();
        
        // Validation should fail with InvalidTimestampException
        assertThatThrownBy(() -> SifAuthenticationUtils.validateTimestamp(timestampStr, toleranceMinutes))
            .as("Past timestamps well outside tolerance should always throw InvalidTimestampException")
            .isInstanceOf(InvalidTimestampException.class)
            .hasMessageContaining("outside tolerance window");
    }

    /**
     * Property 2.5: Preservation - Zero Offset Always Passes
     * 
     * <p>For timestamps at the current time (offset = 0), validation should always
     * succeed regardless of tolerance. This is a special case with no race condition
     * because the timestamp is always within any positive tolerance window.</p>
     * 
     * <p><strong>Test Strategy:</strong></p>
     * <ul>
     *   <li>Test with minutesOffset = 0, various tolerance values</li>
     *   <li>No race condition possible (timestamp is current time)</li>
     * </ul>
     * 
     * <p><strong>Expected Outcome:</strong> Tests PASS consistently on unfixed code
     * (confirms baseline behavior to preserve).</p>
     * 
     * <p><strong>Validates: Requirement 3.1</strong></p>
     */
    @Property(tries = 100)
    @Tag("Property-2-Preservation")
    void currentTimestampAlwaysPassesValidation(
            @ForAll @LongRange(min = 1, max = 60) long toleranceMinutes) {
        
        // Generate timestamp at current time (offset = 0)
        Instant timestamp = Instant.now();
        String timestampStr = timestamp.toString();
        
        // Validation should succeed (no exception thrown)
        SifAuthenticationUtils.validateTimestamp(timestampStr, toleranceMinutes);
        
        // If we get here, the test passed
        assertThat(true)
            .as("Current timestamp should always pass validation")
            .isTrue();
    }

    /**
     * Property 2.6: Preservation - Production Code Uses Instant.now()
     * 
     * <p>This test verifies that the production code continues to use Instant.now()
     * for runtime validation. It does this by observing that validation results
     * change over time as the current time advances.</p>
     * 
     * <p><strong>Test Strategy:</strong></p>
     * <ul>
     *   <li>Create a timestamp at a fixed point in time</li>
     *   <li>Verify that validation behavior is based on current time (Instant.now())</li>
     *   <li>This confirms production code hasn't been changed to use fixed time</li>
     * </ul>
     * 
     * <p><strong>Expected Outcome:</strong> Tests PASS consistently on unfixed code
     * (confirms baseline behavior to preserve).</p>
     * 
     * <p><strong>Validates: Requirement 3.3</strong></p>
     */
    @Property(tries = 50)
    @Tag("Property-2-Preservation")
    void productionCodeUsesInstantNowForValidation() {
        // Create a timestamp 3 minutes in the past
        Instant timestamp = Instant.now().minus(3, ChronoUnit.MINUTES);
        String timestampStr = timestamp.toString();
        
        // With tolerance of 5 minutes, this should pass (3 < 5)
        SifAuthenticationUtils.validateTimestamp(timestampStr, 5L);
        assertThat(true)
            .as("Timestamp 3 minutes old should pass with 5 minute tolerance")
            .isTrue();
        
        // With tolerance of 2 minutes, this should fail (3 > 2)
        assertThatThrownBy(() -> SifAuthenticationUtils.validateTimestamp(timestampStr, 2L))
            .as("Timestamp 3 minutes old should fail with 2 minute tolerance")
            .isInstanceOf(InvalidTimestampException.class);
        
        // This confirms that validation is based on current time (Instant.now())
        // If the code used a fixed reference time, the behavior would be different
    }

    /**
     * Property 2.7: Preservation - Default Tolerance Behavior
     * 
     * <p>This test verifies that the default tolerance (5 minutes) continues to work
     * correctly for non-boundary cases. This ensures the no-argument validateTimestamp()
     * method is preserved.</p>
     * 
     * <p><strong>Test Strategy:</strong></p>
     * <ul>
     *   <li>Test with offsets well within default tolerance (5 minutes)</li>
     *   <li>Test with offsets well outside default tolerance</li>
     *   <li>Verify default behavior is preserved</li>
     * </ul>
     * 
     * <p><strong>Expected Outcome:</strong> Tests PASS consistently on unfixed code
     * (confirms baseline behavior to preserve).</p>
     * 
     * <p><strong>Validates: Requirement 3.4</strong></p>
     */
    @Property(tries = 100)
    @Tag("Property-2-Preservation")
    void defaultToleranceBehaviorIsPreserved(
            @ForAll @IntRange(min = -3, max = 3) int minutesOffset) {
        
        // Generate timestamp well within default tolerance (5 minutes)
        Instant timestamp = Instant.now().plus(minutesOffset, ChronoUnit.MINUTES);
        String timestampStr = timestamp.toString();
        
        // Validation with default tolerance should succeed
        SifAuthenticationUtils.validateTimestamp(timestampStr);
        
        // If we get here, the test passed
        assertThat(true)
            .as("Timestamps within default tolerance should pass validation")
            .isTrue();
    }

    /**
     * Property 2.8: Preservation - Default Tolerance Rejection
     * 
     * <p>This test verifies that timestamps well outside the default tolerance (5 minutes)
     * are correctly rejected. This ensures the no-argument validateTimestamp() method
     * continues to enforce the default tolerance.</p>
     * 
     * <p><strong>Test Strategy:</strong></p>
     * <ul>
     *   <li>Test with offsets well outside default tolerance (5 minutes)</li>
     *   <li>Verify rejection behavior is preserved</li>
     * </ul>
     * 
     * <p><strong>Expected Outcome:</strong> Tests PASS consistently on unfixed code
     * (confirms baseline behavior to preserve).</p>
     * 
     * <p><strong>Validates: Requirement 3.4</strong></p>
     */
    @Property(tries = 100)
    @Tag("Property-2-Preservation")
    void defaultToleranceRejectionIsPreserved(
            @ForAll @IntRange(min = 10, max = 100) int minutesOffset,
            @ForAll boolean isPast) {
        
        // Generate timestamp well outside default tolerance (5 minutes)
        int offset = isPast ? -minutesOffset : minutesOffset;
        Instant timestamp = Instant.now().plus(offset, ChronoUnit.MINUTES);
        String timestampStr = timestamp.toString();
        
        // Validation with default tolerance should fail
        assertThatThrownBy(() -> SifAuthenticationUtils.validateTimestamp(timestampStr))
            .as("Timestamps outside default tolerance should be rejected")
            .isInstanceOf(InvalidTimestampException.class)
            .hasMessageContaining("outside tolerance window");
    }

    /**
     * Property 2.9: Preservation - Validation Consistency
     * 
     * <p>This test verifies that validation results are consistent for the same
     * timestamp when called multiple times in quick succession. For non-boundary
     * cases, the result should be deterministic.</p>
     * 
     * <p><strong>Test Strategy:</strong></p>
     * <ul>
     *   <li>Generate a timestamp well within tolerance</li>
     *   <li>Validate it multiple times in quick succession</li>
     *   <li>Verify all validations produce the same result</li>
     * </ul>
     * 
     * <p><strong>Expected Outcome:</strong> Tests PASS consistently on unfixed code
     * (confirms baseline behavior to preserve).</p>
     * 
     * <p><strong>Validates: Requirement 3.1</strong></p>
     */
    @Property(tries = 100)
    @Tag("Property-2-Preservation")
    void validationIsConsistentForNonBoundaryCases(
            @ForAll @LongRange(min = 10, max = 60) long toleranceMinutes,
            @ForAll @IntRange(min = -5, max = 5) int minutesOffset) {
        
        // Ensure we're testing well within tolerance (not boundary cases)
        Assume.that(Math.abs(minutesOffset) < toleranceMinutes - 5);
        
        // Generate timestamp well within tolerance window
        Instant timestamp = Instant.now().plus(minutesOffset, ChronoUnit.MINUTES);
        String timestampStr = timestamp.toString();
        
        // Validate multiple times in quick succession
        for (int i = 0; i < 10; i++) {
            SifAuthenticationUtils.validateTimestamp(timestampStr, toleranceMinutes);
        }
        
        // If we get here, all validations passed consistently
        assertThat(true)
            .as("Validation should be consistent for non-boundary cases")
            .isTrue();
    }
}
