// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.config;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.Duration;

/**
 * Retry configuration for service calls with exponential backoff.
 * Configures retry policies for Subscription Service and Queue Service calls.
 * 
 * Requirements:
 * - 13: Retry Strategy for Service Calls - Exponential backoff with retries
 * - 13.1: Retry on Subscription Service transient errors (5xx)
 * - 13.2: Retry on Queue Service transient errors (5xx)
 * - 13.3: Exponential backoff (e.g., 100ms, 200ms, 400ms)
 * - 13.4: Configurable maximum attempts (default: 3)
 * - 13.6: No retry for client errors (4xx)
 */
@Configuration
public class RetryConfig {
    
    @Value("${retry.max-attempts:3}")
    private int maxAttempts;
    
    @Value("${retry.initial-backoff:100}")
    private long initialBackoffMillis;
    
    @Value("${retry.max-backoff:1000}")
    private long maxBackoffMillis;
    
    /**
     * Retry registry for managing retry instances.
     * 
     * @return RetryRegistry configured with default retry settings
     */
    @Bean
    public RetryRegistry retryRegistry() {
        return RetryRegistry.ofDefaults();
    }
    
    /**
     * Retry configuration for Subscription Service calls.
     * Retries on 5xx errors with exponential backoff, does not retry on 4xx errors.
     * 
     * @return Retry instance for Subscription Service
     */
    @Bean(name = "subscriptionServiceRetry")
    public Retry subscriptionServiceRetry(RetryRegistry retryRegistry) {
        io.github.resilience4j.retry.RetryConfig config = io.github.resilience4j.retry.RetryConfig.custom()
            .maxAttempts(maxAttempts)
            .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(
                initialBackoffMillis, 
                2.0,
                maxBackoffMillis
            ))
            .retryOnException(this::isRetryableException)
            .build();
        
        return retryRegistry.retry("subscriptionService", config);
    }
    
    /**
     * Retry configuration for Queue Service calls.
     * Retries on 5xx errors with exponential backoff, does not retry on 4xx errors.
     * 
     * @return Retry instance for Queue Service
     */
    @Bean(name = "queueServiceRetry")
    public Retry queueServiceRetry(RetryRegistry retryRegistry) {
        io.github.resilience4j.retry.RetryConfig config = io.github.resilience4j.retry.RetryConfig.custom()
            .maxAttempts(maxAttempts)
            .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(
                initialBackoffMillis,
                2.0,
                maxBackoffMillis
            ))
            .retryOnException(this::isRetryableException)
            .build();
        
        return retryRegistry.retry("queueService", config);
    }
    
    /**
     * Determines if an exception is retryable.
     * Only retries on 5xx server errors, not on 4xx client errors.
     * 
     * @param throwable The exception to check
     * @return true if the exception is retryable, false otherwise
     */
    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof HttpClientErrorException) {
            return false; // 4xx — don't retry
        }
        if (throwable instanceof HttpServerErrorException) {
            return true;  // 5xx — retry
        }
        // Retry on other exceptions (network errors, timeouts, etc.)
        return true;
    }
}
