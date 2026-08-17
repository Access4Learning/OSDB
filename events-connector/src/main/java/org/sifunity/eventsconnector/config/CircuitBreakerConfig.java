// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Circuit breaker configuration for service calls.
 * Configures circuit breakers for Subscription Service and Queue Service to prevent cascading failures.
 * 
 * Requirements:
 * - 13: Retry Strategy for Service Calls - Circuit breaker pattern for resilience
 * - Resilience Requirements: Circuit breaker pattern for service calls to prevent cascading failures
 */
@Configuration
public class CircuitBreakerConfig {
    
    @Value("${resilience4j.circuitbreaker.instances.subscriptionService.failure-rate-threshold:50}")
    private float subscriptionServiceFailureRateThreshold;
    
    @Value("${resilience4j.circuitbreaker.instances.subscriptionService.wait-duration-in-open-state:10s}")
    private Duration subscriptionServiceWaitDurationInOpenState;
    
    @Value("${resilience4j.circuitbreaker.instances.subscriptionService.sliding-window-size:10}")
    private int subscriptionServiceSlidingWindowSize;
    
    @Value("${resilience4j.circuitbreaker.instances.subscriptionService.minimum-number-of-calls:5}")
    private int subscriptionServiceMinimumNumberOfCalls;
    
    @Value("${resilience4j.circuitbreaker.instances.subscriptionService.permitted-number-of-calls-in-half-open-state:3}")
    private int subscriptionServicePermittedNumberOfCallsInHalfOpenState;
    
    @Value("${resilience4j.circuitbreaker.instances.queueService.failure-rate-threshold:50}")
    private float queueServiceFailureRateThreshold;
    
    @Value("${resilience4j.circuitbreaker.instances.queueService.wait-duration-in-open-state:10s}")
    private Duration queueServiceWaitDurationInOpenState;
    
    @Value("${resilience4j.circuitbreaker.instances.queueService.sliding-window-size:10}")
    private int queueServiceSlidingWindowSize;
    
    @Value("${resilience4j.circuitbreaker.instances.queueService.minimum-number-of-calls:5}")
    private int queueServiceMinimumNumberOfCalls;
    
    @Value("${resilience4j.circuitbreaker.instances.queueService.permitted-number-of-calls-in-half-open-state:3}")
    private int queueServicePermittedNumberOfCallsInHalfOpenState;
    
    /**
     * Circuit breaker registry for managing circuit breaker instances.
     * 
     * @return CircuitBreakerRegistry configured with default settings
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }
    
    /**
     * Circuit breaker for Subscription Service calls.
     * Opens circuit when failure rate exceeds threshold to prevent cascading failures.
     * 
     * @param circuitBreakerRegistry Circuit breaker registry
     * @return CircuitBreaker instance for Subscription Service
     */
    @Bean(name = "subscriptionServiceCircuitBreaker")
    public CircuitBreaker subscriptionServiceCircuitBreaker(CircuitBreakerRegistry circuitBreakerRegistry) {
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config = io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            .failureRateThreshold(subscriptionServiceFailureRateThreshold)
            .waitDurationInOpenState(subscriptionServiceWaitDurationInOpenState)
            .slidingWindowSize(subscriptionServiceSlidingWindowSize)
            .slidingWindowType(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .minimumNumberOfCalls(subscriptionServiceMinimumNumberOfCalls)
            .permittedNumberOfCallsInHalfOpenState(subscriptionServicePermittedNumberOfCallsInHalfOpenState)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .ignoreExceptions(org.springframework.web.client.HttpClientErrorException.class)
            .build();
        
        return circuitBreakerRegistry.circuitBreaker("subscriptionService", config);
    }
    
    /**
     * Circuit breaker for Queue Service calls.
     * Opens circuit when failure rate exceeds threshold to prevent cascading failures.
     * 
     * @param circuitBreakerRegistry Circuit breaker registry
     * @return CircuitBreaker instance for Queue Service
     */
    @Bean(name = "queueServiceCircuitBreaker")
    public CircuitBreaker queueServiceCircuitBreaker(CircuitBreakerRegistry circuitBreakerRegistry) {
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config = io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            .failureRateThreshold(queueServiceFailureRateThreshold)
            .waitDurationInOpenState(queueServiceWaitDurationInOpenState)
            .slidingWindowSize(queueServiceSlidingWindowSize)
            .slidingWindowType(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .minimumNumberOfCalls(queueServiceMinimumNumberOfCalls)
            .permittedNumberOfCallsInHalfOpenState(queueServicePermittedNumberOfCallsInHalfOpenState)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .ignoreExceptions(org.springframework.web.client.HttpClientErrorException.class)
            .build();
        
        return circuitBreakerRegistry.circuitBreaker("queueService", config);
    }
}
