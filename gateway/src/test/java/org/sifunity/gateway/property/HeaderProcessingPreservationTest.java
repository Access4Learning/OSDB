// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.util.SifAuthenticationUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preservation Property Tests for Header Processing
 * 
 * **Validates: Requirements 3.9, 3.10**
 * 
 * <p><strong>CRITICAL: These tests are EXPECTED TO PASS on unfixed code.</strong>
 * These tests establish the baseline behavior that must be preserved after the fix.
 * 
 * <p><strong>Purpose:</strong>
 * These property-based tests verify that well-formed authorization header processing
 * continues to work correctly after fixing the malformed header rejection bug. They test:
 * <ul>
 *   <li>Well-formed Authorization headers are correctly accepted and parsed</li>
 *   <li>Valid headers with various character sets are processed correctly</li>
 *   <li>Headers with printable characters are accepted</li>
 * </ul>
 * 
 * <p><strong>Expected Behavior:</strong>
 * All tests in this class should PASS on the unfixed code, confirming that the
 * header processing for valid inputs is correct and should be preserved.
 * 
 * <p><strong>Observation-First Methodology:</strong>
 * These tests observe and capture the behavior of the unfixed code for well-formed
 * headers. This ensures that when we fix the malformed header bug, we don't break
 * the processing of valid headers.
 * 
 * @see MalformedHeaderBugConditionExplorationTest
 * @see SifAuthenticationUtils
 */
class HeaderProcessingPreservationTest {
    
    /**
     * Property 1: Preservation - Well-Formed Headers Are Accepted
     * 
     * **Validates: Requirements 3.9, 3.10**
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES on unfixed code (confirms baseline behavior)</strong>
     * 
     * <p>This property verifies that well-formed authorization headers containing
     * only printable ASCII characters (no null bytes, no control characters) are
     * correctly accepted and parsed.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Headers with alphanumeric sessionTokens are accepted</li>
     *   <li>Headers with alphanumeric hashes are accepted</li>
     *   <li>Parsing extracts sessionToken and hash correctly</li>
     *   <li>No exceptions are thrown for well-formed headers</li>
     * </ul>
     * 
     * @param sessionToken randomly generated well-formed session token
     * @param hash randomly generated well-formed hash
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Preservation: Well-Formed Headers Are Accepted")
    void wellFormedHeadersAreAccepted(
            @ForAll("wellFormedSessionTokens") String sessionToken,
            @ForAll("wellFormedHashes") String hash) {
        
        // Arrange: Construct a well-formed Authorization header
        String credentials = sessionToken + ":" + hash;
        String base64 = java.util.Base64.getEncoder()
                .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64;
        
        // Act: Parse the authorization header (should not throw)
        SifAuthenticationUtils.AuthHeader parsed = SifAuthenticationUtils.parseAuthHeader(authHeader);
        
        // Assert: Verify the header was accepted and parsed correctly
        assertThat(parsed)
                .as("Well-formed header should be accepted and parsed")
                .isNotNull();
        
        assertThat(parsed.sessionToken())
                .as("SessionToken should match the original value")
                .isEqualTo(sessionToken);
        
        assertThat(parsed.hash())
                .as("Hash should match the original value")
                .isEqualTo(hash);
    }
    
    /**
     * Property 2: Preservation - Headers With Special Printable Characters Are Accepted
     * 
     * **Validates: Requirements 3.9, 3.10**
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES on unfixed code (confirms baseline behavior)</strong>
     * 
     * <p>This property verifies that authorization headers containing special
     * printable characters (like hyphens, underscores, dots) are correctly accepted.
     * These are common in UUIDs and Base64-encoded values.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Headers with UUID-format sessionTokens (with hyphens) are accepted</li>
     *   <li>Headers with Base64-format hashes (with +, /, =) are accepted</li>
     *   <li>Parsing handles special characters correctly</li>
     *   <li>No exceptions are thrown for headers with printable special characters</li>
     * </ul>
     * 
     * @param sessionToken randomly generated UUID-like session token
     * @param hash randomly generated Base64-like hash
     */
    @Property(tries = 100)
    @Label("Feature: gateway, Preservation: Headers With Special Printable Characters Are Accepted")
    void headersWithSpecialPrintableCharactersAreAccepted(
            @ForAll("uuidLikeSessionTokens") String sessionToken,
            @ForAll("base64LikeHashes") String hash) {
        
        // Arrange: Construct an Authorization header with special characters
        String credentials = sessionToken + ":" + hash;
        String base64 = java.util.Base64.getEncoder()
                .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64;
        
        // Act: Parse the authorization header (should not throw)
        SifAuthenticationUtils.AuthHeader parsed = SifAuthenticationUtils.parseAuthHeader(authHeader);
        
        // Assert: Verify the header was accepted and parsed correctly
        assertThat(parsed)
                .as("Header with special printable characters should be accepted")
                .isNotNull();
        
        assertThat(parsed.sessionToken())
                .as("SessionToken should match the original value")
                .isEqualTo(sessionToken);
        
        assertThat(parsed.hash())
                .as("Hash should match the original value")
                .isEqualTo(hash);
    }
    
