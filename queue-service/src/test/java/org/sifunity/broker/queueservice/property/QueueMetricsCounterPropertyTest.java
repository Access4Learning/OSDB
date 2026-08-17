// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.*;
import org.sifunity.broker.queueservice.metrics.QueueMetricsService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 1: Counter increment correctness (queue).
 *
 * <p>For any valid tag combination, calling a counter method N times SHALL
 * increment the counter by exactly N. Verified for {@code recordEnqueue},
 * {@code recordDequeue}, and {@code recordDlqMessage}.</p>
 *
 * <p><strong>Validates: Requirements 2.1, 2.2, 2.3</strong></p>
 */
class QueueMetricsCounterPropertyTest {

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> queueNames() {
        return Arbitraries.of(
                "events-queue", "requests-queue", "responses-queue",
                "services-queue", "notifications-queue");
    }

    @Provide
    Arbitrary<String> messageTypes() {
        return Arbitraries.of(
                "xEvent", "xRequest", "xResponse",
                "SchoolInfos", "StudentPersonals", "StaffPersonals");
    }

    @Provide
    Arbitrary<Integer> callCounts() {
        return Arbitraries.integers().between(1, 20);
    }

    // ========== Properties ==========

    /**
     * recordEnqueue: counter value equals N after N calls.
     *
     * <p><strong>Validates: Requirements 2.1</strong></p>
     */
    @Tag("Feature: observability-instrumentation, Property 1: Counter increment correctness")
    @Property(tries = 100)
    void recordEnqueueIncrementsCorrectly(
            @ForAll("queueNames") String queueName,
            @ForAll("messageTypes") String messageType,
            @ForAll("callCounts") int n) {

        MeterRegistry registry = new SimpleMeterRegistry();
        QueueMetricsService service = new QueueMetricsService(registry);

        for (int i = 0; i < n; i++) {
            service.recordEnqueue(queueName, messageType);
        }

        Counter counter = registry.find("sif.queue.enqueued.total")
                .tag("queue_name", queueName)
                .tag("message_type", messageType)
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo((double) n);
    }

    /**
     * recordDequeue: counter value equals N after N calls.
     *
     * <p><strong>Validates: Requirements 2.2</strong></p>
     */
    @Tag("Feature: observability-instrumentation, Property 1: Counter increment correctness")
    @Property(tries = 100)
    void recordDequeueIncrementsCorrectly(
            @ForAll("queueNames") String queueName,
            @ForAll("messageTypes") String messageType,
            @ForAll("callCounts") int n) {

        MeterRegistry registry = new SimpleMeterRegistry();
        QueueMetricsService service = new QueueMetricsService(registry);

        for (int i = 0; i < n; i++) {
            service.recordDequeue(queueName, messageType);
        }

        Counter counter = registry.find("sif.queue.dequeued.total")
                .tag("queue_name", queueName)
                .tag("message_type", messageType)
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo((double) n);
    }

    /**
     * recordDlqMessage: counter value equals N after N calls.
     *
     * <p><strong>Validates: Requirements 2.3</strong></p>
     */
    @Tag("Feature: observability-instrumentation, Property 1: Counter increment correctness")
    @Property(tries = 100)
    void recordDlqMessageIncrementsCorrectly(
            @ForAll("queueNames") String queueName,
            @ForAll("callCounts") int n) {

        MeterRegistry registry = new SimpleMeterRegistry();
        QueueMetricsService service = new QueueMetricsService(registry);

        for (int i = 0; i < n; i++) {
            service.recordDlqMessage(queueName);
        }

        Counter counter = registry.find("sif.queue.dlq.total")
                .tag("queue_name", queueName)
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo((double) n);
    }
}
