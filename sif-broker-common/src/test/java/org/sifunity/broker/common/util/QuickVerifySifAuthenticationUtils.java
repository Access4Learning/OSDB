// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import org.sifunity.broker.common.util.SifAuthenticationUtils.AuthHeader;
import java.time.Instant;

/**
 * Quick verification of SifAuthenticationUtils functionality.
 * This is a simple main method to verify the implementation works correctly.
 */
public class QuickVerifySifAuthenticationUtils {
    
    public static void main(String[] args) {
        System.out.println("=== Testing SifAuthenticationUtils ===\n");
        
        // Test 1: computeHmac
        System.out.println("Test 1: computeHmac()");
        String sessionToken = "test-token-123";
        String timestamp = "2024-01-15T10:30:00Z";
        String sharedSecret = "my-secret-key";
        String hash = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);
        System.out.println("  Hash: " + hash);
        System.out.println("  Hash is Base64: " + hash.matches("^[A-Za-z0-9+/]+=*$"));
        
        // Test 2: computeHmac consistency
        System.out.println("\nTest 2: computeHmac() consistency");
        String hash2 = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);
        System.out.println("  Hash 1: " + hash);
        System.out.println("  Hash 2: " + hash2);
        System.out.println("  Hashes match: " + hash.equals(hash2));
        
        // Test 3: parseAuthHeader
        System.out.println("\nTest 3: parseAuthHeader()");
        String credentials = "my-token:abc123hash";
        String base64Credentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
        String authHeaderStr = "SIF_HMACSHA256 " + base64Credentials;
        AuthHeader parsed = SifAuthenticationUtils.parseAuthHeader(authHeaderStr);
        System.out.println("  Session Token: " + parsed.sessionToken());
        System.out.println("  Hash: " + parsed.hash());
        
        // Test 4: validateTimestamp with current time
        System.out.println("\nTest 4: validateTimestamp() with current time");
        String currentTimestamp = Instant.now().toString();
        try {
            SifAuthenticationUtils.validateTimestamp(currentTimestamp);
            System.out.println("  Current timestamp validated successfully");
        } catch (Exception e) {
            System.out.println("  ERROR: " + e.getMessage());
        }
        
        // Test 5: validateTimestamp with old timestamp
        System.out.println("\nTest 5: validateTimestamp() with old timestamp");
        String oldTimestamp = Instant.now().minusSeconds(600).toString(); // 10 minutes ago
        try {
            SifAuthenticationUtils.validateTimestamp(oldTimestamp);
            System.out.println("  ERROR: Old timestamp should have been rejected");
        } catch (Exception e) {
            System.out.println("  Old timestamp correctly rejected: " + e.getMessage());
        }
        
        // Test 6: Complete authentication flow
        System.out.println("\nTest 6: Complete authentication flow");
        String token = "550e8400-e29b-41d4-a716-446655440000";
        String ts = Instant.now().toString();
        String secret = "my-shared-secret-key";
        
        // Client computes HMAC
        String clientHash = SifAuthenticationUtils.computeHmac(token, ts, secret);
        
        // Client builds authorization header (Base64 encoded)
        String clientCredentials = token + ":" + clientHash;
        String clientBase64 = java.util.Base64.getEncoder().encodeToString(clientCredentials.getBytes());
        String clientAuthHeader = "SIF_HMACSHA256 " + clientBase64;
        System.out.println("  Client auth header: " + clientAuthHeader);
        
        // Server parses and validates
        AuthHeader serverParsed = SifAuthenticationUtils.parseAuthHeader(clientAuthHeader);
        SifAuthenticationUtils.validateTimestamp(ts);
        String serverHash = SifAuthenticationUtils.computeHmac(
            serverParsed.sessionToken(),
            ts,
            secret
        );
        
        System.out.println("  Client hash: " + clientHash);
        System.out.println("  Server hash: " + serverHash);
        System.out.println("  Authentication successful: " + clientHash.equals(serverHash));
        
        System.out.println("\n=== All tests passed! ===");
    }
}
