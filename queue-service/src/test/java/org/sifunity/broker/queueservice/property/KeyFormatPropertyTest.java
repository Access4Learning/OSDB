// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.util.StorageKeyGenerator;
import org.sifunity.broker.queueservice.redis.RedisKeyManager;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Property 2: Stream and storage key format — validates key generation patterns.
 *
 * <p>Validates: Requirements 1.2, 1.3, 8.4, 13.1, 13.2, 13.5, 13.6</p>
 */
class KeyFormatPropertyTest {

    private final RedisKeyManager keyManager = new RedisKeyManager();
    private final StreamMessageRepository smr =
            new StreamMessageRepository(mock(StringRedisTemplate.class), keyManager);
    private final RefCountService refCountService =
            new RefCountService(mock(StringRedisTemplate.class), keyManager);

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<String> alphaStrings() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
    }

    @Provide
    Arbitrary<String> messageTypes() {
        return Arbitraries.of("event", "request", "response");
    }

    @Provide
    Arbitrary<String> prefixes() {
        return Arbitraries.of("", "sif-messages", "osdb");
    }

    /**
     * **Validates: Requirements 1.2, 8.4, 13.1, 13.2, 13.5, 13.6**
     */
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 2: Stream and storage key format")
    void keysMatchExpectedFormats(
            @ForAll("alphaStrings") String domainId,
            @ForAll("alphaStrings") String solutionId,
            @ForAll("uuids") UUID environmentId,
            @ForAll("uuids") UUID queueId,
            @ForAll("prefixes") String prefix,
            @ForAll("alphaStrings") String zone,
            @ForAll("alphaStrings") String service,
            @ForAll("messageTypes") String messageType,
            @ForAll("uuids") UUID messageId) {

        // Stream key format (new domain-prefixed format)
        String streamKey = smr.streamKey(domainId, solutionId, environmentId, queueId);
        String expectedStreamKey = "domain:" + domainId
                + ":solution:" + solutionId
                + ":function:msg_queue:env:" + environmentId
                + ":queue:" + queueId;
        assertThat(streamKey).isEqualTo(expectedStreamKey);

        // DLQ key = stream key + :dlq
        String dlqKey = smr.dlqStreamKey(domainId, solutionId, environmentId, queueId);
        assertThat(dlqKey).isEqualTo(expectedStreamKey + ":dlq");

        // Group name = group:{queueId}
        String groupName = smr.groupName(queueId);
        assertThat(groupName).isEqualTo("group:" + queueId);

        // Storage key format with context
        String storageKey = StorageKeyGenerator.generateKey(
                prefix, domainId, solutionId, environmentId.toString(),
                zone, "default", messageType, service, messageId);
        assertThat(storageKey).endsWith(messageId + ".json");
        assertThat(storageKey).contains(messageType);

        if (prefix != null && !prefix.isEmpty()) {
            assertThat(storageKey).startsWith(prefix + "/");
        } else {
            assertThat(storageKey).startsWith(domainId + "/");
        }

        // Storage key with null context defaults to "default"
        String keyWithNullCtx = StorageKeyGenerator.generateKey(
                prefix, domainId, solutionId, environmentId.toString(),
                zone, null, messageType, service, messageId);
        assertThat(keyWithNullCtx).contains("/default/");
    }

    /**
     * **Validates: Requirement 1.3**
     *
     * Ref count keys must follow the format: domain:{d}:solution:{s}:ref:{storageKey}
     */
    @Property(tries = 100)
    @Label("Feature: domain-solution-cascade-cleanup, Property: Ref count key format")
    void refCountKeysMatchExpectedFormat(
            @ForAll("alphaStrings") String domainId,
            @ForAll("alphaStrings") String solutionId,
            @ForAll("alphaStrings") String storageKey) {

        String refKey = refCountService.refCountKey(domainId, solutionId, storageKey);

        // Must start with domain prefix
        String expectedKey = "domain:" + domainId + ":solution:" + solutionId + ":ref:" + storageKey;
        assertThat(refKey).isEqualTo(expectedKey);

        // Must start with domain:{d}:solution:{s}: prefix (scannable)
        assertThat(refKey).startsWith("domain:" + domainId + ":solution:" + solutionId + ":");

        // Must NOT start with legacy prefix
        assertThat(refKey).doesNotStartWith("function:msg_queue:");
    }
}
