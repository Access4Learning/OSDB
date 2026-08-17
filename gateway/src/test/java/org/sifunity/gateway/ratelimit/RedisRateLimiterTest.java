// SPDX-License-Identifier: MIT
package org.sifunity.gateway.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RedisRateLimiter.
 * 
 * <p>Tests the sliding window rate limiting algorithm with mocked Redis operations.</p>
 */
@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {
    
    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ReactiveZSetOperations<String, String> zSetOperations;
    
    private RedisRateLimiter rateLimiter;
    
    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        rateLimiter = new RedisRateLimiter(redisTemplate);
    }
    
    @Test
    void isAllowed_shouldAllowRequestWhenBelowLimit() {
        // Arrange
        String environmentId = "env-123";
        int requestsPerSecond = 10;
        
        // Mock Redis operations: no existing requests in window
        when(zSetOperations.removeRangeByScore(anyString(), any(Range.class)))
            .thenReturn(Mono.just(0L));
        when(zSetOperations.count(anyString(), any(Range.class)))
            .thenReturn(Mono.just(0L));
        when(zSetOperations.add(anyString(), anyString(), anyDouble()))
            .thenReturn(Mono.just(true));
        when(redisTemplate.expire(anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        
        // Act & Assert
        StepVerifier.create(rateLimiter.isAllowed(environmentId, requestsPerSecond))
            .assertNext(response -> {
                assertThat(response.isAllowed()).isTrue();
                assertThat(response.getRetryAfterSeconds()).isEqualTo(0);
            })
            .verifyComplete();
        
        // Verify Redis operations were called
        verify(zSetOperations).removeRangeByScore(eq("rate_limit:env-123"), any(Range.class));
        verify(zSetOperations).count(eq("rate_limit:env-123"), any(Range.class));
        verify(zSetOperations).add(eq("rate_limit:env-123"), anyString(), anyDouble());
        verify(redisTemplate).expire(eq("rate_limit:env-123"), any(Duration.class));
    }
    
    @Test
    void isAllowed_shouldAllowRequestWhenAtLimitMinusOne() {
        // Arrange
        String environmentId = "env-456";
        int requestsPerSecond = 10;
        
        // Mock Redis operations: 9 existing requests (below limit of 10)
        when(zSetOperations.removeRangeByScore(anyString(), any(Range.class)))
            .thenReturn(Mono.just(0L));
        when(zSetOperations.count(anyString(), any(Range.class)))
            .thenReturn(Mono.just(9L));
        when(zSetOperations.add(anyString(), anyString(), anyDouble()))
            .thenReturn(Mono.just(true));
        when(redisTemplate.expire(anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        
        // Act & Assert
        StepVerifier.create(rateLimiter.isAllowed(environmentId, requestsPerSecond))
            .assertNext(response -> {
                assertThat(response.isAllowed()).isTrue();
                assertThat(response.getRetryAfterSeconds()).isEqualTo(0);
            })
            .verifyComplete();
    }
    
    @Test
    void isAllowed_shouldDenyRequestWhenAtLimit() {
        // Arrange
        String environmentId = "env-789";
        int requestsPerSecond = 10;
        long now = System.currentTimeMillis();
        long oldestTimestamp = now - 500; // 500ms ago
        
        // Mock Redis operations: 10 existing requests (at limit)
        when(zSetOperations.removeRangeByScore(anyString(), any(Range.class)))
            .thenReturn(Mono.just(0L));
        when(zSetOperations.count(anyString(), any(Range.class)))
            .thenReturn(Mono.just(10L));
        when(zSetOperations.rangeByScore(anyString(), any(Range.class), any(Limit.class)))
            .thenReturn(Flux.just(String.valueOf(oldestTimestamp)));
        
        // Act & Assert
        StepVerifier.create(rateLimiter.isAllowed(environmentId, requestsPerSecond))
            .assertNext(response -> {
                assertThat(response.isAllowed()).isFalse();
                assertThat(response.getRetryAfterSeconds()).isGreaterThan(0);
                assertThat(response.getRetryAfterSeconds()).isLessThanOrEqualTo(1);
            })
            .verifyComplete();
        
        // Verify no add operation was called (request denied)
        verify(zSetOperations).rangeByScore(eq("rate_limit:env-789"), any(Range.class), any(Limit.class));
    }
    
    @Test
    void isAllowed_shouldDenyRequestWhenExceedingLimit() {
        // Arrange
        String environmentId = "env-999";
        int requestsPerSecond = 5;
        long now = System.currentTimeMillis();
        long oldestTimestamp = now - 800; // 800ms ago
        
        // Mock Redis operations: 15 existing requests (exceeding limit of 5)
        when(zSetOperations.removeRangeByScore(anyString(), any(Range.class)))
            .thenReturn(Mono.just(0L));
        when(zSetOperations.count(anyString(), any(Range.class)))
            .thenReturn(Mono.just(15L));
        when(zSetOperations.rangeByScore(anyString(), any(Range.class), any(Limit.class)))
            .thenReturn(Flux.just(String.valueOf(oldestTimestamp)));
        
        // Act & Assert
        StepVerifier.create(rateLimiter.isAllowed(environmentId, requestsPerSecond))
            .assertNext(response -> {
                assertThat(response.isAllowed()).isFalse();
                assertThat(response.getRetryAfterSeconds()).isGreaterThan(0);
            })
            .verifyComplete();
    }
    
    @Test
    void isAllowed_shouldRemoveOldEntriesFromWindow() {
        // Arrange
        String environmentId = "env-cleanup";
        int requestsPerSecond = 10;
        
        // Mock Redis operations: old entries removed
        when(zSetOperations.removeRangeByScore(anyString(), any(Range.class)))
            .thenReturn(Mono.just(5L)); // 5 old entries removed
        when(zSetOperations.count(anyString(), any(Range.class)))
            .thenReturn(Mono.just(3L)); // 3 entries remain in window
        when(zSetOperations.add(anyString(), anyString(), anyDouble()))
            .thenReturn(Mono.just(true));
        when(redisTemplate.expire(anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        
        // Act & Assert
        StepVerifier.create(rateLimiter.isAllowed(environmentId, requestsPerSecond))
            .assertNext(response -> {
                assertThat(response.isAllowed()).isTrue();
            })
            .verifyComplete();
        
        // Verify old entries were removed
        verify(zSetOperations).removeRangeByScore(eq("rate_limit:env-cleanup"), any(Range.class));
    }
    
    @Test
    void isAllowed_shouldSetExpirationOnKey() {
        // Arrange
        String environmentId = "env-expiry";
        int requestsPerSecond = 10;
        
        // Mock Redis operations
        when(zSetOperations.removeRangeByScore(anyString(), any(Range.class)))
            .thenReturn(Mono.just(0L));
        when(zSetOperations.count(anyString(), any(Range.class)))
            .thenReturn(Mono.just(0L));
        when(zSetOperations.add(anyString(), anyString(), anyDouble()))
            .thenReturn(Mono.just(true));
        when(redisTemplate.expire(anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        
        // Act
        StepVerifier.create(rateLimiter.isAllowed(environmentId, requestsPerSecond))
            .assertNext(response -> assertThat(response.isAllowed()).isTrue())
            .verifyComplete();
        
        // Assert: verify expiration is set (2 seconds: 1 second window + 1 second buffer)
        verify(redisTemplate).expire(eq("rate_limit:env-expiry"), eq(Duration.ofSeconds(2)));
    }
    
    @Test
    void isAllowed_shouldFailOpenOnRedisError() {
        // Arrange
        String environmentId = "env-error";
        int requestsPerSecond = 10;
        
        // Mock Redis operations to fail
        when(zSetOperations.removeRangeByScore(anyString(), any(Range.class)))
            .thenReturn(Mono.error(new RuntimeException("Redis connection failed")));
        
        // Act & Assert: should allow request on error (fail open)
        StepVerifier.create(rateLimiter.isAllowed(environmentId, requestsPerSecond))
            .assertNext(response -> {
                assertThat(response.isAllowed()).isTrue();
                assertThat(response.getRetryAfterSeconds()).isEqualTo(0);
            })
            .verifyComplete();
    }
    
    @Test
    void isAllowed_shouldCalculateRetryAfterCorrectly() {
        // Arrange
        String environmentId = "env-retry";
        int requestsPerSecond = 10;
        long now = System.currentTimeMillis();
        long oldestTimestamp = now - 300; // 300ms ago, so retry after ~700ms (1 second)
        
        // Mock Redis operations
        when(zSetOperations.removeRangeByScore(anyString(), any(Range.class)))
            .thenReturn(Mono.just(0L));
        when(zSetOperations.count(anyString(), any(Range.class)))
            .thenReturn(Mono.just(10L));
        when(zSetOperations.rangeByScore(anyString(), any(Range.class), any(Limit.class)))
            .thenReturn(Flux.just(String.valueOf(oldestTimestamp)));
        
        // Act & Assert
        StepVerifier.create(rateLimiter.isAllowed(environmentId, requestsPerSecond))
            .assertNext(response -> {
                assertThat(response.isAllowed()).isFalse();
                // Should be 1 second (rounded up from ~700ms)
                assertThat(response.getRetryAfterSeconds()).isEqualTo(1);
            })
            .verifyComplete();
    }
    
    @Test
    void isAllowed_shouldUseCorrectRedisKey() {
        // Arrange
        String environmentId = "test-env-123";
        int requestsPerSecond = 10;
        String expectedKey = "rate_limit:test-env-123";
        
        // Mock Redis operations
        when(zSetOperations.removeRangeByScore(anyString(), any(Range.class)))
            .thenReturn(Mono.just(0L));
        when(zSetOperations.count(anyString(), any(Range.class)))
            .thenReturn(Mono.just(0L));
        when(zSetOperations.add(anyString(), anyString(), anyDouble()))
            .thenReturn(Mono.just(true));
        when(redisTemplate.expire(anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        
        // Act
        StepVerifier.create(rateLimiter.isAllowed(environmentId, requestsPerSecond))
            .assertNext(response -> assertThat(response.isAllowed()).isTrue())
            .verifyComplete();
        
        // Assert: verify correct key is used
        verify(zSetOperations).removeRangeByScore(eq(expectedKey), any(Range.class));
        verify(zSetOperations).count(eq(expectedKey), any(Range.class));
        verify(zSetOperations).add(eq(expectedKey), anyString(), anyDouble());
        verify(redisTemplate).expire(eq(expectedKey), any(Duration.class));
    }

    @Test
    void isAllowed_withDomainSolution_shouldUseScopedKey() {
        // Arrange
        String domainId = "iowa";
        String solutionId = "production";
        String environmentId = "env-abc-123";
        int requestsPerSecond = 10;
        String expectedKey = "domain:iowa:solution:production:function:rate_limit:env:env-abc-123";
        
        // Mock Redis operations
        when(zSetOperations.removeRangeByScore(anyString(), any(Range.class)))
            .thenReturn(Mono.just(0L));
        when(zSetOperations.count(anyString(), any(Range.class)))
            .thenReturn(Mono.just(0L));
        when(zSetOperations.add(anyString(), anyString(), anyDouble()))
            .thenReturn(Mono.just(true));
        when(redisTemplate.expire(anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        
        // Act
        StepVerifier.create(rateLimiter.isAllowed(domainId, solutionId, environmentId, requestsPerSecond))
            .assertNext(response -> assertThat(response.isAllowed()).isTrue())
            .verifyComplete();
        
        // Assert: verify domain/solution-scoped key is used
        verify(zSetOperations).removeRangeByScore(eq(expectedKey), any(Range.class));
        verify(zSetOperations).count(eq(expectedKey), any(Range.class));
        verify(zSetOperations).add(eq(expectedKey), anyString(), anyDouble());
        verify(redisTemplate).expire(eq(expectedKey), any(Duration.class));
    }

    @Test
    void isAllowed_withDomainSolution_shouldDenyWhenAtLimit() {
        // Arrange
        String domainId = "state-a-doe";
        String solutionId = "staging";
        String environmentId = "env-xyz";
        int requestsPerSecond = 5;
        long now = System.currentTimeMillis();
        long oldestTimestamp = now - 500;
        String expectedKey = "domain:state-a-doe:solution:staging:function:rate_limit:env:env-xyz";
        
        // Mock Redis operations: at limit
        when(zSetOperations.removeRangeByScore(anyString(), any(Range.class)))
            .thenReturn(Mono.just(0L));
        when(zSetOperations.count(anyString(), any(Range.class)))
            .thenReturn(Mono.just(5L));
        when(zSetOperations.rangeByScore(anyString(), any(Range.class), any(Limit.class)))
            .thenReturn(Flux.just(String.valueOf(oldestTimestamp)));
        
        // Act & Assert
        StepVerifier.create(rateLimiter.isAllowed(domainId, solutionId, environmentId, requestsPerSecond))
            .assertNext(response -> {
                assertThat(response.isAllowed()).isFalse();
                assertThat(response.getRetryAfterSeconds()).isGreaterThan(0);
            })
            .verifyComplete();
        
        // Verify correct key is used
        verify(zSetOperations).rangeByScore(eq(expectedKey), any(Range.class), any(Limit.class));
    }

    @Test
    void isAllowed_legacyOverload_shouldUseLegacyKeyFormat() {
        // Arrange
        String environmentId = "legacy-env-456";
        int requestsPerSecond = 10;
        String expectedKey = "rate_limit:legacy-env-456";
        
        // Mock Redis operations
        when(zSetOperations.removeRangeByScore(anyString(), any(Range.class)))
            .thenReturn(Mono.just(0L));
        when(zSetOperations.count(anyString(), any(Range.class)))
            .thenReturn(Mono.just(0L));
        when(zSetOperations.add(anyString(), anyString(), anyDouble()))
            .thenReturn(Mono.just(true));
        when(redisTemplate.expire(anyString(), any(Duration.class)))
            .thenReturn(Mono.just(true));
        
        // Act
        StepVerifier.create(rateLimiter.isAllowed(environmentId, requestsPerSecond))
            .assertNext(response -> assertThat(response.isAllowed()).isTrue())
            .verifyComplete();
        
        // Assert: verify legacy key format is used
        verify(zSetOperations).removeRangeByScore(eq(expectedKey), any(Range.class));
        verify(zSetOperations).count(eq(expectedKey), any(Range.class));
        verify(zSetOperations).add(eq(expectedKey), anyString(), anyDouble());
    }
    
    @Test
    void isAllowed_shouldHandleEmptyOldestEntryGracefully() {
        // Arrange
        String environmentId = "env-no-oldest";
        int requestsPerSecond = 10;
        
        // Mock Redis operations: at limit but no oldest entry found
        when(zSetOperations.removeRangeByScore(anyString(), any(Range.class)))
            .thenReturn(Mono.just(0L));
        when(zSetOperations.count(anyString(), any(Range.class)))
            .thenReturn(Mono.just(10L));
        when(zSetOperations.rangeByScore(anyString(), any(Range.class), any(Limit.class)))
            .thenReturn(Flux.empty()); // No oldest entry
        
        // Act & Assert: should default to 1 second retry
        StepVerifier.create(rateLimiter.isAllowed(environmentId, requestsPerSecond))
            .assertNext(response -> {
                assertThat(response.isAllowed()).isFalse();
                assertThat(response.getRetryAfterSeconds()).isEqualTo(1);
            })
            .verifyComplete();
    }
}
