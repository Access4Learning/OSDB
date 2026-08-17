// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit and property tests for {@link CredentialMasker}.
 *
 * <p>Verifies deterministic last-4 masking for long values, full masking for
 * values of length &le; 4, and null/blank handling. The masker must never emit
 * the full input value nor reveal more than the last four characters.</p>
 */
class CredentialMaskerTest {

    // ========== Last-4 masking for long values (length > 4) ==========

    @Test
    void mask_longValue_revealsOnlyLastFour() {
        assertThat(CredentialMasker.mask("abcdef1234")).isEqualTo("****1234");
    }

    @Test
    void mask_fiveCharValue_revealsOnlyLastFour() {
        assertThat(CredentialMasker.mask("abcde")).isEqualTo("****bcde");
    }

    @Test
    void mask_uuidShapedValue_revealsOnlyLastFour() {
        String token = "550e8400-e29b-41d4-a716-446655440000";
        assertThat(CredentialMasker.mask(token)).isEqualTo("****0000");
    }

    @Test
    void mask_longValue_doesNotContainFullValue() {
        String secret = "super-secret-shared-key-value";
        assertThat(CredentialMasker.mask(secret)).doesNotContain(secret);
    }

    // ========== Full masking for short values (length <= 4) ==========

    @Test
    void mask_fourCharValue_returnsPlaceholder() {
        assertThat(CredentialMasker.mask("abcd")).isEqualTo("****");
    }

    @Test
    void mask_singleCharValue_returnsPlaceholder() {
        assertThat(CredentialMasker.mask("a")).isEqualTo("****");
    }

    @Test
    void mask_shortValue_revealsNoPortionOfSecret() {
        assertThat(CredentialMasker.mask("pin9")).isEqualTo("****");
        assertThat(CredentialMasker.mask("pin9")).doesNotContain("pin9");
    }

    // ========== Null / blank handling ==========

    @Test
    void mask_null_returnsPlaceholder() {
        assertThat(CredentialMasker.mask(null)).isEqualTo("****");
    }

    @Test
    void mask_empty_returnsPlaceholder() {
        assertThat(CredentialMasker.mask("")).isEqualTo("****");
    }

    @Test
    void mask_blank_returnsPlaceholder() {
        assertThat(CredentialMasker.mask("   ")).isEqualTo("****");
    }

    // ========== Property: never reveals more than the last 4 characters ==========

    @Property(tries = 5)
    void mask_neverRevealsFullValue(@ForAll @StringLength(min = 5, max = 64) String value) {
        // Blank (whitespace-only) values fall under the null/blank contract and mask to the
        // fixed placeholder, so restrict this property to non-blank generated values.
        Assume.that(!value.isBlank());
        String masked = CredentialMasker.mask(value);
        assertThat(masked).startsWith("****");
        assertThat(masked).isEqualTo("****" + value.substring(value.length() - 4));
        // The revealed portion is at most the last 4 characters.
        assertThat(masked.length()).isEqualTo(8);
    }
}
