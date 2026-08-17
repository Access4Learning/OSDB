// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter.property;

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
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test asserting that a {@code broker-services} environment (the per-solution
 * solution-admin credential) authenticates successfully through the gateway.
 *
 * <p>This is a regression guard. The gateway previously blocked the {@code broker-services}
 * fingerprint with AUTH_010 ("Internal environment cannot authenticate externally"). That block
 * was removed because {@code broker-services} IS the solution-admin credential and must be able
 * to authenticate directly (used by the admin console solution-admin login and by API tests).
 * Solution isolation is enforced downstream by the gateway's MembershipValidationFilter and,
 * independently, by the environment-service AuthorizationFilter's solution-scoped admin check.</p>
 *
 * <p>The block had been re-introduced twice by well-intentioned "security" commits; this test
 * exists to prevent a third regression.</p>
 *
 * @see AuthenticationFilter
 */
class BrokerServicesDirectAuthPropertyTest {

    /**
     * For any authentication attempt where the resolved environment has fingerprint
     * "broker-services", the AuthenticationFilter SHALL allow the request through the chain
     * and SHALL NOT record an AUTH_010 failure.
     */
    @Property(tries = 100)
    @Label("broker-services fingerprint authenticates successfully (no AUTH_010 block)")
    void brokerServicesFingerprintAuthenticatesSuccessfully(
            @ForAll("sessionTokens") String sessionToken,
            @ForAll("applicationNames") String applicationName,
            @ForAll("environmentTypes") EnvironmentType environmentType,
            @ForAll("optionalDomainIds") String domainId,
            @ForAll("optionalSolutionIds") String solutionId) {

        // --- Arrange ---
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayMetricsService mockMetrics = mock(GatewayMetricsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        AuthenticationFilter filter = new AuthenticationFilter(mockClient, objectMapper, mockMetrics);

        String credentials = sessionToken + ":testhash";
        String base64Creds = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        String authHeader = "SIF_HMACSHA256 " + base64Creds;

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/requests/StudentPersonal")
                .header(SifHeaders.AUTHORIZATION, authHeader)
                .header(SifHeaders.TIMESTAMP, "1705318200000")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        UUID environmentId = UUID.randomUUID();
        TokenValidationResponse validationResponse = new TokenValidationResponse(
                environmentId,
                applicationName,
                environmentType,
                "default",
                "broker-services",  // solution-admin credential — must authenticate, not be blocked
                domainId,
                solutionId,
                null
        );

        when(mockClient.validateToken(any(TokenValidationRequest.class)))
                .thenReturn(Mono.just(validationResponse));

        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // --- Act ---
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // --- Assert ---
        // Request proceeds downstream — broker-services is a valid external credential.
        verify(mockChain).filter(any(ServerWebExchange.class));

        // Not blocked with 401.
        assertThat(exchange.getResponse().getStatusCode())
                .as("broker-services must not be blocked at authentication")
                .isNotEqualTo(HttpStatus.UNAUTHORIZED);

        // No AUTH_010 failure recorded.
        verify(mockMetrics, never()).recordAuthenticationFailure("AUTH_010");
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> sessionTokens() {
        return Arbitraries.create(() -> UUID.randomUUID().toString());
    }

    @Provide
    Arbitrary<String> applicationNames() {
        return Arbitraries.of(
                "Internal Broker Services",
                "TestApp",
                "AdminConsole",
                "acceptancetesting-admin"
        );
    }

    @Provide
    Arbitrary<EnvironmentType> environmentTypes() {
        return Arbitraries.of(EnvironmentType.values());
    }

    @Provide
    Arbitrary<String> optionalDomainIds() {
        return Arbitraries.oneOf(
                Arbitraries.just((String) null),
                Arbitraries.create(() -> UUID.randomUUID().toString())
        );
    }

    @Provide
    Arbitrary<String> optionalSolutionIds() {
        return Arbitraries.oneOf(
                Arbitraries.just((String) null),
                Arbitraries.create(() -> UUID.randomUUID().toString())
        );
    }
}
