// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ResponseMetrics.
 * 
 * Requirements: 13.5 (Observability and Logging)
 */
class ResponseMetricsTest {
    
    private MeterRegistry meterRegistry;
    private ResponseMetrics responseMetrics;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        responseMetrics = new ResponseMetrics(meterRegistry);
    }
    
    @Test
    void shouldIncrementResponsesReceivedCounter() {
        // When
        responseMetrics.incrementResponsesReceived();
        
        // Then
        Counter counter = meterRegistry.find("responses.received.total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    void shouldIncrementResponsesRoutedCounter() {
        // When
        responseMetrics.incrementResponsesRouted();
        
        // Then
        Counter counter = meterRegistry.find("responses.routed.total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    void shouldIncrementResponsesFailedCounter() {
        // When
        responseMetrics.incrementResponsesFailed();
        
        // Then
        Counter counter = meterRegistry.find("responses.failed.total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    void shouldIncrementErrorResponsesCounter() {
        // When
        responseMetrics.incrementErrorResponses();
        
        // Then
        Counter counter = meterRegistry.find("responses.errors.total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    void shouldIncrementQueueNotFoundCounter() {
        // When
        responseMetrics.incrementQueueNotFound();
        
        // Then
        Counter counter = meterRegistry.find("errors.queue_not_found.total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    void shouldIncrementServiceUnavailableCounter() {
        // When
        responseMetrics.incrementServiceUnavailable();
        
        // Then
        Counter counter = meterRegistry.find("errors.service_unavailable.total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    void shouldIncrementValidationErrorsCounter() {
        // When
        responseMetrics.incrementValidationErrors();
        
        // Then
        Counter counter = meterRegistry.find("errors.validation.total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    @Test
    void shouldProvideResponseRoutingTimer() {
        // When
        Timer timer = responseMetrics.getResponseRoutingTimer();
        
        // Then
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getName()).isEqualTo("responses.routing.duration");
    }
    
    @Test
    void shouldProvideQueueServiceCallTimer() {
        // When
        Timer timer = responseMetrics.getQueueServiceCallTimer();
        
        // Then
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getName()).isEqualTo("queue.service.calls.duration");
    }
    
    @Test
    void shouldIncrementCountersMultipleTimes() {
        // When
        responseMetrics.incrementResponsesReceived();
        responseMetrics.incrementResponsesReceived();
        responseMetrics.incrementResponsesReceived();
        
        // Then
        Counter counter = meterRegistry.find("responses.received.total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(3.0);
    }
    
    @Test
    void shouldRecordTimerMeasurements() {
        // Given
        Timer timer = responseMetrics.getResponseRoutingTimer();
        
        // When
        timer.record(() -> {
            // Simulate some work
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // Then
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThan(0);
    }
    
    @Test
    void shouldHaveCorrectMetricDescriptions() {
        // When
        responseMetrics.incrementResponsesReceived();
        
        // Then
        Counter counter = meterRegistry.find("responses.received.total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.getId().getDescription()).isEqualTo("Total number of responses received");
    }
}
