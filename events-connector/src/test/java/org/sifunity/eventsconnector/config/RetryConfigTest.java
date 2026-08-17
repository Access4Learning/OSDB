// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.config;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RetryConfig.
 * Verifies that retry policies are properly configured with exponential backoff.
 * 
 * Tests Requirements:
 * - 13: Retry Strategy for Service Calls - Exponential backoff with retries
 * - 13.1: Retry on Subscription Service transient errors (5xx)
 * - 13.2: Retry on Queue Service transient errors (5xx)
 * - 13.3: Exponential backoff
 * - 13.4: Configurable maximum attempts (default: 3)
 */
@SpringBootTest
@TestPropertySource(properties = {
    "retry.max-attempts=3",
    "retry.initial-backoff=100",
    "retry.max-backoff=1000"
})
class RetryConfigTest {
    
    @Autowired
    private RetryRegistry retryRegistry;
    
    @Autowired
    @Qualifier("subscriptionServiceRetry")
    private Retry subscriptionServiceRetry;
    
    @Autowired
    @Qualifier("queueServiceRetry")
    private Retry queueServiceRetry;
    
    @Test
    void retryRegistry_shouldBeConfigured() {
        // Given/When: RetryRegistry is injected
        
        // Then: Registry should not be null
        assertThat(retryRegistry).isNotNull();
    }
    
    @Test
    void subscriptionServiceRetry_shouldBeConfigured() {
        // Given/When: Retry instance is injected
        
        // Then: Retry should not be null
        assertThat(subscriptionServiceRetry).isNotNull();
        
        // And: Should have correct name
        assertThat(subscriptionServiceRetry.getName()).isEqualTo("subscriptionService");
        
        // And: Should have correct max attempts
        assertThat(subscriptionServiceRetry.getRetryConfig().getMaxAttempts()).isEqualTo(3);
    }
    
    @Test
    void queueServiceRetry_shouldBeConfigured() {
        // Given/When: Retry instance is injected
        
        // Then: Retry should not be null
        assertThat(queueServiceRetry).isNotNull();
        
        // And: Should have correct name
        assertThat(queueServiceRetry.getName()).isEqualTo("queueService");
        
        // And: Should have correct max attempts
        assertThat(queueServiceRetry.getRetryConfig().getMaxAttempts()).isEqualTo(3);
    }
    
    @Test
    void retryInstances_shouldBeDistinct() {
        // Given: Both retry instances
        
        // Then: Each retry should be a distinct instance
        assertThat(subscriptionServiceRetry)
            .isNotSameAs(queueServiceRetry);
    }
    
    @Test
    void subscriptionServiceRetry_shouldHaveExponentialBackoff() {
        // Given: Subscription Service retry configuration
        var config = subscriptionServiceRetry.getRetryConfig();
        
        // Then: Should have exponential backoff configured
        assertThat(config.getIntervalFunction()).isNotNull();
        
        // And: Initial wait duration should be 100ms
        assertThat(config.getIntervalFunction().apply(1)).isEqualTo(100);
        
        // And: Second attempt should have doubled backoff (200ms)
        assertThat(config.getIntervalFunction().apply(2)).isEqualTo(200);
        
        // And: Third attempt should have doubled backoff again (400ms)
        assertThat(config.getIntervalFunction().apply(3)).isEqualTo(400);
    }
    
    @Test
    void queueServiceRetry_shouldHaveExponentialBackoff() {
        // Given: Queue Service retry configuration
        var config = queueServiceRetry.getRetryConfig();
        
        // Then: Should have exponential backoff configured
        assertThat(config.getIntervalFunction()).isNotNull();
        
        // And: Initial wait duration should be 100ms
        assertThat(config.getIntervalFunction().apply(1)).isEqualTo(100);
        
        // And: Second attempt should have doubled backoff (200ms)
        assertThat(config.getIntervalFunction().apply(2)).isEqualTo(200);
        
        // And: Third attempt should have doubled backoff again (400ms)
        assertThat(config.getIntervalFunction().apply(3)).isEqualTo(400);
    }
}
