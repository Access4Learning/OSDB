// SPDX-License-Identifier: MIT
package org.sifunity.loadtest.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * HMAC-SHA256 authentication utility for Gatling load tests.
 * <p>
 * Produces the {@code Authorization} and {@code timestamp} headers required by
 * the SIF Unity Broker gateway. Algorithm matches
 * {@code test-provider/HmacAuthUtil.java} and {@code bruno-tests/common/auth.js}:
 * <ol>
 *   <li>timestamp = ISO 8601 instant (e.g. {@code 2026-01-21T06:30:00.000Z})</li>
 *   <li>message   = {@code sessionToken + ":" + timestamp}</li>
 *   <li>hash      = Base64( HMAC-SHA256(message, sharedSecret) )</li>
 *   <li>header    = {@code "SIF_HMACSHA256 " + Base64( sessionToken + ":" + hash )}</li>
 * </ol>
 */
public final class HmacAuthenticator {

    private static final String ALGORITHM = "HmacSHA256";
    static final String AUTH_PREFIX = "SIF_HMACSHA256 ";

    private HmacAuthenticator() { }

    /**
     * Generate Authorization and timestamp headers using the current instant.
     *
     * @param sessionToken environment session token
     * @param sharedSecret environment shared secret
     * @return two-element array: [0] = Authorization header, [1] = timestamp
     */
    public static String[] generateAuthHeaders(String sessionToken, String sharedSecret) {
        return generateAuthHeaders(sessionToken, sharedSecret, Instant.now().toString());
    }

    /**
     * Generate Authorization and timestamp headers using an explicit timestamp.
     * Useful for deterministic testing with pre-computed HMAC values.
     *
     * @param sessionToken environment session token
     * @param sharedSecret environment shared secret
     * @param timestamp    ISO 8601 timestamp string
     * @return two-element array: [0] = Authorization header, [1] = timestamp
     */
    public static String[] generateAuthHeaders(String sessionToken, String sharedSecret, String timestamp) {
        String message = sessionToken + ":" + timestamp;
        String hash = hmacSha256Base64(message, sharedSecret);
        String tokenAndHash = sessionToken + ":" + hash;
        String encoded = Base64.getEncoder()
                .encodeToString(tokenAndHash.getBytes(StandardCharsets.UTF_8));
        return new String[]{ AUTH_PREFIX + encoded, timestamp };
    }

    /**
     * Compute HMAC-SHA256 of {@code data} keyed with {@code secret}, returned as Base64.
     * Package-private to allow direct verification in tests.
     */
    static String hmacSha256Base64(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256", e);
        }
    }
}
