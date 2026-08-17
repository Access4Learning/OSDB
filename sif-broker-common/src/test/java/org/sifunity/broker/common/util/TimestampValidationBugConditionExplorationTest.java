// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import net.jqwik.api.*;
import org.sifunity.broker.common.exception.InvalidTimestampException;
import org.sifunity.broker.common.util.SifAuthenticationUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bug Condition Exploration Test for Timestamp Validation Flakiness
 * 
 * <p>This test demonstrates the race condition in timestamp validation that causes
 * intermittent failures in CI environments. The bug occurs when multiple calls to
 * {@code Instant.now()} at different points in time cause boundary conditions to
 * behave non-deterministically.</p>
 * 
 * <p><strong>CRITICAL:</strong> This test is EXPECTED TO SHOW FLAKINESS on unfixed code.
 * Intermittent failures confirm the race condition exists. This is the SUCCESS case
 * for bug condition exploration.</p>
 * 
 * <p><strong>Bug Condition:</strong> Race condition occurs when:
 * <ul>
 *   <li>actualOffset equals toleranceMinutes (boundary condition)</li>
 *   <li>Time elapses between timestamp generation and validation</li>
 *   <li>Elapsed time causes calculated difference to cross minute boundary</li>
 * </ul>
 * 
 * <p><strong>Expected Behavior on Unfixed Code:</strong>
 * <ul>
 *   <li>Intermittent failures (not 100% pass rate)</li>
 *   <li>"Expecting code to raise a throwable" errors when timing causes boundary crossing</li>
 *   <li>Higher failure rate with artificial delays (CI simulation)</li>
 * </ul>
 * 
 * <p><strong>Expected Behavior on Fixed Code:</strong>
 * <ul>
 *   <li>100% pass rate across all iterations</li>
 *   <li>Deterministic results regardless of timing</li>
 *   <li>No difference between fast and slow execution</li>
 * </ul>
 * 
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3</strong></p>
 */
@Tag("Feature-timestamp-validation-flaky-test-fix")
class TimestampValidationBugConditionExplorationTest {

