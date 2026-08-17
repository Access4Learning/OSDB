// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import org.sifunity.broker.common.constant.DefaultValues;
import org.sifunity.broker.common.exception.InvalidTimestampException;
import org.sifunity.broker.common.exception.MalformedAuthHeaderException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * Utility class for SIF HMAC-SHA256 authentication operations.
 * 
 * <p>This class provides methods for computing HMAC signatures, parsing
 * authorization headers, and validating timestamps according to the SIF 3.7
 * specification. It is used by the Gateway to authenticate incoming requests
 * from consumer and provider applications.</p>
 * 
 * <p>SIF authentication uses HMAC-SHA256 signatures computed from:
 * <ul>
 *   <li>Session token - unique identifier for the environment</li>
 *   <li>Timestamp - ISO-8601 formatted request timestamp (sent in separate header)</li>
 *   <li>Shared secret - secret key known to both client and broker</li>
 * </ul>
 * 
 * <p>The authorization header format is:
 * <pre>Authorization: SIF_HMACSHA256 Base64({sessionToken}:{hash})</pre>
 * <pre>timestamp: {ISO-8601 timestamp}</pre>
 * 
 * <p>Where hash is computed as: HMAC-SHA256({sessionToken}:{timestamp}, sharedSecret)</p>
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Compute HMAC signature
 * String hash = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);
 * 
 * // Build authorization header
 * String authValue = sessionToken + ":" + hash;
 * String authHeader = "SIF_HMACSHA256 " + Base64.getEncoder().encodeToString(authValue.getBytes());
 * 
 * // Parse authorization header
 * AuthHeader authHeader = SifAuthenticationUtils.parseAuthHeader(authHeaderValue);
 * 
 * // Validate timestamp is within tolerance window
 * SifAuthenticationUtils.validateTimestamp(timestamp);
 * 
 * // Verify signature
 * String expectedHash = SifAuthenticationUtils.computeHmac(
 *     authHeader.sessionToken(), 
 *     timestamp, 
 *     sharedSecret
 * );
 * if (!expectedHash.equals(authHeader.hash())) {
 *     throw new InvalidTokenException("HMAC signature mismatch");
 * }
 * }</pre>
 * 
 * @see DefaultValues#TIMESTAMP_TOLERANCE_MINUTES
 * @see MalformedAuthHeaderException
 * @see InvalidTimestampException
 */
public final class SifAuthenticationUtils {
    
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    
    /**
     * Computes an HMAC-SHA256 signature for SIF authentication.
     * 
     * <p>The signature is computed over the concatenated string:
     * <pre>{sessionToken}:{timestamp}</pre>
     * using the shared secret as the HMAC key. The result is Base64-encoded.</p>
     * 
     * <p>This method is deterministic - calling it multiple times with the same
     * inputs will always produce the same output.</p>
     * 
     * @param sessionToken the session token identifying the environment
     * @param timestamp    the ISO-8601 formatted timestamp
     * @param sharedSecret the shared secret key for HMAC computation
     * @return the Base64-encoded HMAC-SHA256 signature
     * @throws RuntimeException if HMAC computation fails (e.g., algorithm not available)
     */
    public static String computeHmac(String sessionToken, String timestamp, String sharedSecret) {
        try {
            String data = sessionToken + ":" + timestamp;
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(
                sharedSecret.getBytes(StandardCharsets.UTF_8), 
                HMAC_ALGORITHM
            );
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }
    
    /**
     * Parses a SIF authorization header into its component parts.
     * 
     * <p>The expected format is:
     * <pre>SIF_HMACSHA256 Base64({sessionToken}:{hash})</pre>
     * 
     * <p>This method validates the header format, decodes the Base64 payload,
     * and extracts the sessionToken and hash components into an {@link AuthHeader} record.</p>
     * 
     * @param authHeader the authorization header value to parse
     * @return an {@link AuthHeader} record containing the parsed components
     * @throws MalformedAuthHeaderException if the header format is invalid
     */
    public static AuthHeader parseAuthHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("SIF_HMACSHA256 ")) {
            throw new MalformedAuthHeaderException("Invalid authorization header format");
        }
        
        // Extract the Base64-encoded part after "SIF_HMACSHA256 "
        String base64Credentials = authHeader.substring(15);
        
