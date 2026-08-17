// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.*;
import org.sifunity.broker.queueservice.metrics.QueueMetricsService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 3: Gauge value correctness.
 *
 * <p>For any gauge method ({@code updateQueueDepth}, {@code updateDlqDepth})
 * and for any valid queue name and for any non-negative long value, calling
 * the method SHALL set the gauge to exactly that value, and a subsequent call
 * with a different value SHALL update the gauge to the new value.</p>
 *
 * <p><strong>Validates: Requirements 2.4, 2.5</strong></p>
 */
class QueueMetricsGaugePropertyTest {

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> queueNames() {
        return Arbitraries.of(
                "events-queue", "requests-queue", "responses-queue",
                "services-queue", "notifications-queue");
    }

    @Provide
    Arbitrary<Long> depths() {
        return Arbitraries.longs().between(0, 100_000);
    }

    @Provide
    Arbitrary<Long> updatedDepths() {
        return Arbitraries.longs().between(0, 100_000);
    }

    // ========== Properties ==========

    /**
     * updateQueueDepth: gauge reflects exact value after call.
     *
     * <p><strong>Validates: Requirements 2.4</strong></p>
     */
    @Tag("Feature: observability-instrumentation, Property 3: Gauge value correctness")
    @Property(tries = 100)
    void updateQueueDepthReflectsExactValue(
            @ForAll("queueNames") String queueName,
            @ForAll("depths") long depth) {

        MeterRegistry registry = new SimpleMeterRegistry();
        QueueMetricsService service = new QueueMetricsService(registry);

        service.updateQueueDepth(queueName, depth);

        Gauge gauge = registry.find("sif.queue.depth")
                .tag("queue_name", queueName)
                .gauge();

        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo((double) depth);
    }

    /**
     * updateQueueDepth: gauge updates correctly on subsequent calls.
     *
     * <p><strong>Validates: Requirements 2.4</strong></p>
     */
    @Tag("Feature: observability-instrumentation, Property 3: Gauge value correctness")
    @Property(tries = 100)
    void updateQueueDepthUpdatesOnSubsequentCalls(
            @ForAll("queueNames") String queueName,
            @ForAll("depths") long firstDepth,
            @ForAll("updatedDepths") long secondDepth) {

        MeterRegistry registry = new SimpleMeterRegistry();
        QueueMetricsService service = new QueueMetricsService(registry);

        service.updateQueueDepth(queueName, firstDepth);
        service.updateQueueDepth(queueName, secondDepth);

        Gauge gauge = registry.find("sif.queue.depth")
                .tag("queue_name", queueName)
                .gauge();

        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo((double) secondDepth);
    }

    /**
     * updateDlqDepth: gauge reflects exact value after call.
     *
     * <p><strong>Validates: Requirements 2.5</strong></p>
     */
    @Tag("Feature: observability-instrumentation, Property 3: Gauge value correctness")
    @Property(tries = 100)
    void updateDlqDepthReflectsExactValue(
            @ForAll("queueNames") String queueName,
            @ForAll("depths") long depth) {

        MeterRegistry registry = new SimpleMeterRegistry();
        QueueMetricsService service = new QueueMetricsService(registry);

        service.updateDlqDepth(queueName, depth);

        Gauge gauge = registry.find("sif.queue.dlq.depth")
                .tag("queue_name", queueName)
                .gauge();

        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo((double) depth);
    }

    /**
     * updateDlqDepth: gauge updates correctly on subsequent calls.
     *
     * <p><strong>Validates: Requirements 2.5</strong></p>
     */
    @Tag("Feature: observability-instrumentation, Property 3: Gauge value correctness")
    @Property(tries = 100)
    void updateDlqDepthUpdatesOnSubsequentCalls(
            @ForAll("queueNames") String queueName,
            @ForAll("depths") long firstDepth,
            @ForAll("updatedDepths") long secondDepth) {

        MeterRegistry registry = new SimpleMeterRegistry();
        QueueMetricsService service = new QueueMetricsService(registry);

        service.updateDlqDepth(queueName, firstDepth);
        service.updateDlqDepth(queueName, secondDepth);

        Gauge gauge = registry.find("sif.queue.dlq.depth")
                .tag("queue_name", queueName)
                .gauge();

        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo((double) secondDepth);
    }
}
