// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.TokenValidationRequest;
import org.sifunity.broker.common.dto.TokenValidationResponse;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import org.sifunity.gateway.metrics.GatewayMetricsService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for fingerprint omission when the authenticated environment
 * has a null or blank fingerprint.
 *
 * <p>Validates: Requirement 4.4 — WHEN the authenticated environment has no
 * fingerprint value, THE Gateway SHALL omit the fingerprint header rather
 * than injecting a null or empty value.</p>
 *
 * @see AuthenticationFilter
 * @see TokenValidationResponse
 */
class AuthenticationFilterFingerprintTest {

    private EnvironmentServiceClient mockClient;
    private GatewayMetricsService mockMetrics;
    private AuthenticationFilter filter;
    private GatewayFilterChain mockChain;
    private AtomicReference<ServerWebExchange> capturedExchange;

    @BeforeEach
    void setUp() {
        mockClient = mock(EnvironmentServiceClient.class);
        mockMetrics = mock(GatewayMetricsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        filter = new AuthenticationFilter(mockClient, objectMapper, mockMetrics);

        capturedExchange = new AtomicReference<>();
        mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            capturedExchange.set(invocation.getArgument(0));
            return Mono.empty();
        });
    }

    private MockServerWebExchange buildExchangeWithClientFingerprint(String clientFingerprint) {
        String sessionToken = UUID.randomUUID().toString();
        String credentials = sessionToken + ":testhash";
        String base64Creds = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64Creds;

        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest
                .get("/requests/StudentPersonal")
                .header(SifHeaders.AUTHORIZATION, authHeader)
                .header(SifHeaders.TIMESTAMP, "1705318200000");

        if (clientFingerprint != null) {
            builder.header("fingerprint", clientFingerprint);
        }

        return MockServerWebExchange.from(builder.build());
    }

    private void stubValidationResponse(String fingerprint) {
        TokenValidationResponse response = new TokenValidationResponse(
                UUID.randomUUID(),
                "TestApp",
                EnvironmentType.BROKERED,
                "default",
                fingerprint,
                null,
                null,
                null
        );
        when(mockClient.validateToken(any(TokenValidationRequest.class)))
                .thenReturn(Mono.just(response));
    }

    @Test
    @DisplayName("Null fingerprint: downstream request should NOT contain fingerprint header")
    void nullFingerprint_omitsHeader() {
        stubValidationResponse(null);
        MockServerWebExchange exchange = buildExchangeWithClientFingerprint(null);

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getHeaders().getFirst("fingerprint"))
                .as("fingerprint header should be absent when authenticated fingerprint is null")
                .isNull();
    }

    @Test
    @DisplayName("Empty string fingerprint: downstream request should NOT contain fingerprint header")
    void emptyFingerprint_omitsHeader() {
        stubValidationResponse("");
        MockServerWebExchange exchange = buildExchangeWithClientFingerprint(null);

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getHeaders().getFirst("fingerprint"))
                .as("fingerprint header should be absent when authenticated fingerprint is empty")
                .isNull();
    }

    @Test
    @DisplayName("Blank fingerprint: downstream request should NOT contain fingerprint header")
    void blankFingerprint_omitsHeader() {
        stubValidationResponse("   ");
        MockServerWebExchange exchange = buildExchangeWithClientFingerprint(null);

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getHeaders().getFirst("fingerprint"))
                .as("fingerprint header should be absent when authenticated fingerprint is blank")
                .isNull();
    }

    @Test
    @DisplayName("Client-supplied fingerprint is stripped when authenticated fingerprint is null")
    void clientFingerprintStripped_whenAuthenticatedFingerprintNull() {
        stubValidationResponse(null);
        MockServerWebExchange exchange = buildExchangeWithClientFingerprint("client-spoofed-value");

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getHeaders().getFirst("fingerprint"))
                .as("client-supplied fingerprint should be stripped even when authenticated fingerprint is null")
                .isNull();
    }

    @Test
    @DisplayName("Client-supplied fingerprint is stripped when authenticated fingerprint is blank")
    void clientFingerprintStripped_whenAuthenticatedFingerprintBlank() {
        stubValidationResponse("   ");
        MockServerWebExchange exchange = buildExchangeWithClientFingerprint("client-spoofed-value");

        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        assertThat(capturedExchange.get()).isNotNull();
        assertThat(capturedExchange.get().getRequest().getHeaders().getFirst("fingerprint"))
                .as("client-supplied fingerprint should be stripped even when authenticated fingerprint is blank")
                .isNull();
    }
}
