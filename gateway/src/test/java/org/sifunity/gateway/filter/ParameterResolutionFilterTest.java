// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.SifHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ParameterResolutionFilter.
 *
 * <p>Test Coverage:
 * <ul>
 *   <li>Conflict between matrix and header returns 400</li>
 *   <li>Matrix-only values forwarded as headers</li>
 *   <li>Header-only values preserved</li>
 *   <li>Missing zoneId with no default returns 400</li>
 *   <li>Missing contextId defaults to "DEFAULT"</li>
 * </ul>
 *
 * <p>Requirements: 2.1, 2.2, 3.1, 3.2, 3.3
 */
@DisplayName("ParameterResolutionFilter Tests")
class ParameterResolutionFilterTest {

    private ParameterResolutionFilter filter;
    private GatewayFilterChain mockChain;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        filter = new ParameterResolutionFilter(objectMapper);
        mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class)))
                .thenReturn(Mono.empty());
    }

    // ========== Conflict Detection Tests ==========

    @Test
    @DisplayName("filter should return 400 when matrix and header zoneId conflict")
    void filter_Returns400_WhenMatrixAndHeaderZoneIdConflict() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/test-domain/test-solution/requests/StudentPersonals")
                .header(SifHeaders.ZONE_ID, "headerZone")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Set exchange attributes (simulating prior filters)
        exchange.getAttributes().put("environmentId", java.util.UUID.randomUUID());
        exchange.getAttributes().put(MatrixParameterFilter.MATRIX_PARAMS_ATTR,
                Map.of("zoneId", "matrixZone"));
        exchange.getAttributes().put("defaultZoneId", "fallbackZone");
        exchange.getAttributes().put("environmentName", "TestEnv");

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert — 400 response, chain NOT called
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mockChain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("filter should return 400 when matrix and header contextId conflict")
    void filter_Returns400_WhenMatrixAndHeaderContextIdConflict() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/test-domain/test-solution/requests/StudentPersonals")
                .header(SifHeaders.CONTEXT_ID, "headerContext")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        exchange.getAttributes().put("environmentId", java.util.UUID.randomUUID());
        exchange.getAttributes().put(MatrixParameterFilter.MATRIX_PARAMS_ATTR,
                Map.of("contextId", "matrixContext"));
        exchange.getAttributes().put("defaultZoneId", "fallbackZone");
        exchange.getAttributes().put("environmentName", "TestEnv");

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mockChain, never()).filter(any(ServerWebExchange.class));
    }

    // ========== Matrix-Only Values Tests ==========

    @Test
    @DisplayName("filter should forward matrix-only zoneId and contextId as headers")
    void filter_ForwardsMatrixOnlyValues_AsHeaders() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/test-domain/test-solution/requests/StudentPersonals")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        exchange.getAttributes().put("environmentId", java.util.UUID.randomUUID());
        exchange.getAttributes().put(MatrixParameterFilter.MATRIX_PARAMS_ATTR,
                Map.of("zoneId", "matrixZone", "contextId", "matrixContext"));
        exchange.getAttributes().put("defaultZoneId", "fallbackZone");
        exchange.getAttributes().put("environmentName", "TestEnv");

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert — chain called with mutated request containing resolved headers
        verify(mockChain).filter(argThat(mutatedExchange -> {
            String zoneId = mutatedExchange.getRequest().getHeaders().getFirst(SifHeaders.ZONE_ID);
            String contextId = mutatedExchange.getRequest().getHeaders().getFirst(SifHeaders.CONTEXT_ID);
            assertThat(zoneId).isEqualTo("matrixZone");
            assertThat(contextId).isEqualTo("matrixContext");
            return true;
        }));
    }

    @Test
    @DisplayName("filter should forward matrix deleteMessageId as header")
    void filter_ForwardsMatrixDeleteMessageId_AsHeader() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/test-domain/test-solution/queues/test-queue/messages")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        exchange.getAttributes().put("environmentId", java.util.UUID.randomUUID());
        exchange.getAttributes().put(MatrixParameterFilter.MATRIX_PARAMS_ATTR,
                Map.of("messageId", "msg-123", "zoneId", "zone1"));
        exchange.getAttributes().put("defaultZoneId", "fallbackZone");
        exchange.getAttributes().put("environmentName", "TestEnv");

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert
        verify(mockChain).filter(argThat(mutatedExchange -> {
            String deleteMessageId = mutatedExchange.getRequest().getHeaders().getFirst("deleteMessageId");
            assertThat(deleteMessageId).isEqualTo("msg-123");
            return true;
        }));
    }

    // ========== Header-Only Values Tests ==========

    @Test
    @DisplayName("filter should preserve header-only zoneId and contextId")
    void filter_PreservesHeaderOnlyValues() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/test-domain/test-solution/requests/StudentPersonals")
                .header(SifHeaders.ZONE_ID, "headerZone")
                .header(SifHeaders.CONTEXT_ID, "headerContext")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        exchange.getAttributes().put("environmentId", java.util.UUID.randomUUID());
        exchange.getAttributes().put(MatrixParameterFilter.MATRIX_PARAMS_ATTR, Map.of());
        exchange.getAttributes().put("defaultZoneId", "fallbackZone");
        exchange.getAttributes().put("environmentName", "TestEnv");

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert — chain called with headers preserved
        verify(mockChain).filter(argThat(mutatedExchange -> {
            String zoneId = mutatedExchange.getRequest().getHeaders().getFirst(SifHeaders.ZONE_ID);
            String contextId = mutatedExchange.getRequest().getHeaders().getFirst(SifHeaders.CONTEXT_ID);
            assertThat(zoneId).isEqualTo("headerZone");
            assertThat(contextId).isEqualTo("headerContext");
            return true;
        }));
    }

    // ========== Default Injection Tests ==========

    @Test
    @DisplayName("filter should return 400 when zoneId missing and no default configured on connector path")
    void filter_Returns400_WhenZoneIdMissing_NoDefault() {
        // Arrange — no zoneId in matrix or headers, no defaultZoneId attribute
        // Use a connector path (/events) which requires mandatory zoneId
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/test-domain/test-solution/events")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        exchange.getAttributes().put("environmentId", java.util.UUID.randomUUID());
        exchange.getAttributes().put(MatrixParameterFilter.MATRIX_PARAMS_ATTR, Map.of());
        // No defaultZoneId attribute set
        exchange.getAttributes().put("environmentName", "TestEnv");

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert — 400 response, chain NOT called
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mockChain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("filter should pass through infrastructure paths without zoneId")
    void filter_PassesThrough_WhenInfrastructurePath_NoZoneId() {
        // Arrange — no zoneId, no defaultZoneId, but path is an infrastructure endpoint
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/test-domain/test-solution/queues/some-queue-id/messages")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        exchange.getAttributes().put("environmentId", java.util.UUID.randomUUID());
        exchange.getAttributes().put(MatrixParameterFilter.MATRIX_PARAMS_ATTR, Map.of());
        // No defaultZoneId attribute set
        exchange.getAttributes().put("environmentName", "TestEnv");

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert — chain IS called (pass through), no 400 error
        verify(mockChain).filter(any(ServerWebExchange.class));
        assertThat(exchange.getResponse().getStatusCode()).isNull(); // not set = no error
    }

    @Test
    @DisplayName("filter should use defaultZoneId when zoneId missing from both sources")
    void filter_UsesDefaultZoneId_WhenMissing() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/test-domain/test-solution/requests/StudentPersonals")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        exchange.getAttributes().put("environmentId", java.util.UUID.randomUUID());
        exchange.getAttributes().put(MatrixParameterFilter.MATRIX_PARAMS_ATTR, Map.of());
        exchange.getAttributes().put("defaultZoneId", "envDefaultZone");
        exchange.getAttributes().put("environmentName", "TestEnv");

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert — chain called with default zoneId
        verify(mockChain).filter(argThat(mutatedExchange -> {
            String zoneId = mutatedExchange.getRequest().getHeaders().getFirst(SifHeaders.ZONE_ID);
            assertThat(zoneId).isEqualTo("envDefaultZone");
            return true;
        }));
    }

    @Test
    @DisplayName("filter should default contextId to DEFAULT when missing from both sources")
    void filter_DefaultsContextId_ToDefault() {
        // Arrange — zoneId provided via matrix, contextId absent
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/test-domain/test-solution/requests/StudentPersonals")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        exchange.getAttributes().put("environmentId", java.util.UUID.randomUUID());
        exchange.getAttributes().put(MatrixParameterFilter.MATRIX_PARAMS_ATTR,
                Map.of("zoneId", "zone1"));
        exchange.getAttributes().put("defaultZoneId", "fallbackZone");
        exchange.getAttributes().put("environmentName", "TestEnv");

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert — contextId defaults to "DEFAULT"
        verify(mockChain).filter(argThat(mutatedExchange -> {
            String contextId = mutatedExchange.getRequest().getHeaders().getFirst(SifHeaders.CONTEXT_ID);
            assertThat(contextId).isEqualTo("DEFAULT");
            return true;
        }));
    }

    // ========== Skip Unauthenticated Requests ==========

    @Test
    @DisplayName("filter should skip when no environmentId attribute (unauthenticated)")
    void filter_Skips_WhenNoEnvironmentId() {
        // Arrange — no environmentId means exempt path (health check, etc.)
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/actuator/health")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        // No environmentId attribute set

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert — chain called without modification
        verify(mockChain).filter(exchange);
    }

    // ========== Filter Ordering ==========

    @Test
    @DisplayName("filter should have order 8 (after EnvironmentContextFilter)")
    void filter_HasOrder7() {
        assertThat(filter.getOrder()).isEqualTo(8);
    }

    // ========== Agreement Case ==========

    @Test
    @DisplayName("filter should pass when matrix and header zoneId agree")
    void filter_Passes_WhenMatrixAndHeaderAgree() {
        // Arrange — same zoneId in both sources
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/test-domain/test-solution/requests/StudentPersonals")
                .header(SifHeaders.ZONE_ID, "sameZone")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        exchange.getAttributes().put("environmentId", java.util.UUID.randomUUID());
        exchange.getAttributes().put(MatrixParameterFilter.MATRIX_PARAMS_ATTR,
                Map.of("zoneId", "sameZone"));
        exchange.getAttributes().put("defaultZoneId", "fallbackZone");
        exchange.getAttributes().put("environmentName", "TestEnv");

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert — chain called with agreed value
        verify(mockChain).filter(argThat(mutatedExchange -> {
            String zoneId = mutatedExchange.getRequest().getHeaders().getFirst(SifHeaders.ZONE_ID);
            assertThat(zoneId).isEqualTo("sameZone");
            return true;
        }));
    }
}
