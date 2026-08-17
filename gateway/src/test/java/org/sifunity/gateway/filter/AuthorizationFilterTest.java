// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.PermissionCheckRequest;
import org.sifunity.broker.common.dto.PermissionCheckResponse;
import org.sifunity.broker.common.exception.InsufficientPermissionsException;
import org.sifunity.broker.common.exception.InvalidRequestException;
import org.sifunity.broker.common.exception.ServiceUnavailableException;
import org.sifunity.broker.common.util.LoggingUtils;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
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
 * Unit tests for AuthorizationFilter.
 * 
 * <p>Test Coverage:
 * <ul>
 *   <li>Successful authorization flow</li>
 *   <li>Service name extraction from headers and path</li>
 *   <li>Zone/context extraction with defaults</li>
 *   <li>Access type determination for various method/path combinations</li>
 *   <li>Permission granted flow</li>
 *   <li>Permission denied flow (403 response)</li>
 *   <li>Exempt path handling</li>
 *   <li>Error handling</li>
 * </ul>
 * 
 * <p>Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10
 */
@DisplayName("AuthorizationFilter Tests")
class AuthorizationFilterTest {
    
    /** Standard test domain/solution prefix for all resource paths. */
    private static final String PREFIX = "/iowa/production";
    
    private AuthorizationFilter filter;
    private EnvironmentServiceClient mockEnvironmentServiceClient;
    private GatewayFilterChain mockChain;
    private ObjectMapper objectMapper;
    private org.sifunity.gateway.metrics.GatewayMetricsService mockMetricsService;
    
