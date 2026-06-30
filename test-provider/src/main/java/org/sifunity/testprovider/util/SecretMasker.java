// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.util;

/**
 * Shared, deterministic masking helper for secret-bearing values (session tokens, shared secrets).
 *
 * <p>This reuses the masking rule originally implemented inline in
 * {@code TestProviderApplication.maskToken}, lifted into a reusable location so the runtime
 * configuration views and the startup banner can apply identical masking.
 *
 * <p>Masking rule:
 * <ul>
 *   <li>Input with length {@code >= 8}: first 4 characters + {@code ****} + last 4 characters.</li>
 *   <li>{@code null} or input shorter than 8 characters: {@code (not set)}.</li>
 * </ul>
 *
 * <p>The function is deterministic and reveals at most the first and last 4 characters of the
 * original value (at most 8 revealed characters).
 */
public final class SecretMasker {

    /** Placeholder returned for null or short inputs. */
    public static final String NOT_SET = "(not set)";

    private SecretMasker() {
        // utility class — no instances
    }

    /**
     * Mask a secret value.
     *
     * @param value the raw value to mask (may be null)
     * @return {@code first4 + "****" + last4} when {@code value} has length >= 8;
     *         otherwise {@code "(not set)"}
     */
    public static String mask(String value) {
        if (value == null || value.length() < 8) {
            return NOT_SET;
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
