// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.sifunity.eventsconnector.dto.DeliveryStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Component for tracking custom metrics related to event distribution.
 * Provides counters, histograms, and timers for monitoring event processing.
 * 
 * Requirements: 14 (Observability and Logging)
 */
@Component
@Slf4j
public class EventMetrics {
    
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Counter> eventsReceivedCounters;
    private final ConcurrentHashMap<String, Counter> eventsDistributedCounters;
    private final DistributionSummary fanOutSizeHistogram;
    
    public EventMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.eventsReceivedCounters = new ConcurrentHashMap<>();
        this.eventsDistributedCounters = new ConcurrentHashMap<>();
        
        // Create fan-out size histogram
        this.fanOutSizeHistogram = DistributionSummary.builder("events.fanout.size")
                .description("Distribution of fan-out sizes (number of subscribers per event)")
                .baseUnit("subscribers")
                .register(meterRegistry);
        
        log.info("EventMetrics component initialized");
    }
    
    /**
     * Records an event received with service name and zone tags.
     * 
     * @param serviceName the service name
     * @param zoneId the zone ID
     */
    public void recordEventReceived(String serviceName, String zoneId) {
        String key = serviceName + ":" + zoneId;
        Counter counter = eventsReceivedCounters.computeIfAbsent(key, k ->
                Counter.builder("events.received.count")
                        .description("Total number of events received")
                        .tag("service", serviceName)
                        .tag("zone", zoneId)
                        .register(meterRegistry)
        );
        counter.increment();
        log.debug("Recorded event received: service={}, zone={}", serviceName, zoneId);
    }
    
    /**
     * Records an event distributed with delivery status.
     * 
     * @param status the delivery status (SUCCESS, PARTIAL, FAILED, NO_SUBSCRIBERS)
     * @param subscriberCount the number of subscribers
     */
    public void recordEventDistributed(DeliveryStatus status, int subscriberCount) {
        String statusKey = status.name();
        Counter counter = eventsDistributedCounters.computeIfAbsent(statusKey, k ->
                Counter.builder("events.distributed.count")
                        .description("Total number of events distributed")
                        .tag("status", status.name())
                        .register(meterRegistry)
        );
        counter.increment();
        
        // Record fan-out size
        fanOutSizeHistogram.record(subscriberCount);
        
        log.debug("Recorded event distributed: status={}, subscriberCount={}", status, subscriberCount);
    }
    
    /**
     * Records the latency of a subscription query.
     * 
     * @param duration the duration of the subscription query
     */
    public void recordSubscriptionQueryLatency(Duration duration) {
        Timer timer = Timer.builder("subscription.query.latency")
                .description("Latency of subscription service queries")
                .register(meterRegistry);
        timer.record(duration);
        log.debug("Recorded subscription query latency: {}ms", duration.toMillis());
    }
    
    /**
     * Records the latency of a queue enqueue operation.
     * 
     * @param duration the duration of the queue enqueue operation
     * @param queueId the queue ID
     * @param success whether the enqueue was successful
     */
    public void recordQueueEnqueueLatency(Duration duration, String queueId, boolean success) {
        Timer timer = Timer.builder("queue.enqueue.latency")
                .description("Latency of queue service enqueue operations")
                .tag("success", String.valueOf(success))
                .register(meterRegistry);
        timer.record(duration);
        log.debug("Recorded queue enqueue latency: queueId={}, duration={}ms, success={}", 
                queueId, duration.toMillis(), success);
    }
    
    /**
     * Records a counter for events with no subscribers.
     */
    public void recordNoSubscribers() {
        Counter counter = Counter.builder("events.no_subscribers.count")
                .description("Total number of events with no subscribers")
                .register(meterRegistry);
        counter.increment();
        log.debug("Recorded event with no subscribers");
    }
    
    /**
     * Records a counter for events with partial delivery.
     */
    public void recordPartialDelivery() {
        Counter counter = Counter.builder("events.partial_delivery.count")
                .description("Total number of events with partial delivery")
                .register(meterRegistry);
        counter.increment();
        log.debug("Recorded event with partial delivery");
    }
    
    /**
     * Records a counter for events that failed completely.
     */
    public void recordFailedDelivery() {
        Counter counter = Counter.builder("events.failed.count")
                .description("Total number of events that failed delivery")
                .register(meterRegistry);
        counter.increment();
        log.debug("Recorded event with failed delivery");
    }
}
