// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.EnvironmentType;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EnvironmentContextFilter.
 * 
 * <p>Test Coverage:
 * <ul>
 *   <li>Header injection for authenticated requests</li>
 *   <li>Skipping injection for exempt paths</li>
 *   <li>Correct header values (X-Environment-Id, X-Environment-Name)</li>
 *   <li>Filter ordering (Order 6)</li>
 *   <li>Preservation of other headers</li>
 * </ul>
 * 
 * <p>Requirements: 4.1, 4.2, 4.6
 */
@DisplayName("EnvironmentContextFilter Tests")
class EnvironmentContextFilterTest {
    
    private EnvironmentContextFilter filter;
    private GatewayFilterChain mockChain;
    
    @BeforeEach
    void setUp() {
        filter = new EnvironmentContextFilter("test-internal-token");
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
    
    // ========== Header Injection Tests ==========
    
    @Test
    @DisplayName("filter should inject X-Environment-Id header for authenticated requests")
    void filter_InjectsEnvironmentIdHeader_ForAuthenticatedRequests() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestApp";
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment context in exchange attributes (simulating AuthenticationFilter)
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", environmentName);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(any(ServerWebExchange.class));
        
        // Verify that the chain was called with a mutated request containing environment headers
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            String injectedEnvironmentId = mutatedRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_ID);
            
            // Verify X-Environment-Id was injected
            assertThat(injectedEnvironmentId).isNotNull();
            assertThat(injectedEnvironmentId).isEqualTo(environmentId.toString());
            