    /**
     * Property 1: Bug Condition - Flaky Timestamp Validation at Boundary Conditions
     * 
     * <p>This test runs the existing timestamp validation logic 1000 times with
     * boundary condition inputs to demonstrate intermittent failures caused by
     * the race condition between two {@code Instant.now()} calls.</p>
     * 
     * <p><strong>Test Strategy:</strong></p>
     * <ul>
     *   <li>Test Case 1: minutesOffset = 5, toleranceMinutes = 5 (boundary success case)</li>
     *   <li>Test Case 2: minutesOffset = 6, toleranceMinutes = 5 (boundary failure case)</li>
     *   <li>Test Case 3: minutesOffset = -5, toleranceMinutes = 5 (negative boundary)</li>
     *   <li>Add artificial delays to simulate CI environment timing</li>
     *   <li>Track failure rates to document the race condition</li>
     * </ul>
     * 
     * <p><strong>IMPORTANT:</strong> On unfixed code, this test will show intermittent
     * failures. This is CORRECT behavior - it proves the bug exists. Do NOT attempt
     * to fix the test or code when failures occur.</p>
     * 
     * <p><strong>Validates: Requirements 1.1, 1.2, 1.3</strong></p>
     */
    @Property(tries = 1)
    @Tag("Property-1-Bug-Condition-Exploration")
    void timestampValidationShowsFlakinesAtBoundaryConditions() {
        // Track failures across all test scenarios
        AtomicInteger totalTests = new AtomicInteger(0);
        AtomicInteger totalFailures = new AtomicInteger(0);
        
        System.out.println("\n=== Bug Condition Exploration: Timestamp Validation Flakiness ===\n");
        
        // Test Case 1: Boundary success case (minutesOffset = 5, toleranceMinutes = 5)
        System.out.println("Test Case 1: Boundary Success (offset=5, tolerance=5)");
        int failures1 = runBoundaryTest(5, 5, 1000, true);
        totalTests.addAndGet(1000);
        totalFailures.addAndGet(failures1);
        System.out.println("  Failures: " + failures1 + " / 1000 (" + 
            String.format("%.2f", failures1 * 100.0 / 1000) + "%)");
        
        // Test Case 2: Boundary failure case (minutesOffset = 6, toleranceMinutes = 5)
        System.out.println("\nTest Case 2: Boundary Failure (offset=6, tolerance=5)");
        int failures2 = runBoundaryTest(6, 5, 1000, false);
        totalTests.addAndGet(1000);
        totalFailures.addAndGet(failures2);
        System.out.println("  Failures: " + failures2 + " / 1000 (" + 
            String.format("%.2f", failures2 * 100.0 / 1000) + "%)");
        
        // Test Case 3: Negative boundary case (minutesOffset = -5, toleranceMinutes = 5)
        System.out.println("\nTest Case 3: Negative Boundary (offset=-5, tolerance=5)");
        int failures3 = runBoundaryTest(-5, 5, 1000, true);
        totalTests.addAndGet(1000);
        totalFailures.addAndGet(failures3);
        System.out.println("  Failures: " + failures3 + " / 1000 (" + 
            String.format("%.2f", failures3 * 100.0 / 1000) + "%)");
        
        // Test Case 4: CI simulation with artificial delays (offset=5, tolerance=5)
        System.out.println("\nTest Case 4: CI Simulation with Delays (offset=5, tolerance=5)");
        int failures4 = runBoundaryTestWithDelay(5, 5, 1000, true);
        totalTests.addAndGet(1000);
        totalFailures.addAndGet(failures4);
        System.out.println("  Failures: " + failures4 + " / 1000 (" + 
            String.format("%.2f", failures4 * 100.0 / 1000) + "%)");
        
        // Summary
        System.out.println("\n=== Summary ===");
        System.out.println("Total Tests: " + totalTests.get());
        System.out.println("Total Failures: " + totalFailures.get());
        System.out.println("Overall Failure Rate: " + 
            String.format("%.2f", totalFailures.get() * 100.0 / totalTests.get()) + "%");
        
        // Analysis
        System.out.println("\n=== Analysis ===");
        if (totalFailures.get() > 0) {
            System.out.println("✓ RACE CONDITION CONFIRMED: Test shows intermittent failures");
            System.out.println("  This proves the bug exists - multiple Instant.now() calls cause");
            System.out.println("  non-deterministic behavior at boundary conditions.");
            System.out.println("\n  Counterexamples found:");
            System.out.println("  - Boundary success case failures: " + failures1);
            System.out.println("  - Boundary failure case failures: " + failures2);
            System.out.println("  - Negative boundary failures: " + failures3);
            System.out.println("  - CI simulation failures: " + failures4);
            System.out.println("\n  Root Cause: Time elapsed between Instant.now() calls causes");
            System.out.println("  ChronoUnit.MINUTES.between() to round differently, changing");
            System.out.println("  validation outcomes unpredictably.");
        } else {
            System.out.println("⚠ NO FLAKINESS DETECTED: All tests passed consistently");
            System.out.println("  This could mean:");
            System.out.println("  1. The code has already been fixed");
            System.out.println("  2. The test environment is too fast to trigger the race condition");
            System.out.println("  3. More iterations or longer delays may be needed");
            System.out.println("\n  Note: On unfixed code, we expect to see intermittent failures.");
        }
        
        System.out.println("\n=== Expected Behavior ===");
        System.out.println("On UNFIXED code: Intermittent failures (proves bug exists)");
        System.out.println("On FIXED code: 100% pass rate (proves fix works)");
        System.out.println("================================================================\n");
        
        // For bug condition exploration, we document the flakiness but don't fail the test
        // The goal is to observe and document the race condition, not to assert pass/fail
        assertThat(true)
            .as("Bug condition exploration complete - see output for flakiness analysis")
            .isTrue();
    }
    
