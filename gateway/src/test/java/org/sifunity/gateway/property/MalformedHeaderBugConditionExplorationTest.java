// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.exception.MalformedAuthHeaderException;
import org.sifunity.broker.common.util.SifAuthenticationUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bug Condition Exploration Test for Malformed Header Rejection
 * 
 * **Validates: Requirements 1.6**
 * 
 * <p>This test explores the bug condition where the AuthenticationPropertiesTest
 * property-based test for malformed header rejection fails. The test is EXPECTED
 * TO FAIL on unfixed code - failure confirms the bug exists.
 * 
 * <p><strong>CRITICAL:</strong> This test MUST FAIL on unfixed code. When it fails,
 * it proves the bug exists and provides counterexamples that demonstrate the issue.
 * 
 * <p><strong>DO NOT attempt to fix the test or the code when it fails.</strong>
 * 
 * <p>The goal is to surface specific counterexamples that show:
 * <ul>
 *   <li>What types of malformed headers are incorrectly accepted</li>
 *   <li>Why the validation logic fails to reject them</li>
 *   <li>What the root cause of the bug is</li>
 * </ul>
 * 
 * <p>Once the bug is fixed, this SAME test will pass, confirming the expected behavior.
 * 
 * <p><strong>Bug Analysis:</strong>
 * The test failure shows that headers like "SIF_HMACSHA256 AAAAOgAAAA" are being
 * accepted when they should be rejected. When Base64-decoded, this becomes a string
 * with null bytes and a colon: "\0\0\0:\0\0\0\0". The current validation checks
 * isEmpty() but null bytes are not considered "empty" - they have length > 0.
 * 
 * <p>This test encodes the expected behavior: malformed headers with null bytes,
 * whitespace-only content, or other invalid characters should be rejected.
 */
class MalformedHeaderBugConditionExplorationTest {
    
    /**
     * Property 1: Bug Condition - Malformed headers with null bytes are accepted
     * 
     * **Validates: Requirements 1.6**
     * 
     * <p><strong>EXPECTED OUTCOME:</strong> This test FAILS on unfixed code.
     * 
     * <p>This property verifies that malformed authorization headers containing
     * null bytes or other non-printable characters are correctly rejected with
     * MalformedAuthHeaderException and error code AUTH_003.
     * 
     * <p>The bug is that headers like "SIF_HMACSHA256 AAAAOgAAAA" (which decodes
     * to "\0\0\0:\0\0\0\0") are being accepted because the validation only checks
     * isEmpty(), and strings with null bytes have length > 0.
     * 
     * @param malformedHeader randomly generated malformed Authorization header
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Bug Condition: Malformed header rejection")
    void malformedHeadersWithNullBytesAreRejected(@ForAll("malformedAuthHeaders") String malformedHeader) {
        
        // Act & Assert: Verify that MalformedAuthHeaderException is thrown
        try {
            SifAuthenticationUtils.parseAuthHeader(malformedHeader);
            
            // If we reach here, the header was incorrectly accepted
            throw new AssertionError("Expected MalformedAuthHeaderException but header was accepted: " + malformedHeader);
            
        } catch (MalformedAuthHeaderException e) {
            // Verify the error code is AUTH_003
            assertThat(e.getErrorCode()).isEqualTo("AUTH_003");
            
            // Verify the error message indicates malformed header
            assertThat(e.getMessage()).isNotBlank();
            
            // Verify the HTTP status is 401 Unauthorized
            assertThat(e.getHttpStatus().value()).isEqualTo(401);
        }
    }
    
    /**
     * Concrete test case: Header with null bytes in sessionToken
     * 
     * <p><strong>EXPECTED OUTCOME:</strong> This test FAILS on unfixed code.
     * 
     * <p>This is a concrete example from the property test failure.
     * The header "SIF_HMACSHA256 AAAAOgAAAA" decodes to "\0\0\0:\0\0\0\0"
     * which should be rejected but is currently accepted.
     */
    @Test
    void headerWithNullBytesInSessionTokenIsRejected() {
        // This is the actual failing case from the property test
        String malformedHeader = "SIF_HMACSHA256 AAAAOgAAAA";
        
        // When decoded, this becomes: "\0\0\0:\0\0\0\0"
        // sessionToken = "\0\0\0" (3 null bytes)
        // hash = "\0\0\0\0" (4 null bytes)
        
        // This should throw MalformedAuthHeaderException
        assertThatThrownBy(() -> SifAuthenticationUtils.parseAuthHeader(malformedHeader))
                .isInstanceOf(MalformedAuthHeaderException.class)
                .hasFieldOrPropertyWithValue("errorCode", "AUTH_003")
                .satisfies(e -> {
                    MalformedAuthHeaderException ex = (MalformedAuthHeaderException) e;
                    assertThat(ex.getHttpStatus().value()).isEqualTo(401);
                });
    }
    
    /**
     * Concrete test case: Header with whitespace-only sessionToken
     * 
     * <p><strong>EXPECTED OUTCOME:</strong> This test should PASS even on unfixed code
     * (unless there's a similar bug with whitespace).
     * 
     * <p>This tests that headers with whitespace-only components are rejected.
     */
    @Test
    void headerWithWhitespaceOnlySessionTokenIsRejected() {
        // Base64 encode "   :hash123" (3 spaces, colon, hash)
        String credentials = "   :hash123";
        String base64 = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        String malformedHeader = "SIF_HMACSHA256 " + base64;
        
        // This should throw MalformedAuthHeaderException
        assertThatThrownBy(() -> SifAuthenticationUtils.parseAuthHeader(malformedHeader))
                .isInstanceOf(MalformedAuthHeaderException.class)
                .hasFieldOrPropertyWithValue("errorCode", "AUTH_003");
    }
    
