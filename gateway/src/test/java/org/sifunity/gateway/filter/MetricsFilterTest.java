// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.gateway.metrics.GatewayMetricsService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MetricsFilter.
 * 
 * <p>Tests verify:</p>
 * <ul>
 *   <li>Request count metrics are recorded</li>
 *   <li>Request duration metrics are recorded</li>
 *   <li>Route prefix is extracted correctly</li>
 *   <li>Metrics are recorded after request completion</li>
 * </ul>
 * 
 * <p>Requirements: 10.3</p>
 */
@ExtendWith(MockitoExtension.class)
class MetricsFilterTest {
    
    @Mock
    private GatewayMetricsService metricsService;
    
    @Mock
    private GatewayFilterChain filterChain;
    
    private MetricsFilter metricsFilter;
    
    @BeforeEach
    void setUp() {
        metricsFilter = new MetricsFilter(metricsService);
        // Don't stub filterChain here - stub it in each test as needed
    }
    
    /**
     * Test that request metrics are recorded for successful requests.
     * 
     * <p>Validates: Requirements 10.3</p>
     */
    @Test
    void shouldRecordMetricsForSuccessfulRequest() {
        // Arrange
        when(filterChain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());
        
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/requests/StudentPersonal")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        
        // Act
        StepVerifier.create(metricsFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // Assert
        verify(metricsService).recordRequest(eq("/requests"), eq("GET"), eq(200));
        verify(metricsService).recordRequestDuration(eq("/requests"), eq("GET"), anyLong());
    }
    
    /**
     * Test that request metrics are recorded for failed requests.
     * 
     * <p>Validates: Requirements 10.3</p>
     */
    @Test
    void shouldRecordMetricsForFailedRequest() {
        // Arrange
        when(filterChain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/responses/123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        
        // Act
        StepVerifier.create(metricsFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // Assert
        verify(metricsService).recordRequest(eq("/responses"), eq("POST"), eq(403));
        verify(metricsService).recordRequestDuration(eq("/responses"), eq("POST"), anyLong());
    }
    
    /**
     * Test that route prefix is extracted correctly.
     * 
     * <p>Validates: Requirements 10.3</p>
     */
    @Test
    void shouldExtractRoutePrefixCorrectly() {
        // Test various paths
        testRouteExtraction("/requests/StudentPersonal", "/requests");
        testRouteExtraction("/responses/123", "/responses");
        testRouteExtraction("/events", "/events");
        testRouteExtraction("/environments", "/environments");
        testRouteExtraction("/actuator/health", "/actuator");
        testRouteExtraction("/", "/");
    }
    
    private void testRouteExtraction(String path, String expectedRoute) {
        // Arrange
        when(filterChain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());
        
        MockServerHttpRequest request = MockServerHttpRequest
                .get(path)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        
        // Act
        StepVerifier.create(metricsFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // Assert
        ArgumentCaptor<String> routeCaptor = ArgumentCaptor.forClass(String.class);
        verify(metricsService, times(1)).recordRequest(routeCaptor.capture(), anyString(), anyInt());
        assertThat(routeCaptor.getValue()).isEqualTo(expectedRoute);
        
        // Reset mocks for next iteration
        reset(metricsService, filterChain);
    }
    
    /**
     * Test that request duration is measured accurately.
     * 
     * <p>Validates: Requirements 10.3</p>
     */
    @Test
    void shouldMeasureRequestDurationAccurately() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/requests/StudentPersonal")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        
        // Simulate delay in filter chain
        when(filterChain.filter(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.delay(java.time.Duration.ofMillis(100)).then());
        
        // Act
        StepVerifier.create(metricsFilter.filter(exchange, filterChain))
                .verifyComplete();
        
        // Assert (use timeout to handle reactive scheduling)
        ArgumentCaptor<Long> durationCaptor = ArgumentCaptor.forClass(Long.class);
        verify(metricsService, timeout(1000)).recordRequestDuration(anyString(), anyString(), durationCaptor.capture());
        
        // Duration should be at least 100ms (with some tolerance for execution time)
        assertThat(durationCaptor.getValue()).isGreaterThanOrEqualTo(90L);
    }
    
    /**
     * Test that metrics are recorded even when filter chain throws error.
     * 
     * <p>Validates: Requirements 10.3</p>
     */
    @Test
    void shouldRecordMetricsEvenWhenFilterChainThrowsError() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/requests/StudentPersonal")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        
        when(filterChain.filter(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.error(new RuntimeException("Test error")));
        
        // Act
        StepVerifier.create(metricsFilter.filter(exchange, filterChain))
                .expectError(RuntimeException.class)
                .verify();
        
        // Assert - metrics should still be recorded
        verify(metricsService).recordRequest(eq("/requests"), eq("GET"), anyInt());
        verify(metricsService).recordRequestDuration(eq("/requests"), eq("GET"), anyLong());
    }
    
    /**
     * Test that filter has correct order.
     * 
     * <p>Validates: Requirements 10.3</p>
     */
    @Test
    void shouldHaveCorrectOrder() {
        // Assert
        assertThat(metricsFilter.getOrder()).isEqualTo(100);
    }
}
