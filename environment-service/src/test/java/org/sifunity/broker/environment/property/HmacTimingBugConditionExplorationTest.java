// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.environment.service.AuthenticationService;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bug Condition Exploration Test for HMAC Timing Attack Vulnerability.
 *
 * <p><strong>CRITICAL: These tests MUST FAIL on unfixed code — failure confirms the bug exists.</strong></p>
 *
 * <p>Bug: {@code AuthenticationService.validateToken()} at line ~158 uses
 * {@code expectedHash.equals(providedHash)} — {@code String.equals()} short-circuits
 * on the first differing byte, leaking timing information and enabling timing side-channel attacks.</p>
 *
 * <p>Bug Condition: HMAC comparison uses {@code MessageDigest.isEqual()} (structural test
 * via source code inspection).</p>
 *
 * <p>On UNFIXED code: test FAILS because {@code String.equals()} is used.</p>
 * <p>On FIXED code: test PASSES because {@code MessageDigest.isEqual()} is used.</p>
 *
 * <p><strong>Validates: Requirements 1.9</strong></p>
 */
class HmacTimingBugConditionExplorationTest {

    private static final Path AUTH_SERVICE_SOURCE = Path.of(
            "environment-service/src/main/java/org/sifunity/broker/environment/service/AuthenticationService.java");

    /**
     * Property: AuthenticationService.validateToken() source SHALL use MessageDigest.isEqual()
     * for HMAC comparison instead of String.equals().
     *
     * <p>This is a structural/compile-time correctness test. We inspect the source code
     * to verify constant-time comparison is used.</p>
     *
     * <p>On UNFIXED code: source contains {@code expectedHash.equals(providedHash)} — test FAILS.</p>
     * <p>On FIXED code: source contains {@code MessageDigest.isEqual()} — test PASSES.</p>
     */
    @Property(tries = 20)
    @Label("AuthenticationService HMAC comparison SHALL use MessageDigest.isEqual() (constant-time)")
    void hmacComparisonShallUseConstantTimeComparison(
            @ForAll("dummyInputs") int dummy) throws IOException {

        // Read the source file to verify the comparison method used
        Path sourceFile = findSourceFile();
        assertThat(sourceFile)
                .as("AuthenticationService.java source file should exist")
                .isNotNull();

        String source = Files.readString(sourceFile);

        // Verify that MessageDigest.isEqual is used (constant-time)
        boolean usesConstantTimeComparison = source.contains("MessageDigest.isEqual(");

        // Verify that the vulnerable pattern is NOT present
        boolean usesVulnerableEquals = source.contains("expectedHash.equals(providedHash)")
                || source.contains("expectedHash.equals( providedHash");

        assertThat(usesConstantTimeComparison)
                .as("AuthenticationService should use MessageDigest.isEqual() for HMAC comparison, " +
                    "but on UNFIXED code String.equals() is used (timing attack vulnerability)")
                .isTrue();

        assertThat(usesVulnerableEquals)
                .as("AuthenticationService should NOT use String.equals() for HMAC comparison " +
                    "(timing side-channel vulnerability)")
                .isFalse();
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<Integer> dummyInputs() {
        // Structural test doesn't need varying inputs, but jqwik requires a @ForAll
        return Arbitraries.integers().between(1, 20);
    }

    // ========== Helpers ==========

    private Path findSourceFile() {
        // Try relative path from project root
        Path relative = AUTH_SERVICE_SOURCE;
        if (Files.exists(relative)) {
            return relative;
        }

        // Try from common working directories
        Path[] candidates = {
                Path.of("").toAbsolutePath().resolve(AUTH_SERVICE_SOURCE),
                Path.of("..").toAbsolutePath().resolve(AUTH_SERVICE_SOURCE),
                Path.of("../..").toAbsolutePath().resolve(AUTH_SERVICE_SOURCE),
        };

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        // Try to find via system property or environment
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            // If running from module directory, go up to project root
            Path fromModule = Path.of(userDir).resolve("../").normalize().resolve(AUTH_SERVICE_SOURCE);
            if (Files.exists(fromModule)) {
                return fromModule;
            }
            // If running from project root
            Path fromRoot = Path.of(userDir).resolve(AUTH_SERVICE_SOURCE);
            if (Files.exists(fromRoot)) {
                return fromRoot;
            }
        }

        return null;
    }
}
