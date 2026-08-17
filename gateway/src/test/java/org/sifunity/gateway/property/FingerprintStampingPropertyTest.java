// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.TokenValidationRequest;
import org.sifunity.broker.common.dto.TokenValidationResponse;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import org.sifunity.gateway.filter.AuthenticationFilter;
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
 * Property-based test for gateway fingerprint stamping.
 *
 * <p>Verifies that the AuthenticationFilter always replaces any client-supplied
 * fingerprint header with the authenticated value from TokenValidationResponse.</p>
 *
 * <p><b>Validates: Requirements 4.1, 4.2</b></p>
 *
 * @see AuthenticationFilter
 * @see TokenValidationResponse
 */
class FingerprintStampingPropertyTest {

    /**
     * Property 8: Gateway fingerprint stamping replaces client value with authenticated value.
     *
     * <p><b>Validates: Requirements 4.1, 4.2</b></p>
     *
     * <p>For any incoming request with an arbitrary client-supplied fingerprint header
     * and for any TokenValidationResponse with a non-null, non-blank fingerprint,
     * after the AuthenticationFilter runs, the downstream request's fingerprint header
     * should equal the authenticated fingerprint value (not the client-supplied value).</p>
     */
    @Property(tries = 100)
    @Label("Feature: header-passthrough-and-sanitization, Property 8: Gateway fingerprint stamping replaces client value with authenticated value")
    void downstreamFingerprintEqualsAuthenticatedValue(
            @ForAll("clientFingerprints") String clientFingerprint,
            @ForAll("authenticatedFingerprints") String authenticatedFingerprint) {

        // --- Arrange ---
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayMetricsService mockMetrics = mock(GatewayMetricsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        AuthenticationFilter filter = new AuthenticationFilter(mockClient, objectMapper, mockMetrics);

        // Build a valid SIF auth header so the filter proceeds to token validation
        String sessionToken = UUID.randomUUID().toString();
        String hash = "testhash";
        String credentials = sessionToken + ":" + hash;
        String base64Creds = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64Creds;
        String timestamp = "1705318200000";

        // Request carries a client-supplied fingerprint header
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/requests/StudentPersonal")
                .header(SifHeaders.AUTHORIZATION, authHeader)
                .header(SifHeaders.TIMESTAMP, timestamp)
                .header("fingerprint", clientFingerprint)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        UUID environmentId = UUID.randomUUID();
        TokenValidationResponse validationResponse = new TokenValidationResponse(
                environmentId,
                "TestApp",
                EnvironmentType.BROKERED,
                "default",
                authenticatedFingerprint,
                null,
                null,
                null
        );

        when(mockClient.validateToken(any(TokenValidationRequest.class)))
                .thenReturn(Mono.just(validationResponse));

        // Capture the downstream exchange passed to the filter chain
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            capturedExchange.set(invocation.getArgument(0));
            return Mono.empty();
        });

        // --- Act ---
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // --- Assert ---
        assertThat(capturedExchange.get()).isNotNull();
        String downstreamFingerprint = capturedExchange.get()
                .getRequest().getHeaders().getFirst("fingerprint");

        // The downstream fingerprint must be the authenticated value, not the client value
        assertThat(downstreamFingerprint)
                .as("downstream fingerprint header should equal authenticated value, not client value")
                .isEqualTo(authenticatedFingerprint);
    }

    // --- Arbitraries ---

    @Provide
    Arbitrary<String> clientFingerprints() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-', '_', '.')
                .ofMinLength(1)
                .ofMaxLength(64);
    }

    @Provide
    Arbitrary<String> authenticatedFingerprints() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-', '_', '.')
                .ofMinLength(1)
                .ofMaxLength(64);
    }
}
