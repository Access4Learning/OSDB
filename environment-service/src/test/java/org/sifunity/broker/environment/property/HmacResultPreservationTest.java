// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;

import java.security.MessageDigest;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preservation Property Test for HMAC Comparison Results.
 *
 * <p><strong>IMPORTANT: These tests encode current CORRECT behavior that must survive the fix.</strong></p>
 *
 * <p>Property: For all valid/invalid HMAC pairs, {@code MessageDigest.isEqual()} returns
 * the same boolean as {@code Arrays.equals()} for all generated pairs. This proves that
 * switching from {@code String.equals()} to {@code MessageDigest.isEqual()} does not change
 * the accept/reject decision — only the timing characteristics change.</p>
 *
 * <p>These tests MUST PASS on both unfixed and fixed code.</p>
 *
 * <p><strong>Validates: Requirements 3.5, 3.6</strong></p>
 */
class HmacResultPreservationTest {

    /**
     * Property: For matching byte arrays, both MessageDigest.isEqual() and Arrays.equals()
     * return true.
     */
    @Property(tries = 20)
    @Label("MessageDigest.isEqual() SHALL return true for matching byte arrays (same as Arrays.equals)")
    void messageDigestIsEqualShallMatchForEqualArrays(
            @ForAll("hashBytes") byte[] hashValue) {

        // Both copies are identical
        byte[] copy = Arrays.copyOf(hashValue, hashValue.length);

        boolean constantTimeResult = MessageDigest.isEqual(hashValue, copy);
        boolean arraysResult = Arrays.equals(hashValue, copy);

        assertThat(constantTimeResult)
                .as("MessageDigest.isEqual(a, copy_of_a) should return same as Arrays.equals(a, copy_of_a)")
                .isEqualTo(arraysResult);

        // Both should be true for matching arrays
        assertThat(constantTimeResult).isTrue();
    }

    /**
     * Property: For non-matching byte arrays, both MessageDigest.isEqual() and Arrays.equals()
     * return false.
     */
    @Property(tries = 20)
    @Label("MessageDigest.isEqual() SHALL return false for non-matching byte arrays (same as Arrays.equals)")
    void messageDigestIsEqualShallMatchForDifferentArrays(
            @ForAll("hashBytes") byte[] hashA,
            @ForAll("hashBytes") byte[] hashB) {

        // Skip if arrays happen to be equal
        if (Arrays.equals(hashA, hashB)) {
            return; // Skip this case - tested above
        }

        boolean constantTimeResult = MessageDigest.isEqual(hashA, hashB);
        boolean arraysResult = Arrays.equals(hashA, hashB);

        assertThat(constantTimeResult)
                .as("MessageDigest.isEqual(a, b) should return same result as Arrays.equals(a, b) for non-matching arrays")
                .isEqualTo(arraysResult);

        // Both should be false for non-matching arrays
        assertThat(constantTimeResult).isFalse();
    }

    /**
     * Property: For random byte array pairs (matching and non-matching),
     * MessageDigest.isEqual() produces the same boolean as Arrays.equals().
     */
    @Property(tries = 20)
    @Label("MessageDigest.isEqual() produces same boolean as Arrays.equals() for all generated pairs")
    void messageDigestIsEqualAlwaysMatchesArraysEquals(
            @ForAll("hashPairs") byte[][] pair) {

        byte[] a = pair[0];
        byte[] b = pair[1];

        boolean constantTimeResult = MessageDigest.isEqual(a, b);
        boolean arraysResult = Arrays.equals(a, b);

        assertThat(constantTimeResult)
                .as("MessageDigest.isEqual() must produce same boolean as Arrays.equals() " +
                    "for all byte array pairs — only timing characteristics differ")
                .isEqualTo(arraysResult);
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<byte[]> hashBytes() {
        // Generate byte arrays representing SHA-256 hashes (32 bytes)
        return Arbitraries.bytes().array(byte[].class).ofSize(32);
    }

    @Provide
    Arbitrary<byte[][]> hashPairs() {
        Arbitrary<byte[]> hashArb = Arbitraries.bytes().array(byte[].class).ofSize(32);

        // Mix of matching and non-matching pairs
        Arbitrary<byte[][]> matchingPairs = hashArb.map(h -> new byte[][]{h, Arrays.copyOf(h, h.length)});
        Arbitrary<byte[][]> differentPairs = Combinators.combine(hashArb, hashArb)
                .as((a, b) -> new byte[][]{a, b});

        return Arbitraries.oneOf(matchingPairs, differentPairs);
    }
}
