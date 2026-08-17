// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

/**
 * Deterministic masking utility for credential values in diagnostic output.
 *
 * <p>Reduces a credential (session token, shared secret, password, etc.) to a
 * non-recoverable representation suitable for safe logging. Unlike a pattern-based
 * redactor, this masker is deterministic: it always reveals at most the last four
 * characters of a sufficiently long value and never reveals any portion of a short
 * value.</p>
 *
 * <p>Masking rules:</p>
 * <ul>
 *   <li>For a value with more than 4 characters: {@code "****" + last 4 characters}
 *       (e.g. {@code "abcdef1234"} becomes {@code "****1234"}).</li>
 *   <li>For a value with 4 or fewer characters, or a {@code null}/blank value:
 *       a fixed placeholder {@code "****"} so that no portion of a short secret is
 *       revealed.</li>
 * </ul>
 *
 * <p>The class is placed in {@code sif-broker-common} so both {@code environment-service}
 * and {@code gateway} can depend on it.</p>
 */
public final class CredentialMasker {

    /** Fixed placeholder used when no characters may be safely revealed. */
    private static final String PLACEHOLDER = "****";

    /** Maximum number of trailing characters that may ever be revealed. */
    private static final int VISIBLE_SUFFIX_LENGTH = 4;

    private CredentialMasker() {
        // Utility class - not instantiable.
    }

    /**
     * Masks a credential value for safe diagnostic logging.
     *
     * @param value the credential value to mask (may be {@code null})
     * @return {@code "****" + last 4 characters} for values longer than 4 characters;
     *         otherwise the fixed placeholder {@code "****"}. Never returns the full
     *         input value and never reveals more than the last 4 characters.
     */
    public static String mask(String value) {
        if (value == null || value.isBlank() || value.length() <= VISIBLE_SUFFIX_LENGTH) {
            return PLACEHOLDER;
        }
        return PLACEHOLDER + value.substring(value.length() - VISIBLE_SUFFIX_LENGTH);
    }
}
