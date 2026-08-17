// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.queueservice.redis.RedisKeyManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for RedisKeyManager key generation.
 *
 * <p>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 6.1, 6.2</p>
 */
class RedisKeyManagerPropertyTest {

    private final RedisKeyManager keyManager = new RedisKeyManager();

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<String> domainIds() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(30);
    }

    @Provide
    Arbitrary<String> solutionIds() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(30);
    }

    @Provide
    Arbitrary<String> storageKeys() {
        return Arbitraries.strings().alpha().numeric().withChars('-', '/', '.').ofMinLength(1).ofMaxLength(60);
    }

    /**
     * Property 1: All generated keys start with domain:{d}:solution:{s}: prefix.
     *
     * <p>Validates: Requirements 1.1, 1.2, 1.3, 1.5, 1.6, 1.7</p>
     */
    @Property(tries = 200)
    @Label("All generated keys start with domain:{d}:solution:{s}: prefix")
    void allKeysStartWithDomainSolutionPrefix(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("uuids") UUID environmentId,
            @ForAll("uuids") UUID queueId,
            @ForAll("storageKeys") String storageKey) {

        String expectedPrefix = "domain:" + domainId + ":solution:" + solutionId + ":";

        assertThat(keyManager.streamKey(domainId, solutionId, environmentId, queueId))
                .startsWith(expectedPrefix);
        assertThat(keyManager.dlqStreamKey(domainId, solutionId, environmentId, queueId))
                .startsWith(expectedPrefix);
        assertThat(keyManager.refCountKey(domainId, solutionId, storageKey))
                .startsWith(expectedPrefix);
        assertThat(keyManager.rateLimitKey(domainId, solutionId, environmentId.toString()))
                .startsWith(expectedPrefix);
    }

    /**
     * Property 2: SCAN patterns match their corresponding generated keys.
     * domainPattern(d) matches any key generated with that domainId.
     * solutionPattern(d, s) matches any key generated with that domainId + solutionId.
     *
     * <p>Validates: Requirements 4.1, 4.2</p>
     */
    @Property(tries = 200)
    @Label("SCAN patterns match their corresponding generated keys")
    void scanPatternsMatchGeneratedKeys(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("uuids") UUID environmentId,
            @ForAll("uuids") UUID queueId,
            @ForAll("storageKeys") String storageKey) {

        // domain pattern is "domain:{d}:*" — glob match means key starts with "domain:{d}:"
        String domainPrefix = "domain:" + domainId + ":";
        String solutionPrefix = "domain:" + domainId + ":solution:" + solutionId + ":";

        // Verify domainPattern matches all generated keys for that domain
        String streamKey = keyManager.streamKey(domainId, solutionId, environmentId, queueId);
        String dlqKey = keyManager.dlqStreamKey(domainId, solutionId, environmentId, queueId);
        String refKey = keyManager.refCountKey(domainId, solutionId, storageKey);
        String rateKey = keyManager.rateLimitKey(domainId, solutionId, environmentId.toString());

        // domainPattern "domain:{d}:*" matches keys starting with "domain:{d}:"
        assertThat(streamKey).startsWith(domainPrefix);
        assertThat(dlqKey).startsWith(domainPrefix);
        assertThat(refKey).startsWith(domainPrefix);
        assertThat(rateKey).startsWith(domainPrefix);

        // solutionPattern "domain:{d}:solution:{s}:*" matches keys starting with that prefix
        assertThat(streamKey).startsWith(solutionPrefix);
        assertThat(dlqKey).startsWith(solutionPrefix);
        assertThat(refKey).startsWith(solutionPrefix);
        assertThat(rateKey).startsWith(solutionPrefix);

        // Verify the patterns themselves have the correct structure
        assertThat(keyManager.domainPattern(domainId)).isEqualTo("domain:" + domainId + ":*");
        assertThat(keyManager.solutionPattern(domainId, solutionId))
                .isEqualTo("domain:" + domainId + ":solution:" + solutionId + ":*");
    }

    /**
     * Property 3: Consumer group name does NOT start with "domain:" prefix.
     * Group names are internal to streams and should not be domain-scoped.
     *
     * <p>Validates: Requirements 1.4</p>
     */
    @Property(tries = 200)
    @Label("Consumer group name does NOT start with domain: prefix")
    void groupNameDoesNotStartWithDomainPrefix(@ForAll("uuids") UUID queueId) {
        String groupName = keyManager.groupName(queueId);

        assertThat(groupName).doesNotStartWith("domain:");
        assertThat(groupName).isEqualTo("group:" + queueId);
    }

    /**
     * Property 4: Legacy patterns match legacy keys.
     * legacyDomainPattern(d) matches legacyStreamKey(d, s, e, q).
     * legacySolutionPattern(d, s) matches legacyStreamKey(d, s, e, q).
     *
     * <p>Validates: Requirements 6.1, 6.2</p>
     */
    @Property(tries = 200)
    @Label("Legacy patterns match legacy keys")
    void legacyPatternsMatchLegacyKeys(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("uuids") UUID environmentId,
            @ForAll("uuids") UUID queueId) {

        String legacyKey = keyManager.legacyStreamKey(domainId, solutionId, environmentId, queueId);

        // legacyDomainPattern is "function:msg_queue:domain:{d}:*"
        // This should match legacyStreamKey which starts with "function:msg_queue:domain:{d}:"
        String legacyDomainPrefix = "function:msg_queue:domain:" + domainId + ":";
        assertThat(legacyKey).startsWith(legacyDomainPrefix);
        assertThat(keyManager.legacyDomainPattern(domainId))
                .isEqualTo(legacyDomainPrefix + "*");

        // legacySolutionPattern is "function:msg_queue:domain:{d}:solution:{s}:*"
        // This should match legacyStreamKey which contains that prefix
        String legacySolutionPrefix = "function:msg_queue:domain:" + domainId
                + ":solution:" + solutionId + ":";
        assertThat(legacyKey).startsWith(legacySolutionPrefix);
        assertThat(keyManager.legacySolutionPattern(domainId, solutionId))
                .isEqualTo(legacySolutionPrefix + "*");
    }
}
