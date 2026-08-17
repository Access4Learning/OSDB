// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CircuitBreakerConfig.
 * Verifies that circuit breakers are properly configured to prevent cascading failures.
 * 
 * Tests Requirements:
 * - 13: Retry Strategy for Service Calls - Circuit breaker pattern for resilience
 * - Resilience Requirements: Circuit breaker pattern for service calls
 */
@SpringBootTest
@TestPropertySource(properties = {
    "resilience4j.circuitbreaker.instances.subscriptionService.failure-rate-threshold=50",
    "resilience4j.circuitbreaker.instances.subscriptionService.wait-duration-in-open-state=10s",
    "resilience4j.circuitbreaker.instances.subscriptionService.sliding-window-size=10",
    "resilience4j.circuitbreaker.instances.subscriptionService.minimum-number-of-calls=5",
    "resilience4j.circuitbreaker.instances.subscriptionService.permitted-number-of-calls-in-half-open-state=3",
    "resilience4j.circuitbreaker.instances.queueService.failure-rate-threshold=50",
    "resilience4j.circuitbreaker.instances.queueService.wait-duration-in-open-state=10s",
    "resilience4j.circuitbreaker.instances.queueService.sliding-window-size=10",
    "resilience4j.circuitbreaker.instances.queueService.minimum-number-of-calls=5",
    "resilience4j.circuitbreaker.instances.queueService.permitted-number-of-calls-in-half-open-state=3"
})
class CircuitBreakerConfigTest {
    
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    
    @Autowired
    @Qualifier("subscriptionServiceCircuitBreaker")
    private CircuitBreaker subscriptionServiceCircuitBreaker;
    
    @Autowired
    @Qualifier("queueServiceCircuitBreaker")
    private CircuitBreaker queueServiceCircuitBreaker;
    
    @Test
    void circuitBreakerRegistry_shouldBeConfigured() {
        // Given/When: CircuitBreakerRegistry is injected
        
        // Then: Registry should not be null
        assertThat(circuitBreakerRegistry).isNotNull();
    }
    
    @Test
    void subscriptionServiceCircuitBreaker_shouldBeConfigured() {
        // Given/When: CircuitBreaker instance is injected
        
        // Then: CircuitBreaker should not be null
        assertThat(subscriptionServiceCircuitBreaker).isNotNull();
        
        // And: Should have correct name
        assertThat(subscriptionServiceCircuitBreaker.getName()).isEqualTo("subscriptionService");
        
        // And: Should be in CLOSED state initially
        assertThat(subscriptionServiceCircuitBreaker.getState())
            .isEqualTo(CircuitBreaker.State.CLOSED);
    }
    
    @Test
    void queueServiceCircuitBreaker_shouldBeConfigured() {
        // Given/When: CircuitBreaker instance is injected
        
        // Then: CircuitBreaker should not be null
        assertThat(queueServiceCircuitBreaker).isNotNull();
        
        // And: Should have correct name
        assertThat(queueServiceCircuitBreaker.getName()).isEqualTo("queueService");
        
        // And: Should be in CLOSED state initially
        assertThat(queueServiceCircuitBreaker.getState())
            .isEqualTo(CircuitBreaker.State.CLOSED);
    }
    
    @Test
    void circuitBreakerInstances_shouldBeDistinct() {
        // Given: Both circuit breaker instances
        
        // Then: Each circuit breaker should be a distinct instance
        assertThat(subscriptionServiceCircuitBreaker)
            .isNotSameAs(queueServiceCircuitBreaker);
    }
    
    @Test
    void subscriptionServiceCircuitBreaker_shouldHaveCorrectConfiguration() {
        // Given: Subscription Service circuit breaker configuration
        var config = subscriptionServiceCircuitBreaker.getCircuitBreakerConfig();
        
        // Then: Should have correct failure rate threshold (50%)
        assertThat(config.getFailureRateThreshold()).isEqualTo(50.0f);
        
        // And: Should have correct wait duration in open state (10 seconds)
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(10000);
        
        // And: Should have correct sliding window size (10)
        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        
        // And: Should have correct minimum number of calls (5)
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
        
        // And: Should have correct permitted calls in half-open state (3)
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        
        // And: Should have automatic transition enabled
        assertThat(config.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isTrue();
    }
    
    @Test
    void queueServiceCircuitBreaker_shouldHaveCorrectConfiguration() {
        // Given: Queue Service circuit breaker configuration
        var config = queueServiceCircuitBreaker.getCircuitBreakerConfig();
        
        // Then: Should have correct failure rate threshold (50%)
        assertThat(config.getFailureRateThreshold()).isEqualTo(50.0f);
        
        // And: Should have correct wait duration in open state (10 seconds)
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(10000);
        
        // And: Should have correct sliding window size (10)
        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        
        // And: Should have correct minimum number of calls (5)
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
        
        // And: Should have correct permitted calls in half-open state (3)
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
        
        // And: Should have automatic transition enabled
        assertThat(config.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isTrue();
    }
}