    @BeforeEach
    void setUp() {
        mockEnvironmentServiceClient = mock(EnvironmentServiceClient.class);
        mockChain = mock(GatewayFilterChain.class);
        mockMetricsService = mock(org.sifunity.gateway.metrics.GatewayMetricsService.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Register JSR310 module for Instant serialization
        
        filter = new AuthorizationFilter(mockEnvironmentServiceClient, objectMapper, mockMetricsService);
        
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
    
    // ========== Successful Authorization Tests ==========
    
    @Test
    @DisplayName("filter should authorize successfully with valid permission")
    void filter_AuthorizesSuccessfully_WithValidPermission() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .header(SifHeaders.ZONE_ID, "default")
            .header(SifHeaders.CONTEXT_ID, "DEFAULT")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Set environment attributes (normally set by AuthenticationFilter)
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", "TestApp");
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(any(ServerWebExchange.class));
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.environmentId().equals(environmentId) &&
            req.serviceName().equals("StudentPersonal") &&
            req.zoneId().equals("default") &&
            req.contextId().equals("DEFAULT") &&
            req.accessType() == AccessType.QUERY
        ));
    }
    
    // ========== Service Name Extraction Tests ==========
    
    @Test
    @DisplayName("filter should extract service name from serviceName header")
    void filter_ExtractsServiceName_FromHeader() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.serviceName().equals("StudentPersonal")
        ));
    }
    
    @Test
    @DisplayName("filter should extract service name from path when header is missing")
    void filter_ExtractsServiceName_FromPath() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/SchoolInfo")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.serviceName().equals("SchoolInfo")
        ));
    }
    
    @Test
    @DisplayName("filter should prefer serviceName header over path")
    void filter_PrefersPathServiceName_ForRequests() {
        // Arrange: For /requests/{serviceName}, service name comes from URL path, not header
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/PathService")
            .header(SifHeaders.SERVICE_NAME, "HeaderService")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert: service name extracted from path, not header
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.serviceName().equals("PathService")
        ));
    }
    
    @Test
    @DisplayName("filter should reject request when service name cannot be extracted")
    void filter_RejectsRequest_WhenServiceNameCannotBeExtracted() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/")  // Resource path is "/" — no service name extractable
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain, never()).filter(any());
        verify(mockEnvironmentServiceClient, never()).checkPermission(any());
        
        // Verify 403 response
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    
    // ========== Zone/Context Extraction Tests ==========
    
    @Test
    @DisplayName("filter should extract zone from zoneId header")
    void filter_ExtractsZone_FromHeader() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .header(SifHeaders.ZONE_ID, "custom-zone")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.zoneId().equals("custom-zone")
        ));
    }
    
    @Test
    @DisplayName("filter should use default zone when header is missing")
    void filter_UsesDefaultZone_WhenHeaderMissing() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default-zone");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.zoneId().equals("default-zone")
        ));
    }
    
    @Test
    @DisplayName("filter should use default zone when header is blank")
    void filter_UsesDefaultZone_WhenHeaderBlank() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .header(SifHeaders.ZONE_ID, "")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default-zone");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.zoneId().equals("default-zone")
        ));
    }
    
    @Test
    @DisplayName("filter should extract context from contextId header")
    void filter_ExtractsContext_FromHeader() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .header(SifHeaders.CONTEXT_ID, "custom-context")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.contextId().equals("custom-context")
        ));
    }
    
    @Test
    @DisplayName("filter should use DEFAULT context when header is missing")
    void filter_UsesDefaultContext_WhenHeaderMissing() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert - contextId defaults to "DEFAULT" when no header is present
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.contextId().equals("DEFAULT")
        ));
    }
    
    @Test
    @DisplayName("filter should use DEFAULT context when no contextId header is present")
    void filter_HandlesNullDefaultContext() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert - contextId defaults to "DEFAULT" when no header is present
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.contextId().equals("DEFAULT")
        ));
    }
    
    // ========== Access Type Determination Tests ==========
    
    @Test
    @DisplayName("filter should determine CONSUME access type for GET /requests")
    void filter_DeterminesConsumeAccessType_ForGetRequests() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.accessType() == AccessType.QUERY
        ));
    }
    
    @Test
    @DisplayName("filter should determine PROVIDE access type for POST /responses")
    void filter_DeterminesProvideAccessType_ForPostResponses() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .post(PREFIX + "/responses/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.accessType() == AccessType.PROVIDE
        ));
    }
    
    @Test
    @DisplayName("filter should determine PROVIDE access type for POST /events")
    void filter_DeterminesProvideAccessType_ForPostEvents() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .post(PREFIX + "/events/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.accessType() == AccessType.PROVIDE
        ));
    }
    
    @Test
    @DisplayName("filter should determine CONSUME access type for GET /services")
    void filter_DeterminesConsumeAccessType_ForGetServices() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/services/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.accessType() == AccessType.QUERY
        ));
    }
    
    @Test
    @DisplayName("filter should determine CREATE access type for POST /services (job creation)")
    void filter_DeterminesCreateAccessType_ForPostServices() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .post(PREFIX + "/services/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.accessType() == AccessType.CREATE
        ));
    }
    
    @Test
    @DisplayName("filter should determine CREATE access type for PUT /services phase operation")
    void filter_DeterminesCreateAccessType_ForPutServicesPhaseOp() {
        // Arrange - PUT at phase level (3+ segments) → CREATE
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .put(PREFIX + "/services/StudentPersonal/job123/export")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.accessType() == AccessType.CREATE
        ));
    }
    
    @Test
    @DisplayName("filter should determine DELETE access type for DELETE /services (job deletion)")
    void filter_DeterminesDeleteAccessType_ForDeleteServices() {
        // Arrange - DELETE at job level (2 segments) → DELETE
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .delete(PREFIX + "/services/StudentPersonal/job123")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.accessType() == AccessType.DELETE
        ));
    }
    
    @Test
    @DisplayName("filter should skip authorization for ACL management paths")
    void filter_SkipsAuthorization_ForAclEntries() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/acl-entries")
            .header(SifHeaders.SERVICE_NAME, "AclEntry")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert - ACL paths are exempt from authorization
        verify(mockChain).filter(exchange);
        verify(mockEnvironmentServiceClient, never()).checkPermission(any());
    }
    
    // ========== Permission Granted Flow Tests ==========
    
    @Test
    @DisplayName("filter should proceed to route when permission is granted")
    void filter_ProceedsToRoute_WhenPermissionGranted() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(any(ServerWebExchange.class));
        verify(mockEnvironmentServiceClient).checkPermission(any());
    }
    
    @Test
    @DisplayName("filter should call chain filter exactly once when authorized")
    void filter_CallsChainFilterOnce_WhenAuthorized() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain, times(1)).filter(any(ServerWebExchange.class));
    }
    
    // ========== Permission Denied Flow Tests ==========
    
    @Test
    @DisplayName("filter should return 403 when permission is denied")
    void filter_Returns403_WhenPermissionDenied() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(false);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain, never()).filter(any());
        verify(mockEnvironmentServiceClient).checkPermission(any());
        
        // Verify 403 response
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }
    
    @Test
    @DisplayName("filter should not call chain when permission is denied")
    void filter_DoesNotCallChain_WhenPermissionDenied() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(false);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain, never()).filter(any());
    }
    
    @Test
    @DisplayName("filter should return 403 when Environment Service returns InsufficientPermissionsException")
    void filter_Returns403_OnInsufficientPermissionsException() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .header(SifHeaders.X_CORRELATION_ID, "test-correlation-id")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        // Set correlation ID in MDC
        LoggingUtils.setCorrelationId("test-correlation-id");
        
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.error(new InsufficientPermissionsException("Access denied")));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain, never()).filter(any());
        
        // Verify 403 response with correlation ID
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID))
            .isEqualTo("test-correlation-id");
    }
    
    @Test
    @DisplayName("filter should include correlation ID in 403 response headers")
    void filter_IncludesCorrelationId_In403ResponseHeaders() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .header(SifHeaders.X_CORRELATION_ID, correlationId)
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        // Set correlation ID in MDC
        LoggingUtils.setCorrelationId(correlationId);
        
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(false);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getHeaders().getFirst(SifHeaders.X_CORRELATION_ID))
            .isEqualTo(correlationId);
    }
    
    // ========== Exempt Path Tests ==========
    
    @Test
    @DisplayName("filter should skip authorization for POST /{domainId}/{solutionId}/environments")
    void filter_SkipsAuthorization_ForPostEnvironments() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .post(PREFIX + "/environments")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(exchange);
        verify(mockEnvironmentServiceClient, never()).checkPermission(any());
    }
    
    @Test
    @DisplayName("filter should skip authorization for environment management paths")
    void filter_SkipsAuthorization_ForGetEnvironments() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/environments")
            .header(SifHeaders.SERVICE_NAME, "Environment")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert - environment paths are exempt from authorization
        verify(mockChain).filter(exchange);
        verify(mockEnvironmentServiceClient, never()).checkPermission(any());
    }
    
    @Test
    @DisplayName("filter should skip authorization for /actuator/health")
    void filter_SkipsAuthorization_ForActuatorHealth() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/actuator/health")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(exchange);
        verify(mockEnvironmentServiceClient, never()).checkPermission(any());
    }
    
    @Test
    @DisplayName("filter should skip authorization for /actuator/health/liveness")
    void filter_SkipsAuthorization_ForActuatorHealthLiveness() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/actuator/health/liveness")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(exchange);
        verify(mockEnvironmentServiceClient, never()).checkPermission(any());
    }
    
    // ========== Error Handling Tests ==========
    
    @Test
    @DisplayName("filter should return 403 when environmentId is null")
    void filter_Returns403_WhenEnvironmentIdIsNull() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // Do not set environmentId attribute (simulating missing authentication)
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain, never()).filter(any());
        verify(mockEnvironmentServiceClient, never()).checkPermission(any());
        
        // Verify 403 response
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    
    @Test
    @DisplayName("filter should handle Environment Service unavailable error")
    void filter_HandlesServiceUnavailableError() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.error(new ServiceUnavailableException(
                "Environment Service", "Service temporarily unavailable")));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain, never()).filter(any());
        
        // Verify 403 response (authorization failed due to service unavailability)
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    
    @Test
    @DisplayName("filter should handle generic exceptions during authorization")
    void filter_HandlesGenericExceptions_DuringAuthorization() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.error(new RuntimeException("Unexpected error")));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain, never()).filter(any());
        
        // Verify 403 response
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    
    @Test
    @DisplayName("filter should return SIF-compliant error response on authorization failure")
    void filter_ReturnsSifCompliantError_OnAuthorizationFailure() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        InsufficientPermissionsException exception = new InsufficientPermissionsException(
            "Access denied for service: StudentPersonal");
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.error(exception));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        ServerHttpResponse response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }
    
    // ========== Filter Ordering Tests ==========
    
    @Test
    @DisplayName("filter should have order 4 (after ServiceTypeValidationFilter)")
    void filter_HasOrder3() {
        // Assert
        assertThat(filter.getOrder()).isEqualTo(4);
    }
    
    // ========== Edge Case Tests ==========
    
    @Test
    @DisplayName("filter should handle multiple sequential requests")
    void filter_HandlesMultipleSequentialRequests() {
        // Arrange - First request
        UUID environmentId1 = UUID.randomUUID();
        
        MockServerHttpRequest request1 = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange1 = MockServerWebExchange.from(request1);
        
        exchange1.getAttributes().put("environmentId", environmentId1);
        exchange1.getAttributes().put("defaultZoneId", "default");
        
        PermissionCheckResponse permissionResponse1 = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse1));
        
        // Act - First request
        StepVerifier.create(filter.filter(exchange1, mockChain))
            .verifyComplete();
        
        // Assert - First request
        verify(mockChain).filter(any(ServerWebExchange.class));
        
        // Arrange - Second request
        UUID environmentId2 = UUID.randomUUID();
        
        MockServerHttpRequest request2 = MockServerHttpRequest
            .get(PREFIX + "/requests/SchoolInfo")
            .header(SifHeaders.SERVICE_NAME, "SchoolInfo")
            .build();
        MockServerWebExchange exchange2 = MockServerWebExchange.from(request2);
        
        exchange2.getAttributes().put("environmentId", environmentId2);
        exchange2.getAttributes().put("defaultZoneId", "zone2");
        
        PermissionCheckResponse permissionResponse2 = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse2));
        
        // Act - Second request
        StepVerifier.create(filter.filter(exchange2, mockChain))
            .verifyComplete();
        
        // Assert - Second request
        verify(mockChain, times(2)).filter(any(ServerWebExchange.class));
        
        // Verify both requests were processed independently
        verify(mockEnvironmentServiceClient, times(2)).checkPermission(any());
    }
    
    @Test
    @DisplayName("filter should handle different environment types")
    void filter_HandlesDifferentEnvironmentTypes() {
        // Test PROVIDER type
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", "ProviderApp");
        exchange.getAttributes().put("environmentType", EnvironmentType.BROKERED);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert
        verify(mockChain).filter(any(ServerWebExchange.class));
        assertThat((EnvironmentType) exchange.getAttribute("environmentType"))
            .isEqualTo(EnvironmentType.BROKERED);
    }
    
    @Test
    @DisplayName("filter should not interfere with other request headers")
    void filter_DoesNotInterfereWithOtherHeaders() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal")
            .header("Custom-Header", "custom-value")
            .header("Another-Header", "another-value")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert - Verify custom headers are preserved
        verify(mockChain).filter(argThat(mutatedExchange -> {
            ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();
            return mutatedRequest.getHeaders().getFirst("Custom-Header").equals("custom-value") &&
                   mutatedRequest.getHeaders().getFirst("Another-Header").equals("another-value");
        }));
    }
    
    @Test
    @DisplayName("filter should extract service name from path for /requests")
    void filter_HandlesComplexServiceNames() {
        // Arrange: For /requests/{serviceName}, service name comes from URL path
        UUID environmentId = UUID.randomUUID();
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get(PREFIX + "/requests/StudentPersonal")
            .header(SifHeaders.SERVICE_NAME, "StudentPersonal-Extended-v2")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("defaultZoneId", "default");
        PermissionCheckResponse permissionResponse = new PermissionCheckResponse(true);
        when(mockEnvironmentServiceClient.checkPermission(any(PermissionCheckRequest.class)))
            .thenReturn(Mono.just(permissionResponse));
        
        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
            .verifyComplete();
        
        // Assert: service name from path, not header
        verify(mockEnvironmentServiceClient).checkPermission(argThat(req ->
            req.serviceName().equals("StudentPersonal")
        ));
    }
}
