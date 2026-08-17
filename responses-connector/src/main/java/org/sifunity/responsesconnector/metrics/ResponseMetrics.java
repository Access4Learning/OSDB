// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Custom metrics for tracking response routing operations.
 * Provides counters and timers for monitoring response processing.
 * 
 * Requirements: 13.5 (Observability and Logging)
 */
@Component
@Slf4j
public class ResponseMetrics {
    
    private final Counter responsesReceivedCounter;
    private final Counter responsesRoutedCounter;
    private final Counter responsesFailedCounter;
    private final Counter errorResponsesCounter;
    private final Counter queueNotFoundCounter;
    private final Counter serviceUnavailableCounter;
    private final Counter validationErrorsCounter;
    
    private final Timer responseRoutingTimer;
    private final Timer queueServiceCallTimer;
    
    public ResponseMetrics(MeterRegistry meterRegistry) {
        // Response counters
        this.responsesReceivedCounter = Counter.builder("responses.received.total")
                .description("Total number of responses received")
                .register(meterRegistry);
        
        this.responsesRoutedCounter = Counter.builder("responses.routed.total")
                .description("Total number of responses successfully routed")
                .register(meterRegistry);
        
        this.responsesFailedCounter = Counter.builder("responses.failed.total")
                .description("Total number of failed routing attempts")
                .register(meterRegistry);
        
        this.errorResponsesCounter = Counter.builder("responses.errors.total")
                .description("Total number of error responses received")
                .register(meterRegistry);
        
        // Error counters
        this.queueNotFoundCounter = Counter.builder("errors.queue_not_found.total")
                .description("Total number of queue not found errors")
                .register(meterRegistry);
        
        this.serviceUnavailableCounter = Counter.builder("errors.service_unavailable.total")
                .description("Total number of service unavailable errors")
                .register(meterRegistry);
        
        this.validationErrorsCounter = Counter.builder("errors.validation.total")
                .description("Total number of validation errors")
                .register(meterRegistry);
        
        // Timers
        this.responseRoutingTimer = Timer.builder("responses.routing.duration")
                .description("Response routing latency")
                .register(meterRegistry);
        
        this.queueServiceCallTimer = Timer.builder("queue.service.calls.duration")
                .description("Queue Service call latency")
                .register(meterRegistry);
    }
    
    /**
     * Increment the counter for responses received.
     */
    public void incrementResponsesReceived() {
        responsesReceivedCounter.increment();
        log.debug("Incremented responses received counter");
    }
    
    /**
     * Increment the counter for responses successfully routed.
     */
    public void incrementResponsesRouted() {
        responsesRoutedCounter.increment();
        log.debug("Incremented responses routed counter");
    }
    
    /**
     * Increment the counter for failed routing attempts.
     */
    public void incrementResponsesFailed() {
        responsesFailedCounter.increment();
        log.debug("Incremented responses failed counter");
    }
    
    /**
     * Increment the counter for error responses.
     */
    public void incrementErrorResponses() {
        errorResponsesCounter.increment();
        log.debug("Incremented error responses counter");
    }
    
    /**
     * Increment the counter for queue not found errors.
     */
    public void incrementQueueNotFound() {
        queueNotFoundCounter.increment();
        log.debug("Incremented queue not found counter");
    }
    
    /**
     * Increment the counter for service unavailable errors.
     */
    public void incrementServiceUnavailable() {
        serviceUnavailableCounter.increment();
        log.debug("Incremented service unavailable counter");
    }
    
    /**
     * Increment the counter for validation errors.
     */
    public void incrementValidationErrors() {
        validationErrorsCounter.increment();
        log.debug("Incremented validation errors counter");
    }
    
    /**
     * Get the timer for response routing operations.
     * 
     * @return Timer for measuring response routing duration
     */
    public Timer getResponseRoutingTimer() {
        return responseRoutingTimer;
    }
    
    /**
     * Get the timer for Queue Service calls.
     * 
     * @return Timer for measuring Queue Service call duration
     */
    public Timer getQueueServiceCallTimer() {
        return queueServiceCallTimer;
    }
}
