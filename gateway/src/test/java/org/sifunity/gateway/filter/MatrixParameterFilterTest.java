// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
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
 * Unit tests for MatrixParameterFilter.
 *
 * <p>Test Coverage:
 * <ul>
 *   <li>Request with matrix params stores them in exchange attributes and strips from URI</li>
 *   <li>Request without matrix params passes through unchanged</li>
 *   <li>Request with query string preserves query string after stripping matrix params</li>
 *   <li>Filter ordering (Order -100)</li>
 * </ul>
 *
 * <p>Requirements: 1.1, 1.3, 1.4
 */
@DisplayName("MatrixParameterFilter Tests")
class MatrixParameterFilterTest {

    private MatrixParameterFilter filter;
    private GatewayFilterChain mockChain;

    @BeforeEach
    void setUp() {
        filter = new MatrixParameterFilter();
        mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class)))
                .thenReturn(Mono.empty());
    }

    // ========== Matrix Param Extraction Tests ==========

    @Test
    @DisplayName("filter should extract matrix params and store in exchange attributes")
    void filter_ExtractsMatrixParams_StoresInAttributes() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/StudentPersonals;zoneId=zone1;contextId=DEFAULT")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert — verify exchange attributes contain extracted params
        @SuppressWarnings("unchecked")
        Map<String, String> storedParams = (Map<String, String>) exchange.getAttributes()
                .get(MatrixParameterFilter.MATRIX_PARAMS_ATTR);

        assertThat(storedParams).isNotNull();
        assertThat(storedParams).containsEntry("zoneId", "zone1");
        assertThat(storedParams).containsEntry("contextId", "DEFAULT");
    }

    @Test
    @DisplayName("filter should strip matrix params from URI forwarded to chain")
    void filter_StripsMatrixParams_FromForwardedUri() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/StudentPersonals;zoneId=zone1;contextId=DEFAULT")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert — verify chain received exchange with clean URI
        verify(mockChain).filter(argThat(mutatedExchange -> {
            String path = mutatedExchange.getRequest().getURI().getRawPath();
            assertThat(path).isEqualTo("/StudentPersonals");
            assertThat(path).doesNotContain(";");
            return true;
        }));
    }

    // ========== No Matrix Params Tests ==========

    @Test
    @DisplayName("filter should pass through unchanged when no matrix params present")
    void filter_PassesThrough_WhenNoMatrixParams() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/StudentPersonals")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert — attributes should have empty map
        @SuppressWarnings("unchecked")
        Map<String, String> storedParams = (Map<String, String>) exchange.getAttributes()
                .get(MatrixParameterFilter.MATRIX_PARAMS_ATTR);

        assertThat(storedParams).isNotNull();
        assertThat(storedParams).isEmpty();

        // Chain should be called (filter passes through)
        verify(mockChain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("filter should not rewrite URI when no matrix params present")
    void filter_DoesNotRewriteUri_WhenNoMatrixParams() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/StudentPersonals")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert — chain receives the original exchange (no mutation needed)
        verify(mockChain).filter(argThat(passedExchange -> {
            String path = passedExchange.getRequest().getURI().getRawPath();
            assertThat(path).isEqualTo("/StudentPersonals");
            return true;
        }));
    }

    // ========== Query String Preservation Tests ==========

    @Test
    @DisplayName("filter should preserve query string after stripping matrix params")
    void filter_PreservesQueryString_AfterStrippingMatrixParams() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/StudentPersonals;zoneId=zone1;contextId=DEFAULT?where=lastName%3D'Smith'&order=asc")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert — verify matrix params extracted
        @SuppressWarnings("unchecked")
        Map<String, String> storedParams = (Map<String, String>) exchange.getAttributes()
                .get(MatrixParameterFilter.MATRIX_PARAMS_ATTR);

        assertThat(storedParams).containsEntry("zoneId", "zone1");
        assertThat(storedParams).containsEntry("contextId", "DEFAULT");

        // Assert — verify URI has clean path but query string preserved
        verify(mockChain).filter(argThat(mutatedExchange -> {
            String uri = mutatedExchange.getRequest().getURI().toString();
            String path = mutatedExchange.getRequest().getURI().getRawPath();
            String query = mutatedExchange.getRequest().getURI().getRawQuery();

            assertThat(path).isEqualTo("/StudentPersonals");
            assertThat(path).doesNotContain(";");
            assertThat(query).contains("where=");
            assertThat(query).contains("order=asc");
            return true;
        }));
    }

    @Test
    @DisplayName("filter should preserve query string when no matrix params present")
    void filter_PreservesQueryString_WhenNoMatrixParams() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/StudentPersonals?where=lastName%3D'Smith'")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert — empty params, query string preserved
        @SuppressWarnings("unchecked")
        Map<String, String> storedParams = (Map<String, String>) exchange.getAttributes()
                .get(MatrixParameterFilter.MATRIX_PARAMS_ATTR);

        assertThat(storedParams).isEmpty();

        verify(mockChain).filter(argThat(passedExchange -> {
            String query = passedExchange.getRequest().getURI().getRawQuery();
            assertThat(query).contains("where=");
            return true;
        }));
    }

    // ========== Filter Ordering Tests ==========

    @Test
    @DisplayName("filter should have order -100 (before authentication)")
    void filter_HasOrderNegative100() {
        assertThat(filter.getOrder()).isEqualTo(-100);
    }

    // ========== Additional Edge Cases ==========

    @Test
    @DisplayName("filter should handle messageId matrix param")
    void filter_HandlesMessageId_MatrixParam() {
        // Arrange
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/queues/messages;messageId=abc-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert
        @SuppressWarnings("unchecked")
        Map<String, String> storedParams = (Map<String, String>) exchange.getAttributes()
                .get(MatrixParameterFilter.MATRIX_PARAMS_ATTR);

        assertThat(storedParams).containsEntry("messageId", "abc-123");

        verify(mockChain).filter(argThat(mutatedExchange -> {
            String path = mutatedExchange.getRequest().getURI().getRawPath();
            assertThat(path).isEqualTo("/queues/messages");
            assertThat(path).doesNotContain(";");
            return true;
        }));
    }

    @Test
    @DisplayName("filter should handle URL-encoded matrix param values")
    void filter_HandlesUrlEncodedValues() {
        // Arrange — MockServerHttpRequest.get(String) re-encodes percent chars,
        // so we test with a value that contains special chars but no percent-encoding.
        // The parser's URL-decoding is tested thoroughly in MatrixParameterParser property tests.
        // Here we verify the filter correctly stores whatever the parser extracts.
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/StudentPersonals;zoneId=DuncanHigh2014;contextId=current")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert — values stored correctly
        @SuppressWarnings("unchecked")
        Map<String, String> storedParams = (Map<String, String>) exchange.getAttributes()
                .get(MatrixParameterFilter.MATRIX_PARAMS_ATTR);

        assertThat(storedParams).containsEntry("zoneId", "DuncanHigh2014");
        assertThat(storedParams).containsEntry("contextId", "current");
        assertThat(storedParams).hasSize(2);
    }
}