    /**
     * Property 3: Preservation - Headers With Long Values Are Accepted
     * 
     * **Validates: Requirements 3.9, 3.10**
     * 
     * <p><strong>EXPECTED OUTCOME: This test PASSES on unfixed code (confirms baseline behavior)</strong>
     * 
     * <p>This property verifies that authorization headers with long sessionTokens
     * and hashes are correctly accepted. This ensures the fix doesn't introduce
     * length restrictions that break existing functionality.
     * 
     * <p>This property verifies:
     * <ul>
     *   <li>Headers with long sessionTokens (up to 100 chars) are accepted</li>
     *   <li>Headers with long hashes (up to 100 chars) are accepted</li>
     *   <li>Parsing handles long values correctly</li>
     *   <li>No exceptions are thrown for headers with long values</li>
     * </ul>
     * 
     * @param sessionToken randomly generated long session token
     * @param hash randomly generated long hash
     */
    @Property(tries = 50)
    @Label("Feature: gateway, Preservation: Headers With Long Values Are Accepted")
    void headersWithLongValuesAreAccepted(
            @ForAll("longSessionTokens") String sessionToken,
            @ForAll("longHashes") String hash) {
        
        // Arrange: Construct an Authorization header with long values
        String credentials = sessionToken + ":" + hash;
        String base64 = java.util.Base64.getEncoder()
                .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64;
        
        // Act: Parse the authorization header (should not throw)
        SifAuthenticationUtils.AuthHeader parsed = SifAuthenticationUtils.parseAuthHeader(authHeader);
        
        // Assert: Verify the header was accepted and parsed correctly
        assertThat(parsed)
                .as("Header with long values should be accepted")
                .isNotNull();
        
        assertThat(parsed.sessionToken())
                .as("SessionToken should match the original value")
                .isEqualTo(sessionToken);
        
        assertThat(parsed.hash())
                .as("Hash should match the original value")
                .isEqualTo(hash);
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates well-formed session tokens with alphanumeric characters only.
     * These represent typical session tokens without special characters.
     */
    @Provide
    Arbitrary<String> wellFormedSessionTokens() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(10)
                .ofMaxLength(50);
    }
    
    /**
     * Generates well-formed hashes with alphanumeric characters only.
     * These represent typical HMAC hashes in hexadecimal format.
     */
    @Provide
    Arbitrary<String> wellFormedHashes() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(32)
                .ofMaxLength(64);
    }
    
    /**
     * Generates UUID-like session tokens with hyphens.
     * Format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
     */
    @Provide
    Arbitrary<String> uuidLikeSessionTokens() {
        return Arbitraries.strings()
                .withCharRange('a', 'f')
                .numeric()
                .ofLength(32)
                .map(s -> {
                    // Format as UUID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
                    return s.substring(0, 8) + "-" +
                           s.substring(8, 12) + "-" +
                           s.substring(12, 16) + "-" +
                           s.substring(16, 20) + "-" +
                           s.substring(20, 32);
                });
    }
    
    /**
     * Generates Base64-like hashes with special characters (+, /, =).
     * These represent typical Base64-encoded HMAC-SHA256 hashes.
     */
    @Provide
    Arbitrary<String> base64LikeHashes() {
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('a', 'z')
                .numeric()
                .withChars('+', '/', '=')
                .ofLength(44);  // HMAC-SHA256 in Base64 is 44 characters
    }
    
    /**
     * Generates long session tokens (50-100 characters).
     * These test that the fix doesn't introduce length restrictions.
     */
    @Provide
    Arbitrary<String> longSessionTokens() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(50)
                .ofMaxLength(100);
    }
    
    /**
     * Generates long hashes (50-100 characters).
     * These test that the fix doesn't introduce length restrictions.
     */
    @Provide
    Arbitrary<String> longHashes() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(50)
                .ofMaxLength(100);
    }
}
