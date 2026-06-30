// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * HMAC-SHA256 authentication utility.
 * <p>
 * Produces the {@code Authorization} and {@code timestamp} headers required by
 * the SIF Unity Broker gateway.
 * <p>
 * Algorithm (matches Bruno {@code common/auth.js}):
 * <ol>
 *   <li>timestamp = ISO 8601 instant (e.g. {@code 2026-01-21T06:30:00.000Z})</li>
 *   <li>message   = {@code sessionToken + ":" + timestamp}</li>
 *   <li>hash      = Base64( HMAC-SHA256(message, sharedSecret) )</li>
 *   <li>header    = {@code "SIF_HMACSHA256 " + Base64( sessionToken + ":" + hash )}</li>
 * </ol>
 */
public final class HmacAuthUtil {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String AUTH_PREFIX = "SIF_HMACSHA256 ";

    private HmacAuthUtil() { }

    /**
     * Generate an Authorization header value and its companion timestamp.
     *
     * @param sessionToken the provider's session token
     * @param sharedSecret the provider's shared secret
     * @return a two-element array: [0] = Authorization header value, [1] = timestamp
     */
    public static String[] generateAuthHeaders(String sessionToken, String sharedSecret) {
        String timestamp = Instant.now().toString(); // ISO 8601

        String message = sessionToken + ":" + timestamp;
        String hash = hmacSha256Base64(message, sharedSecret);

        String tokenAndHash = sessionToken + ":" + hash;
        String encoded = Base64.getEncoder().encodeToString(
                tokenAndHash.getBytes(StandardCharsets.UTF_8));

        String authorization = AUTH_PREFIX + encoded;
        return new String[]{ authorization, timestamp };
    }

    private static String hmacSha256Base64(String data, String secret) {
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
