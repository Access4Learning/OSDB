// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.util.LoggingUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CorrelationIdFilter.
 * 
 * <p>Test Coverage:
 * <ul>
 *   <li>Correlation ID generation when absent</li>
 *   <li>Correlation ID preservation when present</li>
 *   <li>MDC population with correlation ID</li>
 *   <li>MDC cleanup after request processing</li>
 *   <li>Filter ordering (Order 1)</li>
 * </ul>
 * 
 * <p>Requirements: 4.3, 4.4, 9.1, 9.2
 */
@DisplayName("CorrelationIdFilter Tests")
class CorrelationIdFilterTest {
    
    private CorrelationIdFilter filter;
    private GatewayFilterChain mockChain;
    
    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        mockChain = mock(GatewayFilterChain.class);
        
        // Mock chain to return completed Mono
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenReturn(Mono.empty());
        
        // Clear MDC before each test
        LoggingUtils.clearContext();
    }
    
    @AfterEach
    void tearDown() {
        // Clear MDC after each test to prevent context leakage
        LoggingUtils.clearContext();
    }
    
    // ========== Correlation ID Generation Tests ==========
    
    @Test
    @DisplayName("filter should generate new correlation ID when header is absent")
    void filter_GeneratesCorrelationId_WhenAbsent() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/test")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(any(ServerWebExchange.class));
        
        // Verify that the chain was called with a mutated request containing correlation ID
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            String correlationId = mutatedRequest.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
            
            // Verify correlation ID was added
            assertThat(correlationId).isNotNull();
            assertThat(correlationId).isNotBlank();
            
            // Verify it's a valid UUID
            try {
                UUID.fromString(correlationId);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }));
    }
    
    @Test
    @DisplayName("filter should generate new correlation ID when header is blank")
    void filter_GeneratesCorrelationId_WhenBlank() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/test")
            .header(SifHeaders.X_CORRELATION_ID, "")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(any(ServerWebExchange.class));
        
        // Verify that a new correlation ID was generated
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            String correlationId = mutatedRequest.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
            
            // Verify correlation ID was added
            assertThat(correlationId).isNotNull();
            assertThat(correlationId).isNotBlank();
            
            // Verify it's a valid UUID
            try {
                UUID.fromString(correlationId);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }));
    }
    
    @Test
    @DisplayName("filter should generate new correlation ID when header is whitespace")
    void filter_GeneratesCorrelationId_WhenWhitespace() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/test")
            .header(SifHeaders.X_CORRELATION_ID, "   ")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(any(ServerWebExchange.class));
        
        // Verify that a new correlation ID was generated
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            String correlationId = mutatedRequest.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
            
            // Verify correlation ID was added
            assertThat(correlationId).isNotNull();
            assertThat(correlationId).isNotBlank();
            
            // Verify it's a valid UUID
            try {
                UUID.fromString(correlationId);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }));
    }
    
    // ========== Correlation ID Preservation Tests ==========
    
    @Test
    @DisplayName("filter should preserve existing correlation ID when present")
    void filter_PreservesCorrelationId_WhenPresent() {
        // Arrange
        String existingCorrelationId = UUID.randomUUID().toString();
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/test")
            .header(SifHeaders.X_CORRELATION_ID, existingCorrelationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(any(ServerWebExchange.class));
        
        // Verify that the existing correlation ID was preserved
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            String correlationId = mutatedRequest.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
            
            // Verify correlation ID matches the original
            assertThat(correlationId).isEqualTo(existingCorrelationId);
            return true;
        }));
    }
    
    @Test
    @DisplayName("filter should preserve non-UUID correlation ID when present")
    void filter_PreservesNonUuidCorrelationId_WhenPresent() {
        // Arrange
        String existingCorrelationId = "custom-correlation-id-12345";
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/test")
            .header(SifHeaders.X_CORRELATION_ID, existingCorrelationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(any(ServerWebExchange.class));
        
        // Verify that the existing correlation ID was preserved (even if not a UUID)
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            String correlationId = mutatedRequest.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
            
            // Verify correlation ID matches the original
            assertThat(correlationId).isEqualTo(existingCorrelationId);
            return true;
        }));
    }
    
    // ========== MDC Population Tests ==========
    
    @Test
    @DisplayName("filter should populate MDC with correlation ID")
    void filter_PopulatesMdc_WithCorrelationId() {
        // Arrange
        String existingCorrelationId = UUID.randomUUID().toString();
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/test")
            .header(SifHeaders.X_CORRELATION_ID, existingCorrelationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Mock chain to verify MDC during execution
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenAnswer(invocation -> {
                // Verify MDC is populated during filter chain execution
                String mdcCorrelationId = LoggingUtils.getCorrelationId();
                assertThat(mdcCorrelationId).isEqualTo(existingCorrelationId);
                return Mono.empty();
            });
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(any(ServerWebExchange.class));
    }
    
    @Test
    @DisplayName("filter should populate MDC with generated correlation ID")
    void filter_PopulatesMdc_WithGeneratedCorrelationId() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/test")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Mock chain to verify MDC during execution
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenAnswer(invocation -> {
                // Verify MDC is populated during filter chain execution
                String mdcCorrelationId = LoggingUtils.getCorrelationId();
                assertThat(mdcCorrelationId).isNotNull();
                assertThat(mdcCorrelationId).isNotBlank();
                
                // Verify it's a valid UUID
                try {
                    UUID.fromString(mdcCorrelationId);
                } catch (IllegalArgumentException e) {
                    throw new AssertionError("MDC correlation ID is not a valid UUID: " + mdcCorrelationId);
                }
                
                return Mono.empty();
            });
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(any(ServerWebExchange.class));
    }
    
    @Test
    @DisplayName("filter should clear MDC after request processing")
    void filter_ClearsMdc_AfterRequestProcessing() {
        // Arrange
        String existingCorrelationId = UUID.randomUUID().toString();
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/test")
            .header(SifHeaders.X_CORRELATION_ID, existingCorrelationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert - MDC should be cleared after request processing
        String mdcCorrelationId = LoggingUtils.getCorrelationId();
        assertThat(mdcCorrelationId).isNull();
    }
    
    @Test
    @DisplayName("filter should clear MDC even when chain throws error")
    void filter_ClearsMdc_WhenChainThrowsError() {
        // Arrange
        String existingCorrelationId = UUID.randomUUID().toString();
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/test")
            .header(SifHeaders.X_CORRELATION_ID, existingCorrelationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Mock chain to throw an error
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenReturn(Mono.error(new RuntimeException("Test error")));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectError(RuntimeException.class)
            .verify();
        
        // Assert - MDC should be cleared even after error
        String mdcCorrelationId = LoggingUtils.getCorrelationId();
        assertThat(mdcCorrelationId).isNull();
    }
    
    // ========== Filter Ordering Tests ==========
    
    @Test
    @DisplayName("filter should have order 1 (first in chain)")
    void filter_HasOrder1() {
        // Assert
        assertThat(filter.getOrder()).isEqualTo(1);
    }
    
    // ========== Header Injection Tests ==========
    
    @Test
    @DisplayName("filter should inject correlation ID into request headers for backend services")
    void filter_InjectsCorrelationId_IntoRequestHeaders() {
        // Arrange
        String existingCorrelationId = UUID.randomUUID().toString();
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/test")
            .header(SifHeaders.X_CORRELATION_ID, existingCorrelationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            HttpHeaders headers = mutatedRequest.getHeaders();
            
            // Verify correlation ID is in headers
            assertThat(headers.containsKey(SifHeaders.X_CORRELATION_ID)).isTrue();
            assertThat(headers.getFirst(SifHeaders.X_CORRELATION_ID)).isEqualTo(existingCorrelationId);
            
            return true;
        }));
    }
    
    @Test
    @DisplayName("filter should not modify other headers when injecting correlation ID")
    void filter_DoesNotModifyOtherHeaders_WhenInjectingCorrelationId() {
        // Arrange
        String existingCorrelationId = UUID.randomUUID().toString();
        String customHeaderValue = "custom-value";
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/test")
            .header(SifHeaders.X_CORRELATION_ID, existingCorrelationId)
            .header("Custom-Header", customHeaderValue)
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            HttpHeaders headers = mutatedRequest.getHeaders();
            
            // Verify other headers are preserved
            assertThat(headers.getFirst("Custom-Header")).isEqualTo(customHeaderValue);
            assertThat(headers.getFirst(SifHeaders.SERVICE_NAME)).isEqualTo("StudentPersonal");
            
            return true;
        }));
    }
    
    // ========== Edge Case Tests ==========
    
    @Test
    @DisplayName("filter should handle multiple requests with different correlation IDs")
    void filter_HandlesMultipleRequests_WithDifferentCorrelationIds() {
        // Arrange
        String correlationId1 = UUID.randomUUID().toString();
        String correlationId2 = UUID.randomUUID().toString();
        
        MockServerHttpRequest request1 = MockServerHttpRequest
            .get("/test1")
            .header(SifHeaders.X_CORRELATION_ID, correlationId1)
            .build();
        MockServerWebExchange exchange1 = MockServerWebExchange.from(request1);
        
        MockServerHttpRequest request2 = MockServerHttpRequest
            .get("/test2")
            .header(SifHeaders.X_CORRELATION_ID, correlationId2)
            .build();
        MockServerWebExchange exchange2 = MockServerWebExchange.from(request2);
        
        // Act & Assert - First request
        StepVerifier.create(filter.filter(exchange1, mockChain))
            .verifyComplete();
        
        verify(mockChain).filter(argThat(mutatedExchange -> {
            String correlationId = mutatedExchange.getRequest().getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
            assertThat(correlationId).isEqualTo(correlationId1);
            return true;
        }));
        
        // MDC should be cleared after first request
        assertThat(LoggingUtils.getCorrelationId()).isNull();
        
        // Act & Assert - Second request
        StepVerifier.create(filter.filter(exchange2, mockChain))
            .verifyComplete();
        
        verify(mockChain, times(2)).filter(argThat(mutatedExchange -> {
            String correlationId = mutatedExchange.getRequest().getHeaders().getFirst(SifHeaders.X_CORRELATION_ID);
            // This will match both calls, so we just verify it's not null
            assertThat(correlationId).isNotNull();
            return true;
        }));
        
        // MDC should be cleared after second request
        assertThat(LoggingUtils.getCorrelationId()).isNull();
    }
}
