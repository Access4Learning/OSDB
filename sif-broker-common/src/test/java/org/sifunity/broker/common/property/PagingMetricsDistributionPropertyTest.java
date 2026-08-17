// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.property;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.*;
import org.sifunity.broker.common.metrics.ConnectorMetricsService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 4: Distribution summary recording correctness.
 *
 * <p>For any valid connector name and SIF service name, and for any positive
 * integer page count, calling {@code recordPagingPagesPerLoop} SHALL record
 * the value in a distribution summary with metric name
 * {@code sif.paging.pages.per.loop} and the correct tags, and the summary's
 * count SHALL increase by 1.</p>
 *
 * <p><strong>Validates: Requirements 3.2</strong></p>
 */
class PagingMetricsDistributionPropertyTest {

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
    Arbitrary<Integer> pageCounts() {
        return Arbitraries.integers().between(1, 500);
    }

    @Provide
    Arbitrary<Integer> callCounts() {
        return Arbitraries.integers().between(1, 20);
    }

    // ========== Properties ==========

    /**
     * recordPagingPagesPerLoop: distribution summary count equals N after N calls.
     *
     * <p><strong>Validates: Requirements 3.2</strong></p>
     */
    @Tag("Feature: observability-instrumentation, Property 4: Distribution summary recording correctness")
    @Property(tries = 100)
    void recordPagingPagesPerLoopIncrementsCountCorrectly(
            @ForAll("connectorNames") String connector,
            @ForAll("sifServiceNames") String sifServiceName,
            @ForAll("pageCounts") int pages,
            @ForAll("callCounts") int n) {

        MeterRegistry registry = new SimpleMeterRegistry();
        ConnectorMetricsService service = new ConnectorMetricsService(registry, 100);

        for (int i = 0; i < n; i++) {
            service.recordPagingPagesPerLoop(connector, sifServiceName, pages);
        }

        DistributionSummary summary = registry.find("sif.paging.pages.per.loop")
                .tag("connector", connector)
                .tag("sif_service_name", sifServiceName)
                .summary();

        assertThat(summary).isNotNull();
        assertThat(summary.count()).isEqualTo(n);
    }
}
