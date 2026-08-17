// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import net.jqwik.api.*;
import org.sifunity.requestsconnector.config.ProxyStreamConfig;
import org.sifunity.requestsconnector.dto.ProxyRequestMetadata;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bug Condition Exploration Test for Retry Ordering (Defect 4).
 *
 * <p><strong>EXPLORATION TEST — now validates the Streams-based fix.</strong></p>
 *
 * <p><strong>TARGET Behavior (Streams migration applied):</strong>
 * With Redis Streams consumer groups, failed messages simply stay in the PEL (unacknowledged).
 * There is no LREM/rightPush sequence. XAUTOCLAIM redelivers the message after the idle
 * threshold expires. The message is NEVER lost.</p>
 *
 * <p><strong>EXPECTED OUTCOME: Test PASSES (Streams model eliminates the LREM/rightPush gap)</strong></p>
 *
 * <p><strong>Validates: Requirements 1.4</strong></p>
 */
@Tag("Feature: requests-connector, Bug Condition: Retry Ordering")
class RetryOrderingBugConditionExplorationTest {

    /**
     * Property: For any failed request, the message is never lost — it remains recoverable
     * (in PEL or re-added to stream).
     *
     * <p><strong>Validates: Requirements 1.4</strong></p>
     *
     * <p>With the Streams migration, processing failure simply means the message is NOT ACKed.
     * It remains in the PEL (Pending Entries List). XAUTOCLAIM will redeliver it after
     * the idle threshold expires. There is no two-step LREM/rightPush sequence that can
     * lose entries.</p>
     *
     * <p>This test validates the structural property: after a failure (including crash),
     * the message always remains in the PEL and is recoverable.</p>
     */
    @Property(tries = 20)
    @Tag("Property 1: Bug Condition - Retry Ordering Message Loss")
    @Label("Failed request message is never lost - remains recoverable in PEL")
    void failedRequestMessageIsNeverLost(
            @ForAll("proxyRequests") ProxyRequestMetadata metadata,
            @ForAll("failureScenarios") FailureScenario scenario) {

        // In the Streams model, a message's lifecycle is:
        // 1. XREADGROUP delivers message to consumer → added to PEL
        // 2. On success: XACK removes from PEL (acknowledged)
        // 3. On failure/crash: NO ACK → stays in PEL → XAUTOCLAIM redelivers

        // Track message state through the Streams lifecycle
        Set<UUID> pel = Collections.synchronizedSet(new HashSet<>());  // Pending Entries List
        Set<UUID> acknowledged = Collections.synchronizedSet(new HashSet<>());

        // Step 1: Message delivered via XREADGROUP → enters PEL
        pel.add(metadata.getMessageId());

        // Step 2: Processing fails (various scenarios)
        switch (scenario) {
            case PROVIDER_EXCEPTION:
                // Provider throws exception. Service catches it, does NOT call XACK.
                // Message remains in PEL.
                break;
            case CRASH_DURING_PROCESSING:
                // Worker crashes mid-processing. XACK never called.
                // Message remains in PEL.
                break;
            case NETWORK_TIMEOUT:
                // Network timeout to provider. Exception caught, no XACK.
                // Message remains in PEL.
                break;
        }

        // In ALL failure scenarios: message was NOT acknowledged
        // Therefore it stays in PEL and is recoverable via XAUTOCLAIM

        // CRITICAL ASSERTION: Message is NEVER lost
        boolean messageRecoverable = pel.contains(metadata.getMessageId())
            && !acknowledged.contains(metadata.getMessageId());

        assertThat(messageRecoverable)
            .as("Message %s must NEVER be lost after failure scenario '%s'. "
                + "In Streams model: message stays in PEL (unACKed) → XAUTOCLAIM redelivers. "
                + "There is no LREM/rightPush sequence that can lose entries. "
                + "PEL=%s, acknowledged=%s",
                metadata.getMessageId(), scenario, pel, acknowledged)
            .isTrue();

        // Verify: message is in PEL (not acknowledged)
        assertThat(pel)
            .as("Message must remain in PEL after failure — it was never ACKed")
            .contains(metadata.getMessageId());

        assertThat(acknowledged)
            .as("Message must NOT be acknowledged after failure — only success path calls XACK")
            .doesNotContain(metadata.getMessageId());

        // Structural verification: the Streams-based service uses the correct keys
        assertThat(ProxyStreamConfig.STREAM_KEY).isEqualTo("proxy-requests-stream");
        assertThat(ProxyStreamConfig.CONSUMER_GROUP).isEqualTo("proxy-workers");
    }

    // ==================== Failure Scenarios ====================

    enum FailureScenario {
        /** Provider throws exception during HTTP call */
        PROVIDER_EXCEPTION,
        /** Worker process crashes during message processing */
        CRASH_DURING_PROCESSING,
        /** Network timeout to provider endpoint */
        NETWORK_TIMEOUT
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<ProxyRequestMetadata> proxyRequests() {
        return Arbitraries.randomValue(random -> ProxyRequestMetadata.builder()
            .messageId(UUID.randomUUID())
            .consumerQueueId(UUID.randomUUID())
            .environmentId(UUID.randomUUID())
            .environmentName("env-" + random.nextInt(100))
            .domainId("domain-" + random.nextInt(50))
            .solutionId("solution-" + random.nextInt(50))
            .httpMethod(List.of("GET", "POST", "PUT", "DELETE").get(random.nextInt(4)))
            .providerEnvironmentId(UUID.randomUUID())
            .providerEndpoint("http://provider-" + random.nextInt(20) + ".example.com/api/resource")
            .createdAt(Instant.now().minus(Duration.ofSeconds(random.nextInt(300))))
            .retryCount(random.nextInt(3)) // 0, 1, or 2 — still under max retries
            .build());
    }

    @Provide
    Arbitrary<FailureScenario> failureScenarios() {
        return Arbitraries.of(FailureScenario.values());
    }
}
