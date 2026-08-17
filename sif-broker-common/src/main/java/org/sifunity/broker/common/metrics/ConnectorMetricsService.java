// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for recording Connector and Paging metrics.
 *
 * <p>This service provides methods to record metrics for all four SIF connectors
 * (Events, Requests, Responses, Services) and paging loop operations.</p>
 *
 * <p>Connector Metrics:</p>
 * <ul>
 *   <li>sif.connector.requests.total - Counter for connector requests</li>
 *   <li>sif.connector.request.duration - Timer for request processing duration</li>
 *   <li>sif.connector.errors.total - Counter for connector errors</li>
 * </ul>
 *
 * <p>Paging Metrics:</p>
 * <ul>
 *   <li>sif.paging.requests.total - Counter for paged requests</li>
 *   <li>sif.paging.pages.per.loop - Distribution summary for pages per loop</li>
 *   <li>sif.paging.loop.duration - Timer for paging loop duration</li>
 *   <li>sif.paging.timeouts.total - Counter for paging timeouts</li>
 * </ul>
 *
 * <p>Cardinality limiting is applied: when the number of unique tag combinations
 * for a metric exceeds the configured limit, new tag values are replaced with
 * {@code _other_} to prevent metric explosion.</p>
 *
 * <p>Requirements: 1.1, 1.2, 1.3, 1.4, 3.1, 3.2, 3.3, 3.4</p>
 */
@Slf4j
@Service
public class ConnectorMetricsService {

    private static final String UNKNOWN = "unknown";
    private static final String OTHER = "_other_";

    private static final String TAG_CONNECTOR = "connector";
    private static final String TAG_SIF_SERVICE_NAME = "sif_service_name";
    private static final String TAG_ZONE = "zone";
    private static final String TAG_METHOD = "method";
    private static final String TAG_STATUS = "status";
    private static final String TAG_ERROR_TYPE = "error_type";

    private final MeterRegistry meterRegistry;
    private final int cardinalityLimit;

    /**
     * Tracks the number of unique tag-value combinations seen per metric name.
     * Key: "metricName:tagKey", Value: set of seen values (tracked via count).
     */
    private final Map<String, ConcurrentHashMap<String, Boolean>> cardinalityTracker = new ConcurrentHashMap<>();

    /**
     * Constructs a ConnectorMetricsService with the specified meter registry and cardinality limit.
     *
     * @param meterRegistry   the Micrometer meter registry
     * @param cardinalityLimit maximum unique tag combinations per metric (default 100)
     */
    public ConnectorMetricsService(
            MeterRegistry meterRegistry,
            @Value("${observability.metrics.cardinality.limit:100}") int cardinalityLimit) {
        this.meterRegistry = meterRegistry;
        this.cardinalityLimit = cardinalityLimit;
    }

    /**
     * Records a connector request.
     *
     * @param connector      the connector name (e.g., "events-connector")
     * @param sifServiceName the SIF service name (e.g., "SchoolInfos")
     * @param zone           the SIF zone identifier
     * @param method         the HTTP method (e.g., "GET", "POST")
     * @param status         the HTTP status code
     */
    public void recordConnectorRequest(String connector, String sifServiceName,
                                       String zone, String method, int status) {
        Counter.builder("sif.connector.requests.total")
                .description("Total connector requests")
                .tag(TAG_CONNECTOR, sanitize(connector))
                .tag(TAG_SIF_SERVICE_NAME, applyCardinalityLimit("sif.connector.requests.total", TAG_SIF_SERVICE_NAME, sanitize(sifServiceName)))
                .tag(TAG_ZONE, applyCardinalityLimit("sif.connector.requests.total", TAG_ZONE, sanitize(zone)))
                .tag(TAG_METHOD, sanitize(method))
                .tag(TAG_STATUS, String.valueOf(status))
                .register(meterRegistry)
                .increment();
    }

    /**
     * Records the duration of a connector request.
     *
     * @param connector      the connector name
     * @param sifServiceName the SIF service name
     * @param status         the HTTP status code
     * @param durationMs     the duration in milliseconds (negative values clamped to 0)
     */
    public void recordConnectorRequestDuration(String connector, String sifServiceName,
                                               int status, long durationMs) {
        long safeDuration = Math.max(0, durationMs);
        Timer.builder("sif.connector.request.duration")
                .description("Connector request processing duration")
                .tag(TAG_CONNECTOR, sanitize(connector))
                .tag(TAG_SIF_SERVICE_NAME, applyCardinalityLimit("sif.connector.request.duration", TAG_SIF_SERVICE_NAME, sanitize(sifServiceName)))
                .tag(TAG_STATUS, String.valueOf(status))
                .register(meterRegistry)
                .record(safeDuration, TimeUnit.MILLISECONDS);
    }

