// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.*;

import org.sifunity.broker.common.metrics.ConnectorMetricsService;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 2: Timer recording correctness.
 *
 * <p>For any valid tag combination and any positive duration value, calling a timer
 * method SHALL record the duration in a timer with the expected metric name and exact
 * tag key-value pairs, and the timer's count SHALL increase by 1 and its total time
 * SHALL increase by the recorded duration.</p>
 *
 * <p><strong>Validates: Requirements 1.2, 3.3</strong></p>
 */
class ConnectorMetricsTimerPropertyTest {

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> connectorNames() {
        return Arbitraries.of(
                "events-connector", "requests-connector",
                "responses-connector", "services-connector");
    }

    @Provide
    Arbitrary<String> sifServiceNames() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
    }

    @Provide
    Arbitrary<Integer> statusCodes() {
        return Arbitraries.of(200, 201, 204, 400, 401, 403, 404, 500, 502, 503);
    }

    @Provide
    Arbitrary<Long> durations() {
        return Arbitraries.longs().between(1L, 30_000L);
    }

    // ========== Properties ==========

    /**
     * recordConnectorRequestDuration: timer count increases by 1 and total time
     * increases by recorded duration after each call.
     *
     * <p><strong>Validates: Requirements 1.2</strong></p>
     */
    @Tag("Feature: observability-instrumentation, Property 2: Timer recording correctness")
    @Property(tries = 100)
    void recordConnectorRequestDurationRecordsCorrectly(
            @ForAll("connectorNames") String connector,
            @ForAll("sifServiceNames") String sifServiceName,
            @ForAll("statusCodes") int status,
            @ForAll("durations") long durationMs) {

        MeterRegistry registry = new SimpleMeterRegistry();
        ConnectorMetricsService service = new ConnectorMetricsService(registry, 100);

        service.recordConnectorRequestDuration(connector, sifServiceName, status, durationMs);

        Timer timer = registry.find("sif.connector.request.duration")
                .tag("connector", connector)
                .tag("sif_service_name", sifServiceName)
                .tag("status", String.valueOf(status))
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS))
                .isCloseTo((double) durationMs, org.assertj.core.data.Offset.offset(0.01));
    }

    /**
     * recordPagingLoopDuration: timer count increases by 1 and total time
     * increases by recorded duration after each call.
     *
     * <p><strong>Validates: Requirements 3.3</strong></p>
     */
    @Tag("Feature: observability-instrumentation, Property 2: Timer recording correctness")
    @Property(tries = 100)
    void recordPagingLoopDurationRecordsCorrectly(
            @ForAll("connectorNames") String connector,
            @ForAll("sifServiceNames") String sifServiceName,
            @ForAll("durations") long durationMs) {

        MeterRegistry registry = new SimpleMeterRegistry();
        ConnectorMetricsService service = new ConnectorMetricsService(registry, 100);

        service.recordPagingLoopDuration(connector, sifServiceName, durationMs);

        Timer timer = registry.find("sif.paging.loop.duration")
                .tag("connector", connector)
                .tag("sif_service_name", sifServiceName)
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS))
                .isCloseTo((double) durationMs, org.assertj.core.data.Offset.offset(0.01));
    }

    /**
     * recordConnectorRequestDuration: timer count equals N and total time equals
     * sum of durations after N calls with same tags.
     *
     * <p><strong>Validates: Requirements 1.2</strong></p>
     */
    @Tag("Feature: observability-instrumentation, Property 2: Timer recording correctness")
    @Property(tries = 100)
    void recordConnectorRequestDurationAccumulatesCorrectly(
            @ForAll("connectorNames") String connector,
            @ForAll("sifServiceNames") String sifServiceName,
            @ForAll("statusCodes") int status,
            @ForAll("durations") long durationMs1,
            @ForAll("durations") long durationMs2) {

        MeterRegistry registry = new SimpleMeterRegistry();
        ConnectorMetricsService service = new ConnectorMetricsService(registry, 100);

        service.recordConnectorRequestDuration(connector, sifServiceName, status, durationMs1);
        service.recordConnectorRequestDuration(connector, sifServiceName, status, durationMs2);

        Timer timer = registry.find("sif.connector.request.duration")
                .tag("connector", connector)
                .tag("sif_service_name", sifServiceName)
                .tag("status", String.valueOf(status))
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(2L);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS))
                .isCloseTo((double) (durationMs1 + durationMs2), org.assertj.core.data.Offset.offset(0.1));
    }

    /**
     * recordPagingLoopDuration: timer count equals N and total time equals
     * sum of durations after N calls with same tags.
     *
     * <p><strong>Validates: Requirements 3.3</strong></p>
     */
    @Tag("Feature: observability-instrumentation, Property 2: Timer recording correctness")
    @Property(tries = 100)
    void recordPagingLoopDurationAccumulatesCorrectly(
            @ForAll("connectorNames") String connector,
            @ForAll("sifServiceNames") String sifServiceName,
            @ForAll("durations") long durationMs1,
            @ForAll("durations") long durationMs2) {

        MeterRegistry registry = new SimpleMeterRegistry();
        ConnectorMetricsService service = new ConnectorMetricsService(registry, 100);

        service.recordPagingLoopDuration(connector, sifServiceName, durationMs1);
        service.recordPagingLoopDuration(connector, sifServiceName, durationMs2);

        Timer timer = registry.find("sif.paging.loop.duration")
                .tag("connector", connector)
                .tag("sif_service_name", sifServiceName)
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(2L);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS))
                .isCloseTo((double) (durationMs1 + durationMs2), org.assertj.core.data.Offset.offset(0.1));
    }
}
