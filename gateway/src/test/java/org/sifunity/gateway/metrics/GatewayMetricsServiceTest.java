// SPDX-License-Identifier: MIT
package org.sifunity.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.AccessType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GatewayMetricsService.
 * 
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Authentication success/failure metrics are recorded</li>
 *   <li>Authorization success/failure metrics are recorded</li>
 *   <li>Rate limiting metrics are recorded</li>
 *   <li>Request count and duration metrics are recorded</li>
 * </ul>
 * 
 * <p>Requirements: 10.3, 10.4, 10.5, 10.6</p>
 */
class GatewayMetricsServiceTest {
    
    private MeterRegistry meterRegistry;
    private GatewayMetricsService metricsService;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new GatewayMetricsService(meterRegistry);
    }
    
    /**
     * Test that authentication success metric is recorded.
     * 
     * <p>Validates: Requirements 10.4</p>
     */
    @Test
    void shouldRecordAuthenticationSuccess() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        // Act
        metricsService.recordAuthenticationSuccess(environmentId);
        
        // Assert
        Counter counter = meterRegistry.find("gateway.authentication.success")
                .tag("environment_id", environmentId.toString())
                .counter();
        
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    /**
     * Test that authentication failure metric is recorded.
     * 
     * <p>Validates: Requirements 10.4</p>
     */
    @Test
    void shouldRecordAuthenticationFailure() {
        // Arrange
        String errorCode = "AUTH_001";
        
        // Act
        metricsService.recordAuthenticationFailure(errorCode);
        
        // Assert
        Counter counter = meterRegistry.find("gateway.authentication.failure")
                .tag("error_code", errorCode)
                .counter();
        
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    /**
     * Test that authorization success metric is recorded.
     * 
     * <p>Validates: Requirements 10.5</p>
     */
    @Test
    void shouldRecordAuthorizationSuccess() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String serviceName = "StudentPersonal";
        AccessType accessType = AccessType.QUERY;
        
        // Act
        metricsService.recordAuthorizationSuccess(environmentId, serviceName, accessType);
        
        // Assert
        Counter counter = meterRegistry.find("gateway.authorization.success")
                .tag("environment_id", environmentId.toString())
                .tag("service_name", serviceName)
                .tag("access_type", accessType.name())
                .counter();
        
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    /**
     * Test that authorization failure metric is recorded.
     * 
     * <p>Validates: Requirements 10.5</p>
     */
    @Test
    void shouldRecordAuthorizationFailure() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String serviceName = "StudentPersonal";
        AccessType accessType = AccessType.PROVIDE;
        
        // Act
        metricsService.recordAuthorizationFailure(environmentId, serviceName, accessType);
        
        // Assert
        Counter counter = meterRegistry.find("gateway.authorization.failure")
                .tag("environment_id", environmentId.toString())
                .tag("service_name", serviceName)
                .tag("access_type", accessType.name())
                .counter();
        
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    /**
     * Test that rate limit exceeded metric is recorded.
     * 
     * <p>Validates: Requirements 10.6</p>
     */
    @Test
    void shouldRecordRateLimitExceeded() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        // Act
        metricsService.recordRateLimitExceeded(environmentId);
        
        // Assert
        Counter counter = meterRegistry.find("gateway.ratelimit.exceeded")
                .tag("environment_id", environmentId.toString())
                .counter();
        
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    /**
     * Test that request count metric is recorded.
     * 
     * <p>Validates: Requirements 10.3</p>
     */
    @Test
    void shouldRecordRequest() {
        // Arrange
        String route = "/requests";
        String method = "GET";
        int statusCode = 200;
        
        // Act
        metricsService.recordRequest(route, method, statusCode);
        
        // Assert
        Counter counter = meterRegistry.find("gateway.requests")
                .tag("route", route)
                .tag("method", method)
                .tag("status", String.valueOf(statusCode))
                .counter();
        
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
    
    /**
     * Test that request duration metric is recorded.
     * 
     * <p>Validates: Requirements 10.3</p>
     */
    @Test
    void shouldRecordRequestDuration() {
        // Arrange
        String route = "/responses";
        String method = "POST";
        long durationMs = 150;
        
        // Act
        metricsService.recordRequestDuration(route, method, durationMs);
        
        // Assert
        Timer timer = meterRegistry.find("gateway.request.duration")
                .tag("route", route)
                .tag("method", method)
                .timer();
        
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isGreaterThanOrEqualTo(durationMs);
    }
    
    /**
     * Test that multiple metrics can be recorded independently.
     * 
     * <p>Validates: Requirements 10.3, 10.4, 10.5, 10.6</p>
     */
    @Test
    void shouldRecordMultipleMetricsIndependently() {
        // Arrange
        UUID env1 = UUID.randomUUID();
        UUID env2 = UUID.randomUUID();
        
        // Act
        metricsService.recordAuthenticationSuccess(env1);
        metricsService.recordAuthenticationSuccess(env1);
        metricsService.recordAuthenticationSuccess(env2);
        metricsService.recordAuthenticationFailure("AUTH_001");
        metricsService.recordRateLimitExceeded(env1);
        
        // Assert
        Counter authSuccessEnv1 = meterRegistry.find("gateway.authentication.success")
                .tag("environment_id", env1.toString())
                .counter();
        Counter authSuccessEnv2 = meterRegistry.find("gateway.authentication.success")
                .tag("environment_id", env2.toString())
                .counter();
        Counter authFailure = meterRegistry.find("gateway.authentication.failure")
                .tag("error_code", "AUTH_001")
                .counter();
        Counter rateLimitExceeded = meterRegistry.find("gateway.ratelimit.exceeded")
                .tag("environment_id", env1.toString())
                .counter();
        
        assertThat(authSuccessEnv1.count()).isEqualTo(2.0);
        assertThat(authSuccessEnv2.count()).isEqualTo(1.0);
        assertThat(authFailure.count()).isEqualTo(1.0);
        assertThat(rateLimitExceeded.count()).isEqualTo(1.0);
    }
}
