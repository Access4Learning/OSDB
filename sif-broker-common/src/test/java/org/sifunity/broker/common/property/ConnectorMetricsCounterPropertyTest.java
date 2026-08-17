// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.*;
import org.sifunity.broker.common.metrics.ConnectorMetricsService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 1: Counter increment correctness.
 *
 * <p>For any valid tag combination, calling a counter method N times SHALL
 * increment the counter by exactly N. Verified for {@code recordConnectorRequest},
 * {@code recordConnectorError}, {@code recordPagingRequest}, and
 * {@code recordPagingTimeout}.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.3, 3.1, 3.4</strong></p>
 */
class ConnectorMetricsCounterPropertyTest {

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
    Arbitrary<String> zones() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(15);
    }

    @Provide
    Arbitrary<String> methods() {
        return Arbitraries.of("GET", "POST", "PUT", "DELETE", "HEAD");
    }

    @Provide
    Arbitrary<Integer> statusCodes() {
        return Arbitraries.of(200, 201, 204, 400, 401, 403, 404, 500, 502, 503);
    }

    @Provide
    Arbitrary<String> errorTypes() {
        return Arbitraries.of("validation", "timeout", "connection", "auth", "internal");
    }

    @Provide
    Arbitrary<Integer> callCounts() {
        return Arbitraries.integers().between(1, 20);
    }

    // ========== Properties ==========

    /**
     * recordConnectorRequest: counter value equals N after N calls.
     *
     * <p><strong>Validates: Requirements 1.1</strong></p>
     */
    @Tag("Feature: observability-instrumentation, Property 1: Counter increment correctness")
    @Property(tries = 100)
    void recordConnectorRequestIncrementsCorrectly(
            @ForAll("connectorNames") String connector,
            @ForAll("sifServiceNames") String sifServiceName,
            @ForAll("zones") String zone,
            @ForAll("methods") String method,
            @ForAll("statusCodes") int status,
            @ForAll("callCounts") int n) {

        MeterRegistry registry = new SimpleMeterRegistry();
        ConnectorMetricsService service = new ConnectorMetricsService(registry, 100);

        for (int i = 0; i < n; i++) {
            service.recordConnectorRequest(connector, sifServiceName, zone, method, status);
        }

        Counter counter = registry.find("sif.connector.requests.total")
                .tag("connector", connector)
                .tag("sif_service_name", sifServiceName)
                .tag("zone", zone)
                .tag("method", method)
                .tag("status", String.valueOf(status))
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo((double) n);
    }

    /**
     * recordConnectorError: counter value equals N after N calls.
     *
     * <p><strong>Validates: Requirements 1.3</strong></p>
     */
    @Tag("Feature: observability-instrumentation, Property 1: Counter increment correctness")
    @Property(tries = 100)
    void recordConnectorErrorIncrementsCorrectly(
            @ForAll("connectorNames") String connector,
            @ForAll("sifServiceNames") String sifServiceName,
            @ForAll("zones") String zone,
            @ForAll("errorTypes") String errorType,
            @ForAll("callCounts") int n) {

        MeterRegistry registry = new SimpleMeterRegistry();
        ConnectorMetricsService service = new ConnectorMetricsService(registry, 100);

        for (int i = 0; i < n; i++) {
            service.recordConnectorError(connector, sifServiceName, zone, errorType);
        }

        Counter counter = registry.find("sif.connector.errors.total")
                .tag("connector", connector)
                .tag("sif_service_name", sifServiceName)
                .tag("zone", zone)
                .tag("error_type", errorType)
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo((double) n);
    }

    /**
     * recordPagingRequest: counter value equals N after N calls.
     *
     * <p><strong>Validates: Requirements 3.1</strong></p>
     */
    @Tag("Feature: observability-instrumentation, Property 1: Counter increment correctness")
    @Property(tries = 100)
    void recordPagingRequestIncrementsCorrectly(
            @ForAll("connectorNames") String connector,
            @ForAll("sifServiceNames") String sifServiceName,
            @ForAll("zones") String zone,
            @ForAll("callCounts") int n) {

        MeterRegistry registry = new SimpleMeterRegistry();
        ConnectorMetricsService service = new ConnectorMetricsService(registry, 100);

        for (int i = 0; i < n; i++) {
            service.recordPagingRequest(connector, sifServiceName, zone);
        }

        Counter counter = registry.find("sif.paging.requests.total")
                .tag("connector", connector)
                .tag("sif_service_name", sifServiceName)
                .tag("zone", zone)
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo((double) n);
    }

    /**
     * recordPagingTimeout: counter value equals N after N calls.
     *
     * <p><strong>Validates: Requirements 3.4</strong></p>
     */
    @Tag("Feature: observability-instrumentation, Property 1: Counter increment correctness")
    @Property(tries = 100)
    void recordPagingTimeoutIncrementsCorrectly(
            @ForAll("connectorNames") String connector,
            @ForAll("sifServiceNames") String sifServiceName,
            @ForAll("callCounts") int n) {

        MeterRegistry registry = new SimpleMeterRegistry();
        ConnectorMetricsService service = new ConnectorMetricsService(registry, 100);

        for (int i = 0; i < n; i++) {
            service.recordPagingTimeout(connector, sifServiceName);
        }

        Counter counter = registry.find("sif.paging.timeouts.total")
                .tag("connector", connector)
                .tag("sif_service_name", sifServiceName)
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo((double) n);
    }
}