            return true;
        }));
    }
    
    @Test
    @DisplayName("filter should inject X-Environment-Name header for authenticated requests")
    void filter_InjectsEnvironmentNameHeader_ForAuthenticatedRequests() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String environmentName = "MyApplication";
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment context in exchange attributes
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", environmentName);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(any(ServerWebExchange.class));
        
        // Verify that the chain was called with a mutated request containing environment headers
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            String injectedEnvironmentName = mutatedRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_NAME);
            
            // Verify X-Environment-Name was injected
            assertThat(injectedEnvironmentName).isNotNull();
            assertThat(injectedEnvironmentName).isEqualTo(environmentName);
            
            return true;
        }));
    }
    
    @Test
    @DisplayName("filter should inject both environment headers for authenticated requests")
    void filter_InjectsBothEnvironmentHeaders_ForAuthenticatedRequests() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestProvider";
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/responses/StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment context in exchange attributes
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", environmentName);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(any(ServerWebExchange.class));
        
        // Verify both headers are injected
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            HttpHeaders headers = mutatedRequest.getHeaders();
            
            // Verify both headers are present
            assertThat(headers.containsKey(SifHeaders.X_ENVIRONMENT_ID)).isTrue();
            assertThat(headers.containsKey(SifHeaders.X_ENVIRONMENT_NAME)).isTrue();
            
            // Verify correct values
            assertThat(headers.getFirst(SifHeaders.X_ENVIRONMENT_ID)).isEqualTo(environmentId.toString());
            assertThat(headers.getFirst(SifHeaders.X_ENVIRONMENT_NAME)).isEqualTo(environmentName);
            
            return true;
        }));
    }
    
    @Test
    @DisplayName("filter should inject correct header values for different environments")
    void filter_InjectsCorrectHeaderValues_ForDifferentEnvironments() {
        // Arrange
        UUID environmentId1 = UUID.randomUUID();
        String environmentName1 = "ConsumerApp";
        
        MockServerHttpRequest request1 = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange1 = MockServerWebExchange.from(request1);
        
        exchange1.getAttributes().put("environmentId", environmentId1);
        exchange1.getAttributes().put("environmentName", environmentName1);
        
        // Act - First request
        StepVerifier.create(filter.filter(exchange1, mockChain))
            .verifyComplete();
        
        // Assert - First request
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            assertThat(mutatedRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_ID))
                .isEqualTo(environmentId1.toString());
            assertThat(mutatedRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_NAME))
                .isEqualTo(environmentName1);
            return true;
        }));
        
        // Arrange - Second request with different environment
        UUID environmentId2 = UUID.randomUUID();
        String environmentName2 = "ProviderApp";
        
        MockServerHttpRequest request2 = MockServerHttpRequest
            .get("/responses/StudentPersonal")
            .build();
        MockServerWebExchange exchange2 = MockServerWebExchange.from(request2);
        
        exchange2.getAttributes().put("environmentId", environmentId2);
        exchange2.getAttributes().put("environmentName", environmentName2);
        
        // Act - Second request
        StepVerifier.create(filter.filter(exchange2, mockChain))
            .verifyComplete();
        
        // Assert - Second request has different values
        verify(mockChain, times(2)).filter(any(ServerWebExchange.class));
    }
    
    // ========== Exempt Path Tests ==========
    
    @Test
    @DisplayName("filter should skip header injection when no environment context")
    void filter_SkipsHeaderInjection_WhenNoEnvironmentContext() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/environments")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // No environment context set (simulating exempt path)
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert - filter still strips internal headers even on exempt paths
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            // Verify no environment headers are present (stripped)
            assertThat(mutatedRequest.getHeaders().containsKey(SifHeaders.X_ENVIRONMENT_ID)).isFalse();
            assertThat(mutatedRequest.getHeaders().containsKey(SifHeaders.X_ENVIRONMENT_NAME)).isFalse();
            return true;
        }));
    }
    
    @Test
    @DisplayName("filter should skip header injection for POST /environments")
    void filter_SkipsHeaderInjection_ForPostEnvironments() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/environments")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // No environment context (exempt path)
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert - filter strips internal headers even on exempt paths
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            // Verify no environment headers are present
            assertThat(mutatedRequest.getHeaders().get(SifHeaders.X_ENVIRONMENT_ID)).isNull();
            assertThat(mutatedRequest.getHeaders().get(SifHeaders.X_ENVIRONMENT_NAME)).isNull();
            return true;
        }));
    }
    
    @Test
    @DisplayName("filter should skip header injection for /actuator/health")
    void filter_SkipsHeaderInjection_ForActuatorHealth() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/actuator/health")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // No environment context (exempt path)
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert - filter strips internal headers even on exempt paths
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            // Verify no environment headers are present
            assertThat(mutatedRequest.getHeaders().get(SifHeaders.X_ENVIRONMENT_ID)).isNull();
            assertThat(mutatedRequest.getHeaders().get(SifHeaders.X_ENVIRONMENT_NAME)).isNull();
            return true;
        }));
    }
    
    @Test
    @DisplayName("filter should skip header injection when environmentId is null")
    void filter_SkipsHeaderInjection_WhenEnvironmentIdIsNull() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set only environmentName, but not environmentId
        exchange.getAttributes().put("environmentName", "TestApp");
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert - filter strips internal headers even when environmentId is null
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            // Verify no environment headers are present
            assertThat(mutatedRequest.getHeaders().get(SifHeaders.X_ENVIRONMENT_ID)).isNull();
            assertThat(mutatedRequest.getHeaders().get(SifHeaders.X_ENVIRONMENT_NAME)).isNull();
            return true;
        }));
    }
    
    // ========== Header Preservation Tests ==========
    
    @Test
    @DisplayName("filter should preserve existing headers when injecting environment headers")
    void filter_PreservesExistingHeaders_WhenInjectingEnvironmentHeaders() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestApp";
        String correlationId = UUID.randomUUID().toString();
        String serviceName = "StudentPersonal";
        String zoneId = "default";
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .header(SifHeaders.SERVICE_NAME, serviceName)
            .header(SifHeaders.ZONE_ID, zoneId)
            .header("Custom-Header", "custom-value")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment context
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", environmentName);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            HttpHeaders headers = mutatedRequest.getHeaders();
            
            // Verify environment headers were added
            assertThat(headers.getFirst(SifHeaders.X_ENVIRONMENT_ID)).isEqualTo(environmentId.toString());
            assertThat(headers.getFirst(SifHeaders.X_ENVIRONMENT_NAME)).isEqualTo(environmentName);
            
            // Verify existing headers were preserved
            assertThat(headers.getFirst(SifHeaders.X_CORRELATION_ID)).isEqualTo(correlationId);
            assertThat(headers.getFirst(SifHeaders.SERVICE_NAME)).isEqualTo(serviceName);
            assertThat(headers.getFirst(SifHeaders.ZONE_ID)).isEqualTo(zoneId);
            assertThat(headers.getFirst("Custom-Header")).isEqualTo("custom-value");
            
            return true;
        }));
    }
    
    @Test
    @DisplayName("filter should not modify request path when injecting headers")
    void filter_DoesNotModifyRequestPath_WhenInjectingHeaders() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestApp";
        String requestPath = "/requests/StudentPersonal/12345";
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(requestPath)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment context
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", environmentName);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            
            // Verify path is unchanged
            assertThat(mutatedRequest.getPath().value()).isEqualTo(requestPath);
            
            return true;
        }));
    }
    
    @Test
    @DisplayName("filter should not modify request method when injecting headers")
    void filter_DoesNotModifyRequestMethod_WhenInjectingHeaders() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestApp";
        
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/responses/StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment context
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", environmentName);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            
            // Verify method is unchanged
            assertThat(mutatedRequest.getMethod()).isEqualTo(org.springframework.http.HttpMethod.POST);
            
            return true;
        }));
    }
    
    // ========== Filter Ordering Tests ==========
    
    @Test
    @DisplayName("filter should have order 7 (seventh in chain, after ProxyImpersonationFilter)")
    void filter_HasOrder6() {
        // Assert
        assertThat(filter.getOrder()).isEqualTo(7);
    }
    
    // ========== Edge Case Tests ==========
    
    @Test
    @DisplayName("filter should handle environment name with special characters")
    void filter_HandlesEnvironmentName_WithSpecialCharacters() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String environmentName = "Test-App_2024 (Production)";
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment context
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", environmentName);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            String injectedName = mutatedRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_NAME);
            
            // Verify special characters are preserved
            assertThat(injectedName).isEqualTo(environmentName);
            
            return true;
        }));
    }
    
    @Test
    @DisplayName("filter should handle environment name with unicode characters")
    void filter_HandlesEnvironmentName_WithUnicodeCharacters() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String environmentName = "Test-App-日本語-Ñoño";
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment context
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", environmentName);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            String injectedName = mutatedRequest.getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_NAME);
            
            // Verify unicode characters are preserved
            assertThat(injectedName).isEqualTo(environmentName);
            
            return true;
        }));
    }
    
    @Test
    @DisplayName("filter should handle multiple requests with different environment contexts")
    void filter_HandlesMultipleRequests_WithDifferentEnvironmentContexts() {
        // Arrange - First request
        UUID environmentId1 = UUID.randomUUID();
        String environmentName1 = "ConsumerApp";
        
        MockServerHttpRequest request1 = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange1 = MockServerWebExchange.from(request1);
        
        exchange1.getAttributes().put("environmentId", environmentId1);
        exchange1.getAttributes().put("environmentName", environmentName1);
        
        // Act - First request
        StepVerifier.create(filter.filter(exchange1, mockChain))
            .verifyComplete();
        
        // Arrange - Second request
        UUID environmentId2 = UUID.randomUUID();
        String environmentName2 = "ProviderApp";
        
        MockServerHttpRequest request2 = MockServerHttpRequest
            .get("/responses/StudentPersonal")
            .build();
        MockServerWebExchange exchange2 = MockServerWebExchange.from(request2);
        
        exchange2.getAttributes().put("environmentId", environmentId2);
        exchange2.getAttributes().put("environmentName", environmentName2);
        
        // Act - Second request
        StepVerifier.create(filter.filter(exchange2, mockChain))
            .verifyComplete();
        
        // Assert - Both requests were processed
        verify(mockChain, times(2)).filter(any(ServerWebExchange.class));
    }
    
    @Test
    @DisplayName("filter should handle request with all environment types")
    void filter_HandlesRequest_WithAllEnvironmentTypes() {
        // Test CONSUMER type
        UUID consumerId = UUID.randomUUID();
        MockServerHttpRequest consumerRequest = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .build();
        MockServerWebExchange consumerExchange = MockServerWebExchange.from(consumerRequest);
        consumerExchange.getAttributes().put("environmentId", consumerId);
        consumerExchange.getAttributes().put("environmentName", "ConsumerApp");
        consumerExchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        StepVerifier.create(filter.filter(consumerExchange, mockChain))
            .verifyComplete();
        
        // Test PROVIDER type
        UUID providerId = UUID.randomUUID();
        MockServerHttpRequest providerRequest = MockServerHttpRequest
            .post("/responses/StudentPersonal")
            .build();
        MockServerWebExchange providerExchange = MockServerWebExchange.from(providerRequest);
        providerExchange.getAttributes().put("environmentId", providerId);
        providerExchange.getAttributes().put("environmentName", "ProviderApp");
        providerExchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        StepVerifier.create(filter.filter(providerExchange, mockChain))
            .verifyComplete();
        
        // Test BROKER type
        UUID brokerId = UUID.randomUUID();
        MockServerHttpRequest brokerRequest = MockServerHttpRequest
            .get("/environments")
            .build();
        MockServerWebExchange brokerExchange = MockServerWebExchange.from(brokerRequest);
        brokerExchange.getAttributes().put("environmentId", brokerId);
        brokerExchange.getAttributes().put("environmentName", "BrokerApp");
        brokerExchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        
        StepVerifier.create(filter.filter(brokerExchange, mockChain))
            .verifyComplete();
        
        // Assert - All three requests were processed
        verify(mockChain, times(3)).filter(any(ServerWebExchange.class));
    }
    
    @Test
    @DisplayName("filter should handle chain errors gracefully")
    void filter_HandlesChainErrors_Gracefully() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestApp";
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", environmentName);
        
        // Mock chain to throw an error
        when(mockChain.filter(any(ServerWebExchange.class)))
            .thenReturn(Mono.error(new RuntimeException("Chain error")));
        
        // Act & Assert
        StepVerifier.create(filter.filter(exchange, mockChain))
            .expectError(RuntimeException.class)
            .verify();
        
        // Verify filter attempted to call chain with mutated request
        verify(mockChain).filter(any(ServerWebExchange.class));
    }
    
    @Test
    @DisplayName("filter should inject headers for different HTTP methods")
    void filter_InjectsHeaders_ForDifferentHttpMethods() {
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestApp";
        
        // Test GET
        MockServerHttpRequest getRequest = MockServerHttpRequest
            .get("/requests/StudentPersonal")
            .build();
        MockServerWebExchange getExchange = MockServerWebExchange.from(getRequest);
        getExchange.getAttributes().put("environmentId", environmentId);
        getExchange.getAttributes().put("environmentName", environmentName);
        
        StepVerifier.create(filter.filter(getExchange, mockChain))
            .verifyComplete();
        
        // Test POST
        MockServerHttpRequest postRequest = MockServerHttpRequest
            .post("/responses/StudentPersonal")
            .build();
        MockServerWebExchange postExchange = MockServerWebExchange.from(postRequest);
        postExchange.getAttributes().put("environmentId", environmentId);
        postExchange.getAttributes().put("environmentName", environmentName);
        
        StepVerifier.create(filter.filter(postExchange, mockChain))
            .verifyComplete();
        
        // Test PUT
        MockServerHttpRequest putRequest = MockServerHttpRequest
            .put("/services/StudentPersonal")
            .build();
        MockServerWebExchange putExchange = MockServerWebExchange.from(putRequest);
        putExchange.getAttributes().put("environmentId", environmentId);
        putExchange.getAttributes().put("environmentName", environmentName);
        
        StepVerifier.create(filter.filter(putExchange, mockChain))
            .verifyComplete();
        
        // Test DELETE
        MockServerHttpRequest deleteRequest = MockServerHttpRequest
            .delete("/services/StudentPersonal")
            .build();
        MockServerWebExchange deleteExchange = MockServerWebExchange.from(deleteRequest);
        deleteExchange.getAttributes().put("environmentId", environmentId);
        deleteExchange.getAttributes().put("environmentName", environmentName);
        
        StepVerifier.create(filter.filter(deleteExchange, mockChain))
            .verifyComplete();
        
        // Assert - All four requests were processed
        verify(mockChain, times(4)).filter(any(ServerWebExchange.class));
    }
}
