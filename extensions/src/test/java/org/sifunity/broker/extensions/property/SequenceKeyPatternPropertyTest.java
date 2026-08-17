// Feature: message-sequence-extension, Property 1: Sequence key pattern correctness
package org.sifunity.broker.extensions.property;

import net.jqwik.api.*;
import org.sifunity.broker.extensions.sequencing.SequenceService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 1: Sequence key pattern correctness.
 *
 * <p>For any valid domainId and solutionId, {@link SequenceService#buildKey(String, String)}
 * produces a key matching {@code domain:{domainId}:solution:{solutionId}:messagesequence},
 * and parsing the key back recovers the original domainId and solutionId values.</p>
 *
 * <p><strong>Validates: Requirements 2.1, 2.2</strong></p>
 */
@PropertyDefaults(tries = 10)
class SequenceKeyPatternPropertyTest {

    // ========== Arbitraries ==========

    /**
     * Generates non-empty alphanumeric strings (no colons) to avoid ambiguity
     * when splitting the key on the colon delimiter.
     */
    @Provide
    Arbitrary<String> alphanumericIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    // ========== Properties ==========

    /**
     * buildKey SHALL produce a key matching the exact pattern
     * {@code domain:{domainId}:solution:{solutionId}:messagesequence}.
     */
    @Property
    @Tag("Feature: message-sequence-extension, Property 1: Sequence key pattern correctness")
    void buildKeyProducesCorrectPattern(
            @ForAll("alphanumericIds") String domainId,
            @ForAll("alphanumericIds") String solutionId) {

        String key = SequenceService.buildKey(domainId, solutionId);

        String expected = "domain:" + domainId + ":solution:" + solutionId + ":messagesequence";
        assertThat(key).isEqualTo(expected);
    }

    /**
     * Parsing the key produced by buildKey SHALL recover the original
     * domainId and solutionId values.
     */
    @Property
    @Tag("Feature: message-sequence-extension, Property 1: Sequence key pattern correctness")
    void buildKeyRoundTripsViaParsingRecovery(
            @ForAll("alphanumericIds") String domainId,
            @ForAll("alphanumericIds") String solutionId) {

        String key = SequenceService.buildKey(domainId, solutionId);

        // Key format: domain:{domainId}:solution:{solutionId}:messagesequence
        // Split on known delimiters to recover original values
        assertThat(key).startsWith("domain:");
        assertThat(key).endsWith(":messagesequence");

        // Strip prefix "domain:" and suffix ":messagesequence"
        String middle = key.substring("domain:".length(),
                key.length() - ":messagesequence".length());

        // middle = {domainId}:solution:{solutionId}
        String solutionDelimiter = ":solution:";
        int delimiterIndex = middle.indexOf(solutionDelimiter);
        assertThat(delimiterIndex).isGreaterThan(0);

        String parsedDomainId = middle.substring(0, delimiterIndex);
        String parsedSolutionId = middle.substring(delimiterIndex + solutionDelimiter.length());

        assertThat(parsedDomainId).isEqualTo(domainId);
        assertThat(parsedSolutionId).isEqualTo(solutionId);
    }
}
