// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.*;
import org.sifunity.broker.queueservice.metrics.QueueMetricsService;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 2: Timer recording correctness (queue).
 *
 * <p>For any valid queue name and any positive latency value, calling
 * {@code recordDeliveryLatency} SHALL record the duration in a timer with
 * metric name {@code sif.queue.delivery.latency} and the exact
 * {@code queue_name} tag, and the timer's count SHALL increase by 1 and
 * its total time SHALL increase by the recorded duration.</p>
 *
 * <p><strong>Validates: Requirements 2.6</strong></p>
 */
class QueueMetricsTimerPropertyTest {

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> queueNames() {
        return Arbitraries.of(
                "events-queue", "requests-queue", "responses-queue",
                "services-queue", "notifications-queue");
    }

    @Provide
    Arbitrary<Long> latencies() {
        return Arbitraries.longs().between(1L, 30_000L);
    }

    // ========== Properties ==========

    /**
     * recordDeliveryLatency: timer count increases by 1 and total time
     * increases by recorded duration after a single call.
     *
     * <p><strong>Validates: Requirements 2.6</strong></p>
     */
    @Tag("Feature: observability-instrumentation, Property 2: Timer recording correctness")
    @Property(tries = 100)
    void recordDeliveryLatencyRecordsCorrectly(
            @ForAll("queueNames") String queueName,
            @ForAll("latencies") long latencyMs) {

        MeterRegistry registry = new SimpleMeterRegistry();
        QueueMetricsService service = new QueueMetricsService(registry);

        service.recordDeliveryLatency(queueName, latencyMs);

        Timer timer = registry.find("sif.queue.delivery.latency")
                .tag("queue_name", queueName)
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS))
                .isCloseTo((double) latencyMs, org.assertj.core.data.Offset.offset(0.01));
    }

    /**
     * recordDeliveryLatency: timer count equals 2 and total time equals
     * sum of durations after two calls with same queue name.
     *
     * <p><strong>Validates: Requirements 2.6</strong></p>
     */
    @Tag("Feature: observability-instrumentation, Property 2: Timer recording correctness")
    @Property(tries = 100)
    void recordDeliveryLatencyAccumulatesCorrectly(
            @ForAll("queueNames") String queueName,
            @ForAll("latencies") long latencyMs1,
            @ForAll("latencies") long latencyMs2) {

        MeterRegistry registry = new SimpleMeterRegistry();
        QueueMetricsService service = new QueueMetricsService(registry);

        service.recordDeliveryLatency(queueName, latencyMs1);
        service.recordDeliveryLatency(queueName, latencyMs2);

        Timer timer = registry.find("sif.queue.delivery.latency")
                .tag("queue_name", queueName)
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(2L);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS))
                .isCloseTo((double) (latencyMs1 + latencyMs2), org.assertj.core.data.Offset.offset(0.1));
    }
}
