// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.property;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.StringLength;
import org.sifunity.testprovider.util.SecretMasker;

/**
 * Property 5: Masking bound.
 *
 * <p><b>Validates: Requirements 3.3, 3.4</b>
 *
 * <p>{@code SecretMasker.mask} reveals at most 8 characters of the original value (the first 4 and
 * the last 4) and is idempotent on already-masked output.
 *
 * <pre>∀ s: |reveal(mask(s))| ≤ 8</pre>
 */
class MaskingBoundPropertyTest {

    /** Number of asterisks the mask inserts between the revealed prefix and suffix. */
    private static final int MASK_ARTIFACT_LENGTH = "****".length();

    /**
     * For any string, the mask reveals at most 8 characters of the original value. Secrets of
     * length ≥ 8 are masked to {@code first4 + "****" + last4} (exactly 8 revealed characters);
     * shorter values collapse to the {@code (not set)} placeholder, revealing nothing.
     */
    @Property(tries = 20)
    void maskRevealsAtMostEightCharacters(@ForAll String secret) {
        String masked = SecretMasker.mask(secret);

        if (secret != null && secret.length() >= 8) {
            // The mask preserves only the first and last 4 characters of the original.
            assertThat(masked).startsWith(secret.substring(0, 4));
            assertThat(masked).endsWith(secret.substring(secret.length() - 4));
            // Revealed characters = masked length minus the inserted "****" artifact.
            int revealed = masked.length() - MASK_ARTIFACT_LENGTH;
            assertThat(revealed).isLessThanOrEqualTo(8);
        } else {
            // Null or short values reveal no characters of the original.
            assertThat(masked).isEqualTo(SecretMasker.NOT_SET);
        }
    }

    /**
     * Masking an already-masked secret (length ≥ 8) yields the same value: the masked form keeps
     * the original prefix and suffix, so re-masking is a no-op.
     */
    @Property(tries = 20)
    void maskIsIdempotentOnMaskedOutput(@ForAll @StringLength(min = 8, max = 256) String secret) {
        String once = SecretMasker.mask(secret);
        String twice = SecretMasker.mask(once);
        assertThat(twice).isEqualTo(once);
    }
}