        // Decode from Base64
        String credentials;
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
            credentials = new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new MalformedAuthHeaderException("Invalid Base64 encoding in authorization header");
        }
        
        // Split on colon to get sessionToken:hash
        // Format: sessionToken:hash
        int colonIndex = credentials.indexOf(':');
        if (colonIndex == -1) {
            throw new MalformedAuthHeaderException("Authorization header must contain sessionToken:hash");
        }
        
        String sessionToken = credentials.substring(0, colonIndex);
        String hash = credentials.substring(colonIndex + 1);
        
        // Validate that components don't contain null bytes or control characters (check FIRST)
        if (containsInvalidCharacters(sessionToken) || containsInvalidCharacters(hash)) {
            throw new MalformedAuthHeaderException("Authorization header contains invalid characters");
        }
        
        // Validate that both components are present and not empty
        if (sessionToken.isBlank() || hash.isBlank()) {
            throw new MalformedAuthHeaderException("Authorization header components cannot be empty");
        }
        
        return new AuthHeader(sessionToken, hash);
    }
    
    /**
     * Checks if a string contains invalid characters (null bytes or control characters).
     * 
     * <p>This method validates that the string contains only printable characters.
     * It rejects:
     * <ul>
     *   <li>Null bytes (\0)</li>
     *   <li>Control characters (ASCII 0-31) except tab (\t), newline (\n), and carriage return (\r)</li>
     * </ul>
     * 
     * <p>This validation prevents malformed authorization headers from being accepted
     * when they contain non-printable characters that could indicate corruption or
     * malicious input.</p>
     * 
     * @param value the string to validate
     * @return true if the string contains invalid characters, false otherwise
     */
    private static boolean containsInvalidCharacters(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            // Check for null bytes and ALL control characters (0x00-0x1F)
            if (c < 32) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Validates that a timestamp is within the default tolerance window.
     * 
     * <p>This method uses the default tolerance of
     * {@link DefaultValues#TIMESTAMP_TOLERANCE_MINUTES} minutes.</p>
     * 
     * @param timestamp the ISO-8601 formatted timestamp to validate
     * @throws InvalidTimestampException if the timestamp is outside the tolerance window
     *                                   or cannot be parsed
     * @see #validateTimestamp(String, long)
     */
    public static void validateTimestamp(String timestamp) {
        validateTimestamp(timestamp, DefaultValues.TIMESTAMP_TOLERANCE_MINUTES);
    }
    
    /**
     * Validates that a timestamp is within a specified tolerance window.
     * 
     * <p>The timestamp must be in ISO-8601 format (e.g., "2024-01-15T10:30:00Z").
     * The tolerance window extends both before and after the current time.</p>
     * 
     * <p>For example, with a tolerance of 5 minutes:
     * <ul>
     *   <li>Current time: 10:00:00</li>
     *   <li>Valid range: 09:55:00 to 10:05:00</li>
     * </ul>
     * 
     * @param timestamp        the ISO-8601 formatted timestamp to validate
     * @param toleranceMinutes the tolerance window in minutes
     * @throws InvalidTimestampException if the timestamp is outside the tolerance window
     *                                   or cannot be parsed as ISO-8601
     */
    public static void validateTimestamp(String timestamp, long toleranceMinutes) {
        validateTimestampWithReference(timestamp, toleranceMinutes, Instant.now());
    }
    
    /**
     * Validates that a timestamp is within a specified tolerance window using a reference time.
     * 
     * <p>This package-private method allows tests to provide a fixed reference time for
     * deterministic testing, while the public API continues to use {@code Instant.now()}.</p>
     * 
     * <p>The timestamp must be in ISO-8601 format (e.g., "2024-01-15T10:30:00Z").
     * The tolerance window extends both before and after the reference time.</p>
     * 
     * @param timestamp        the ISO-8601 formatted timestamp to validate
     * @param toleranceMinutes the tolerance window in minutes
     * @param referenceTime    the reference time to compare against
     * @throws InvalidTimestampException if the timestamp is outside the tolerance window
     *                                   or cannot be parsed as ISO-8601
     */
    static void validateTimestampWithReference(String timestamp, long toleranceMinutes, Instant referenceTime) {
        try {
            Instant requestTime = Instant.parse(timestamp);
            long minutesDiff = Math.abs(ChronoUnit.MINUTES.between(requestTime, referenceTime));
            
            if (minutesDiff > toleranceMinutes) {
                throw new InvalidTimestampException(
                    "Timestamp is outside tolerance window (" + toleranceMinutes + " minutes)"
                );
            }
        } catch (Exception e) {
            if (e instanceof InvalidTimestampException) {
                throw e;
            }
            throw new InvalidTimestampException("Invalid timestamp format: " + timestamp);
        }
    }
    
    /**
     * Record representing the parsed components of a SIF authorization header.
     * 
     * <p>Note: The timestamp is sent in a separate "timestamp" header, not in the authorization header.</p>
     * 
     * @param sessionToken the session token identifying the environment
     * @param hash         the Base64-encoded HMAC-SHA256 signature
     */
    public record AuthHeader(String sessionToken, String hash) {}
    
    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private SifAuthenticationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
}
