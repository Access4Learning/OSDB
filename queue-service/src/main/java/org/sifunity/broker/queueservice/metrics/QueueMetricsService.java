// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for recording Queue-specific metrics.
 *
 * <p>This service provides methods to record metrics for queue operations
 * including enqueue/dequeue counters, dead-letter queue counters, queue depth
 * gauges, and delivery latency timers.</p>
 *
 * <p>Queue Metrics:</p>
 * <ul>
 *   <li>sif.queue.enqueued.total - Counter for enqueued messages</li>
 *   <li>sif.queue.dequeued.total - Counter for dequeued messages</li>
 *   <li>sif.queue.dlq.total - Counter for dead-letter queue messages</li>
 *   <li>sif.queue.depth - Gauge for current queue depth</li>
 *   <li>sif.queue.dlq.depth - Gauge for current DLQ depth</li>
 *   <li>sif.queue.delivery.latency - Timer for delivery latency</li>
 * </ul>
 *
 * <p>Null tag values are replaced with {@code "unknown"}.
 * Negative depth and latency values are clamped to 0.</p>
 *
 * <p>Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6</p>
 */
@Slf4j
@Service
public class QueueMetricsService {

    private static final String UNKNOWN = "unknown";

    private static final String TAG_QUEUE_NAME = "queue_name";
    private static final String TAG_MESSAGE_TYPE = "message_type";

    private final MeterRegistry meterRegistry;

    /**
     * Gauge suppliers for queue depth. Key: queue name, Value: AtomicLong holding current depth.
     * Gauge is registered once per queue name; subsequent calls update the AtomicLong value.
     */
    private final ConcurrentHashMap<String, AtomicLong> queueDepthGauges = new ConcurrentHashMap<>();

    /**
     * Gauge suppliers for DLQ depth. Key: queue name, Value: AtomicLong holding current DLQ depth.
     */
    private final ConcurrentHashMap<String, AtomicLong> dlqDepthGauges = new ConcurrentHashMap<>();

    /**
     * Constructs a QueueMetricsService with the specified meter registry.
     *
     * @param meterRegistry the Micrometer meter registry
     */
    public QueueMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records a message enqueue event.
     *
     * @param queueName   the name of the queue
     * @param messageType the type of message enqueued
     */
    public void recordEnqueue(String queueName, String messageType) {
        Counter.builder("sif.queue.enqueued.total")
                .description("Total messages enqueued")
                .tag(TAG_QUEUE_NAME, sanitize(queueName))
                .tag(TAG_MESSAGE_TYPE, sanitize(messageType))
                .register(meterRegistry)
                .increment();
    }

    /**
     * Records a message dequeue event.
     *
     * @param queueName   the name of the queue
     * @param messageType the type of message dequeued
     */
    public void recordDequeue(String queueName, String messageType) {
        Counter.builder("sif.queue.dequeued.total")
                .description("Total messages dequeued")
                .tag(TAG_QUEUE_NAME, sanitize(queueName))
                .tag(TAG_MESSAGE_TYPE, sanitize(messageType))
                .register(meterRegistry)
                .increment();
    }

    /**
     * Records a message moved to the dead-letter queue.
     *
     * @param queueName the name of the queue
     */
    public void recordDlqMessage(String queueName) {
        Counter.builder("sif.queue.dlq.total")
                .description("Total messages moved to dead-letter queue")
                .tag(TAG_QUEUE_NAME, sanitize(queueName))
                .register(meterRegistry)
                .increment();
    }

    /**
     * Updates the current queue depth gauge for a given queue.
     * Registers the gauge once per queue name; subsequent calls update the value.
     *
     * @param queueName the name of the queue
     * @param depth     the current queue depth (negative values clamped to 0)
     */
    public void updateQueueDepth(String queueName, long depth) {
        long safeDepth = Math.max(0, depth);
        String safeName = sanitize(queueName);

        AtomicLong gauge = queueDepthGauges.computeIfAbsent(safeName, name -> {
            AtomicLong atomicLong = new AtomicLong(0);
            Gauge.builder("sif.queue.depth", atomicLong, AtomicLong::doubleValue)
                    .description("Current pending message count")
                    .tag(TAG_QUEUE_NAME, name)
                    .register(meterRegistry);
            return atomicLong;
        });

        gauge.set(safeDepth);
    }

    /**
     * Updates the current dead-letter queue depth gauge for a given queue.
     * Registers the gauge once per queue name; subsequent calls update the value.
     *
     * @param queueName the name of the queue
     * @param depth     the current DLQ depth (negative values clamped to 0)
     */
    public void updateDlqDepth(String queueName, long depth) {
        long safeDepth = Math.max(0, depth);
        String safeName = sanitize(queueName);

        AtomicLong gauge = dlqDepthGauges.computeIfAbsent(safeName, name -> {
            AtomicLong atomicLong = new AtomicLong(0);
            Gauge.builder("sif.queue.dlq.depth", atomicLong, AtomicLong::doubleValue)
                    .description("Current dead-letter queue message count")
                    .tag(TAG_QUEUE_NAME, name)
                    .register(meterRegistry);
            return atomicLong;
        });

        gauge.set(safeDepth);
    }

    /**
     * Records the delivery latency for a message.
     *
     * @param queueName the name of the queue
     * @param latencyMs the delivery latency in milliseconds (negative values clamped to 0)
     */
    public void recordDeliveryLatency(String queueName, long latencyMs) {
        long safeLatency = Math.max(0, latencyMs);
        Timer.builder("sif.queue.delivery.latency")
                .description("Time between enqueue and delivery")
                .tag(TAG_QUEUE_NAME, sanitize(queueName))
                .register(meterRegistry)
                .record(safeLatency, TimeUnit.MILLISECONDS);
    }

    /**
     * Replaces null tag values with "unknown".
     */
    private String sanitize(String value) {
        return value == null ? UNKNOWN : value;
    }
}