    /**
     * Concrete test case: Header with whitespace-only hash
     * 
     * <p><strong>EXPECTED OUTCOME:</strong> This test should PASS even on unfixed code
     * (unless there's a similar bug with whitespace).
     * 
     * <p>This tests that headers with whitespace-only hash components are rejected.
     */
    @Test
    void headerWithWhitespaceOnlyHashIsRejected() {
        // Base64 encode "token123:   " (token, colon, 3 spaces)
        String credentials = "token123:   ";
        String base64 = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        String malformedHeader = "SIF_HMACSHA256 " + base64;
        
        // This should throw MalformedAuthHeaderException
        assertThatThrownBy(() -> SifAuthenticationUtils.parseAuthHeader(malformedHeader))
                .isInstanceOf(MalformedAuthHeaderException.class)
                .hasFieldOrPropertyWithValue("errorCode", "AUTH_003");
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates random malformed Authorization headers.
     * 
     * <p>This generator creates headers that should be rejected, including:
     * <ul>
     *   <li>Headers with null bytes in sessionToken or hash</li>
     *   <li>Headers with whitespace-only sessionToken or hash</li>
     *   <li>Headers with control characters</li>
     *   <li>Headers with empty components after Base64 decoding</li>
     * </ul>
     * 
     * <p>The generator focuses on edge cases that the current validation might miss.
     */
    @Provide
    Arbitrary<String> malformedAuthHeaders() {
        // Generate various types of malformed headers that should be rejected
        return Arbitraries.oneOf(
                // Headers with null bytes in sessionToken (guaranteed to have at least one null byte)
                Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(5)
                        .flatMap(prefix ->
                                Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(5)
                                        .flatMap(suffix ->
                                                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20)
                                                        .map(hash -> {
                                                            String sessionToken = prefix + "\0" + suffix;
                                                            String credentials = sessionToken + ":" + hash;
                                                            String base64 = java.util.Base64.getEncoder()
                                                                    .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                                            return "SIF_HMACSHA256 " + base64;
                                                        })
                                        )
                        ),
                
                // Headers with null bytes in hash (guaranteed to have at least one null byte)
                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20)
                        .flatMap(sessionToken ->
                                Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(5)
                                        .flatMap(prefix ->
                                                Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(5)
                                                        .map(suffix -> {
                                                            String hash = prefix + "\0" + suffix;
                                                            String credentials = sessionToken + ":" + hash;
                                                            String base64 = java.util.Base64.getEncoder()
                                                                    .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                                            return "SIF_HMACSHA256 " + base64;
                                                        })
                                        )
                        ),
                
                // Headers with null bytes in both sessionToken and hash
                Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(5)
                        .flatMap(prefix1 ->
                                Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(5)
                                        .flatMap(suffix1 ->
                                                Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(5)
                                                        .flatMap(prefix2 ->
                                                                Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(5)
                                                                        .map(suffix2 -> {
                                                                            String sessionToken = prefix1 + "\0" + suffix1;
                                                                            String hash = prefix2 + "\0" + suffix2;
                                                                            String credentials = sessionToken + ":" + hash;
                                                                            String base64 = java.util.Base64.getEncoder()
                                                                                    .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                                                            return "SIF_HMACSHA256 " + base64;
                                                                        })
                                                        )
                                        )
                        ),
                
                // Headers with whitespace-only sessionToken
                Arbitraries.strings()
                        .whitespace()
                        .ofMinLength(1)
                        .ofMaxLength(10)
                        .flatMap(sessionToken ->
                                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20)
                                        .map(hash -> {
                                            String credentials = sessionToken + ":" + hash;
                                            String base64 = java.util.Base64.getEncoder()
                                                    .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                            return "SIF_HMACSHA256 " + base64;
                                        })
                        ),
                
                // Headers with whitespace-only hash
                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20)
                        .flatMap(sessionToken ->
                                Arbitraries.strings()
                                        .whitespace()
                                        .ofMinLength(1)
                                        .ofMaxLength(10)
                                        .map(hash -> {
                                            String credentials = sessionToken + ":" + hash;
                                            String base64 = java.util.Base64.getEncoder()
                                                    .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                            return "SIF_HMACSHA256 " + base64;
                                        })
                        ),
                
                // Headers with control characters in sessionToken (guaranteed to have at least one control char)
                Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(5)
                        .flatMap(prefix ->
                                Arbitraries.chars().range('\u0001', '\u001F')
                                        .flatMap(controlChar ->
                                                Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(5)
                                                        .flatMap(suffix ->
                                                                Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20)
                                                                        .map(hash -> {
                                                                            String sessionToken = prefix + controlChar + suffix;
                                                                            String credentials = sessionToken + ":" + hash;
                                                                            String base64 = java.util.Base64.getEncoder()
                                                                                    .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                                                            return "SIF_HMACSHA256 " + base64;
                                                                        })
                                                        )
                                        )
                        ),
                
                // The specific failing case from the test report
                Arbitraries.just("SIF_HMACSHA256 AAAAOgAAAA")
        );
    }
}