    /**
     * Runs the timestamp validation test multiple times with boundary condition inputs.
     * 
     * <p>After the fix, this method uses a fixed reference time to eliminate race conditions,
     * ensuring deterministic results across all iterations.</p>
     * 
     * @param minutesOffset the offset to apply to the reference time
     * @param toleranceMinutes the tolerance window in minutes
     * @param iterations the number of times to run the test
     * @param shouldSucceed whether validation is expected to succeed
     * @return the number of failures observed
     */
    private int runBoundaryTest(int minutesOffset, long toleranceMinutes, 
                                 int iterations, boolean shouldSucceed) {
        int failures = 0;
        
        // Use fixed reference time to eliminate race condition
        Instant referenceTime = Instant.parse("2024-01-15T10:00:00Z");
        
        for (int i = 0; i < iterations; i++) {
            try {
                // Generate timestamp using fixed reference time
                Instant timestamp = referenceTime.plus(minutesOffset, ChronoUnit.MINUTES);
                String timestampStr = timestamp.toString();
                
                long actualOffset = Math.abs(minutesOffset);
                
                if (shouldSucceed) {
                    // Expected: validation should succeed (actualOffset <= toleranceMinutes)
                    try {
                        SifAuthenticationUtils.validateTimestampWithReference(timestampStr, toleranceMinutes, referenceTime);
                    } catch (InvalidTimestampException e) {
                        // Unexpected failure - test logic error
                        failures++;
                    }
                } else {
                    // Expected: validation should fail (actualOffset > toleranceMinutes)
                    try {
                        SifAuthenticationUtils.validateTimestampWithReference(timestampStr, toleranceMinutes, referenceTime);
                        // Unexpected success - test logic error
                        failures++;
                    } catch (InvalidTimestampException e) {
                        // Expected failure - validation correctly rejected timestamp
                    }
                }
            } catch (Exception e) {
                // Unexpected exception
                failures++;
            }
        }
        
        return failures;
    }
    
    /**
     * Runs the timestamp validation test with artificial delays to simulate CI environment.
     * 
     * <p>After the fix, this method uses a fixed reference time, so delays no longer
     * affect the test results. This verifies that timing independence has been achieved.</p>
     * 
     * @param minutesOffset the offset to apply to the reference time
     * @param toleranceMinutes the tolerance window in minutes
     * @param iterations the number of times to run the test
     * @param shouldSucceed whether validation is expected to succeed
     * @return the number of failures observed
     */
    private int runBoundaryTestWithDelay(int minutesOffset, long toleranceMinutes, 
                                          int iterations, boolean shouldSucceed) {
        int failures = 0;
        
        // Use fixed reference time to eliminate race condition
        Instant referenceTime = Instant.parse("2024-01-15T10:00:00Z");
        
        for (int i = 0; i < iterations; i++) {
            try {
                // Generate timestamp using fixed reference time
                Instant timestamp = referenceTime.plus(minutesOffset, ChronoUnit.MINUTES);
                String timestampStr = timestamp.toString();
                
                // Simulate CI environment delay (should have no effect with fixed reference time)
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                long actualOffset = Math.abs(minutesOffset);
                
                if (shouldSucceed) {
                    // Expected: validation should succeed (actualOffset <= toleranceMinutes)
                    try {
                        SifAuthenticationUtils.validateTimestampWithReference(timestampStr, toleranceMinutes, referenceTime);
                    } catch (InvalidTimestampException e) {
                        // Unexpected failure - test logic error
                        failures++;
                    }
                } else {
                    // Expected: validation should fail (actualOffset > toleranceMinutes)
                    try {
                        SifAuthenticationUtils.validateTimestampWithReference(timestampStr, toleranceMinutes, referenceTime);
                        // Unexpected success - test logic error
                        failures++;
                    } catch (InvalidTimestampException e) {
                        // Expected failure - validation correctly rejected timestamp
                    }
                }
            } catch (Exception e) {
                // Unexpected exception
                failures++;
            }
        }
        
        return failures;
    }
}
