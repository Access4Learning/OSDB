// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.Mockito;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.eventsconnector.client.QueueServiceClient;
import org.sifunity.eventsconnector.dto.DeliveryResultStatus;
import org.sifunity.eventsconnector.dto.EnqueueResponse;
import org.sifunity.eventsconnector.dto.QueueDeliveryResult;
import org.sifunity.eventsconnector.metrics.EventMetrics;
import org.sifunity.eventsconnector.service.QueueEnqueueService;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for QueueEnqueueService fan-out result completeness.
 *
 * <p>Validates that for any non-empty list of queue UUIDs and any SifMessageEnvelope,
 * the returned List&lt;QueueDeliveryResult&gt; has exactly the same size as the input
 * queue list and contains one result entry for each input queue UUID — regardless
 * of whether individual enqueue operations succeed or fail.</p>
 */
// Feature: restclient-netty-removal, Property 5: Fan-out result completeness
@Tag("Feature: restclient-netty-removal")
class QueueEnqueueServicePropertyTest {

    private QueueServiceClient mockClient;
    private EventMetrics mockMetrics;
    private QueueEnqueueService service;

    @BeforeProperty
    void setUp() {
        mockClient = Mockito.mock(QueueServiceClient.class);
        mockMetrics = Mockito.mock(EventMetrics.class);
        doNothing().when(mockMetrics).recordQueueEnqueueLatency(any(), any(), Mockito.anyBoolean());
        service = new QueueEnqueueService(mockClient, mockMetrics);
    }

    /**
     * Property 5: Fan-out result completeness
     *
     * <b>Validates: Requirements 5.2, 5.3</b>
     *
     * For any non-empty list of queue UUIDs and any SifMessageEnvelope,
     * when QueueEnqueueService.enqueueToMultipleQueues is called, the returned
     * List&lt;QueueDeliveryResult&gt; shall have exactly the same size as the input
     * queue list, and shall contain one result entry for each input queue UUID —
     * regardless of whether individual enqueue operations succeed or fail.
     */
    @Property(tries = 100)
    @Tag("Property 5: Fan-out result completeness")
    void fanOutResultCompleteness(
            @ForAll("queueIdLists") List<UUID> queueIds,
            @ForAll("successPatterns") List<Boolean> successFlags
    ) {
        // Align success flags to queue list size (truncate or pad with true)
        List<Boolean> aligned = queueIds.stream()
                .map(id -> {
                    int idx = queueIds.indexOf(id);
                    return idx < successFlags.size() ? successFlags.get(idx) : true;
                })
                .toList();

        // Configure mock: for each queue, either succeed or throw
        for (int i = 0; i < queueIds.size(); i++) {
            UUID queueId = queueIds.get(i);
            if (aligned.get(i)) {
                when(mockClient.enqueueMessage(
                        eq("domain-1"), eq("solution-1"), eq(queueId), any()))
                        .thenReturn(EnqueueResponse.of(
                                UUID.randomUUID(), queueId, "key-" + i, Instant.now(), "ENQUEUED"));
            } else {
                when(mockClient.enqueueMessage(
                        eq("domain-1"), eq("solution-1"), eq(queueId), any()))
                        .thenThrow(new RuntimeException("error"));
            }
        }

        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
                .messageType(MessageType.EVENT)
                .serviceName("TestService")
                .serviceType(ServiceType.OBJECT)
                .zoneId("zone-1")
                .contextId("DEFAULT")
                .payload("test-payload")
                .build();

        // Act
        List<QueueDeliveryResult> results = service.enqueueToMultipleQueues(
                "domain-1", "solution-1", queueIds, envelope);

        // Assert: result list size matches input size
        assertThat(results).hasSize(queueIds.size());

        // Assert: one result per input queue UUID
        Set<String> resultQueueIds = results.stream()
                .map(QueueDeliveryResult::queueId)
                .collect(Collectors.toSet());
        Set<String> inputQueueIds = queueIds.stream()
                .map(UUID::toString)
                .collect(Collectors.toSet());
        assertThat(resultQueueIds).containsExactlyInAnyOrderElementsOf(inputQueueIds);

        // Assert: each result has the correct status based on success/failure
        for (int i = 0; i < queueIds.size(); i++) {
            String queueIdStr = queueIds.get(i).toString();
            QueueDeliveryResult result = results.stream()
                    .filter(r -> r.queueId().equals(queueIdStr))
                    .findFirst()
                    .orElseThrow();

            if (aligned.get(i)) {
                assertThat(result.status()).isEqualTo(DeliveryResultStatus.SUCCESS);
                assertThat(result.error()).isNull();
            } else {
                assertThat(result.status()).isEqualTo(DeliveryResultStatus.FAILED);
                assertThat(result.error()).isNotNull();
            }
        }
    }

    /**
     * Generates non-empty lists of unique queue UUIDs (1–20 elements).
     */
    @Provide
    Arbitrary<List<UUID>> queueIdLists() {
        return Arbitraries.create(UUID::randomUUID)
                .list()
                .ofMinSize(1)
                .ofMaxSize(20)
                .uniqueElements();
    }

    /**
     * Generates lists of booleans indicating success (true) or failure (false)
     * for each queue. Size range matches queue ID lists.
     */
    @Provide
    Arbitrary<List<Boolean>> successPatterns() {
        return Arbitraries.of(true, false)
                .list()
                .ofMinSize(1)
                .ofMaxSize(20);
    }
}
