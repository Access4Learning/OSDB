// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Service for creating and managing custom business metrics.
 * 
 * <p>This service provides APIs for:
 * <ul>
 *   <li>Incrementing counters for events (queue messages, cache operations, errors)</li>
 *   <li>Recording gauge values (queue depth, connection pools)</li>
 *   <li>Timing operations with histograms</li>
 * </ul>
 * 
 * <p>All metrics are automatically tagged with service name and environment
 * via the common tags configuration in {@link org.sifunity.broker.common.config.ObservabilityConfig}.
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Increment a counter
 * metricsService.incrementQueueMessageProcessed("immediate-requests", "success");
 * 
 * // Record a gauge value
 * metricsService.recordQueueDepth("immediate-requests", 42);
 * 
 * // Time an operation
 * String result = metricsService.timeOperation(
 *     "http.provider.call",
 *     Map.of("provider", "sis-provider"),
 *     () -> callProvider()
 * );
 * }</pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    prefix = "observability.metrics",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class BusinessMetricsService {
    
    private final MeterRegistry registry;
    
    // Cache for gauge suppliers to prevent recreation
    private final Map<String, Object> gaugeSuppliers = new ConcurrentHashMap<>();
    
    /**
     * Increments a counter for processed queue messages.
     * 
     * @param queueName the name of the queue
     * @param status the processing status (success, error, retry)
     */
    public void incrementQueueMessageProcessed(String queueName, String status) {
        Counter.builder("queue.messages.processed")
            .tag("queue", queueName)
            .tag("status", status)
            .description("Number of queue messages processed")
            .register(registry)
            .increment();
    }
    
    /**
     * Increments a counter for cache operations.
     * 
     * @param cacheName the name of the cache
     * @param operation the operation type (get, put, evict)
     * @param result the operation result (hit, miss, success, error)
     */
    public void incrementCacheOperation(String cacheName, String operation, String result) {
        Counter.builder("cache.operations")
            .tag("cache", cacheName)
            .tag("operation", operation)
            .tag("result", result)
            .description("Cache operation results")
            .register(registry)
            .increment();
    }
    
    /**
     * Increments an error counter.
     * 
     * @param errorType the type of error (validation, database, network, etc.)
     * @param serviceName the name of the service where the error occurred
     */
    public void incrementError(String errorType, String serviceName) {
        Counter.builder("errors.total")
            .tag("error_type", errorType)
            .tag("service", serviceName)
            .description("Total number of errors by type")
            .register(registry)
            .increment();
    }
    
    /**
     * Records the current depth of a queue as a gauge.
     * 
     * @param queueName the name of the queue
     * @param depth the current queue depth
     */
    public void recordQueueDepth(String queueName, int depth) {
        String key = "queue.depth." + queueName;
        gaugeSuppliers.put(key, depth);
        
        Gauge.builder("queue.depth", () -> (Integer) gaugeSuppliers.getOrDefault(key, 0))
            .tag("queue", queueName)
            .description("Current queue depth")
            .register(registry);
    }
    
    /**
     * Records database connection pool metrics as gauges.
     * 
     * @param poolName the name of the connection pool
     * @param active the number of active connections
     * @param idle the number of idle connections
     * @param total the total number of connections
     */
    public void recordDatabaseConnectionPool(String poolName, int active, int idle, int total) {
        String activeKey = "db.pool.active." + poolName;
        String idleKey = "db.pool.idle." + poolName;
        String totalKey = "db.pool.total." + poolName;
        
        gaugeSuppliers.put(activeKey, active);
        gaugeSuppliers.put(idleKey, idle);
        gaugeSuppliers.put(totalKey, total);
        
        Gauge.builder("db.pool.active", () -> (Integer) gaugeSuppliers.getOrDefault(activeKey, 0))
            .tag("pool", poolName)
            .description("Number of active database connections")
            .register(registry);
        
        Gauge.builder("db.pool.idle", () -> (Integer) gaugeSuppliers.getOrDefault(idleKey, 0))
            .tag("pool", poolName)
            .description("Number of idle database connections")
            .register(registry);
        
        Gauge.builder("db.pool.total", () -> (Integer) gaugeSuppliers.getOrDefault(totalKey, 0))
            .tag("pool", poolName)
            .description("Total number of database connections")
            .register(registry);
    }
    
    /**
     * Times an operation and records the duration in a histogram.
     * 
     * @param operationName the name of the operation
     * @param tags additional tags for the metric
     * @param operation the operation to time
     * @param <T> the return type of the operation
     * @return the result of the operation
     */
    public <T> T timeOperation(String operationName, 
                              Map<String, String> tags,
                              Supplier<T> operation) {
        Timer.Sample sample = Timer.start(registry);
        try {
            return operation.get();
        } finally {
            Timer.Builder builder = Timer.builder(operationName)
                .description("Operation execution time");
            
            if (tags != null) {
                tags.forEach(builder::tag);
            }
            
            sample.stop(builder.register(registry));
        }
    }
    
    /**
     * Times an operation and records the duration (no return value).
     * 
     * @param operationName the name of the operation
     * @param tags additional tags for the metric
     * @param operation the operation to time
     */
    public void timeOperation(String operationName, 
                             Map<String, String> tags,
                             Runnable operation) {
        timeOperation(operationName, tags, () -> {
            operation.run();
            return null;
        });
    }
    
    /**
     * Increments a counter for SIF message processing.
     * 
     * @param messageType the SIF message type
     * @param zone the zone ID
     * @param status the processing status
     */
    public void incrementSifMessageProcessed(String messageType, String zone, String status) {
        Counter.builder("sif.messages.processed")
            .tag("message_type", messageType)
            .tag("zone", zone)
            .tag("status", status)
            .description("Number of SIF messages processed")
            .register(registry)
            .increment();
    }
}