    /**
     * Records a connector error.
     *
     * @param connector      the connector name
     * @param sifServiceName the SIF service name
     * @param zone           the SIF zone identifier
     * @param errorType      the error type (e.g., "validation", "timeout")
     */
    public void recordConnectorError(String connector, String sifServiceName,
                                     String zone, String errorType) {
        Counter.builder("sif.connector.errors.total")
                .description("Total connector errors")
                .tag(TAG_CONNECTOR, sanitize(connector))
                .tag(TAG_SIF_SERVICE_NAME, applyCardinalityLimit("sif.connector.errors.total", TAG_SIF_SERVICE_NAME, sanitize(sifServiceName)))
                .tag(TAG_ZONE, applyCardinalityLimit("sif.connector.errors.total", TAG_ZONE, sanitize(zone)))
                .tag(TAG_ERROR_TYPE, sanitize(errorType))
                .register(meterRegistry)
                .increment();
    }

    /**
     * Records a paging request initiation.
     *
     * @param connector      the connector name
     * @param sifServiceName the SIF service name
     * @param zone           the SIF zone identifier
     */
    public void recordPagingRequest(String connector, String sifServiceName, String zone) {
        Counter.builder("sif.paging.requests.total")
                .description("Total paged requests initiated")
                .tag(TAG_CONNECTOR, sanitize(connector))
                .tag(TAG_SIF_SERVICE_NAME, applyCardinalityLimit("sif.paging.requests.total", TAG_SIF_SERVICE_NAME, sanitize(sifServiceName)))
                .tag(TAG_ZONE, applyCardinalityLimit("sif.paging.requests.total", TAG_ZONE, sanitize(zone)))
                .register(meterRegistry)
                .increment();
    }

    /**
     * Records the number of pages fetched in a paging loop.
     *
     * @param connector      the connector name
     * @param sifServiceName the SIF service name
     * @param pages          the number of pages fetched
     */
    public void recordPagingPagesPerLoop(String connector, String sifServiceName, int pages) {
        DistributionSummary.builder("sif.paging.pages.per.loop")
                .description("Pages fetched per paging loop")
                .tag(TAG_CONNECTOR, sanitize(connector))
                .tag(TAG_SIF_SERVICE_NAME, applyCardinalityLimit("sif.paging.pages.per.loop", TAG_SIF_SERVICE_NAME, sanitize(sifServiceName)))
                .register(meterRegistry)
                .record(pages);
    }

    /**
     * Records the duration of a paging loop.
     *
     * @param connector      the connector name
     * @param sifServiceName the SIF service name
     * @param durationMs     the duration in milliseconds (negative values clamped to 0)
     */
    public void recordPagingLoopDuration(String connector, String sifServiceName, long durationMs) {
        long safeDuration = Math.max(0, durationMs);
        Timer.builder("sif.paging.loop.duration")
                .description("Paging loop total duration")
                .tag(TAG_CONNECTOR, sanitize(connector))
                .tag(TAG_SIF_SERVICE_NAME, applyCardinalityLimit("sif.paging.loop.duration", TAG_SIF_SERVICE_NAME, sanitize(sifServiceName)))
                .register(meterRegistry)
                .record(safeDuration, TimeUnit.MILLISECONDS);
    }

    /**
     * Records a paging timeout event.
     *
     * @param connector      the connector name
     * @param sifServiceName the SIF service name
     */
    public void recordPagingTimeout(String connector, String sifServiceName) {
        Counter.builder("sif.paging.timeouts.total")
                .description("Total paging loop timeouts")
                .tag(TAG_CONNECTOR, sanitize(connector))
                .tag(TAG_SIF_SERVICE_NAME, applyCardinalityLimit("sif.paging.timeouts.total", TAG_SIF_SERVICE_NAME, sanitize(sifServiceName)))
                .register(meterRegistry)
                .increment();
    }

    /**
     * Replaces null tag values with "unknown".
     */
    private String sanitize(String value) {
        return value == null ? UNKNOWN : value;
    }

    /**
     * Applies cardinality limiting for a specific metric + tag key combination.
     * When the number of unique values for a tag exceeds the configured limit,
     * new values are replaced with "_other_".
     *
     * @param metricName the metric name
     * @param tagKey     the tag key
     * @param tagValue   the tag value
     * @return the original tag value, or "_other_" if cardinality limit exceeded
     */
    private String applyCardinalityLimit(String metricName, String tagKey, String tagValue) {
        String trackerKey = metricName + ":" + tagKey;
        ConcurrentHashMap<String, Boolean> seenValues =
                cardinalityTracker.computeIfAbsent(trackerKey, k -> new ConcurrentHashMap<>());

        if (seenValues.containsKey(tagValue)) {
            return tagValue;
        }

        if (seenValues.size() >= cardinalityLimit) {
            return OTHER;
        }

        seenValues.put(tagValue, Boolean.TRUE);
        return tagValue;
    }
}
