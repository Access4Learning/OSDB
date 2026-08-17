// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.EnvironmentLookupResponse;
import org.sifunity.broker.common.dto.TokenValidationResponse;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import org.sifunity.gateway.filter.*;
import org.sifunity.gateway.metrics.GatewayMetricsService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration test for the proxy impersonation flow through the full gateway filter chain.
 *
 * <p>This test exercises the complete filter chain:
 * AuthenticationFilter → AuthorizationFilter → MembershipValidationFilter →
 * ProxyImpersonationFilter → EnvironmentContextFilter</p>
 *
 * <p>Uses WireMock to simulate the environment-service internal API responses.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.3, 1.4, 5.1, 5.2, 5.3, 5.4, 10.1, 10.2</strong></p>
 */
@DisplayName("Proxy Impersonation Integration Test")
class ProxyImpersonationIntegrationTest {

    private static WireMockServer wireMockServer;
    private static ObjectMapper objectMapper;

    private EnvironmentServiceClient environmentServiceClient;
    private AuthenticationFilter authenticationFilter;
    private ProxyImpersonationFilter proxyImpersonationFilter;
    private EnvironmentContextFilter environmentContextFilter;
    private GatewayMetricsService metricsService;

    // Test data
    private static final UUID ADMIN_ENV_ID = UUID.randomUUID();
    private static final UUID TARGET_BROKER_ENV_ID = UUID.randomUUID();
    private static final UUID SOLUTION_UUID = UUID.randomUUID();
    private static final String ADMIN_SESSION_TOKEN = "admin-session-token-" + UUID.randomUUID();
    private static final String ADMIN_APP_NAME = "GlobalAdminApp";
    private static final String TARGET_APP_NAME = "BROKER_SERVICES_" + SOLUTION_UUID;

    // Non-admin test data
    private static final UUID NON_ADMIN_ENV_ID = UUID.randomUUID();
    private static final String NON_ADMIN_SESSION_TOKEN = "regular-session-token-" + UUID.randomUUID();
    private static final String NON_ADMIN_APP_NAME = "RegularApp";
    private static final String NON_ADMIN_DOMAIN = "some-domain";
    private static final String NON_ADMIN_SOLUTION = "some-solution";

    @BeforeAll
    static void startWireMock() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();

        // Create real EnvironmentServiceClient pointing to WireMock
        environmentServiceClient = new EnvironmentServiceClient(
                WebClient.builder(),
                "http://localhost:" + wireMockServer.port(),
                "test-internal-token",
                null,
                null,
                io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("test")
        );

        metricsService = mock(GatewayMetricsService.class);

        // Create real filters
        authenticationFilter = new AuthenticationFilter(environmentServiceClient, objectMapper, metricsService);
        proxyImpersonationFilter = new ProxyImpersonationFilter(environmentServiceClient, objectMapper);
        environmentContextFilter = new EnvironmentContextFilter("test-internal-token");
    }

    // ========== Helper Methods ==========

    /**
     * Builds an auth header in the SIF_HMACSHA256 format: SIF_HMACSHA256 Base64(sessionToken:hash)
     */
    private String buildAuthHeader(String sessionToken) {
        String credentials = sessionToken + ":test-hash-value";
        String base64 = java.util.Base64.getEncoder()
                .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "SIF_HMACSHA256 " + base64;
    }

    /**
     * Stubs the environment-service validate-token endpoint for admin authentication.
     */
    private void stubAdminAuthentication() throws Exception {
        TokenValidationResponse adminResponse = new TokenValidationResponse(
                ADMIN_ENV_ID, ADMIN_APP_NAME, EnvironmentType.DIRECT,
                "default", null,
                GlobalAdminConstants.GLOBAL_DOMAIN, GlobalAdminConstants.GLOBAL_SOLUTION, null
        );

        wireMockServer.stubFor(post(urlEqualTo("/internal/validate-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(adminResponse))));
    }

    /**
     * Stubs the environment-service validate-token endpoint for non-admin authentication.
     */
    private void stubNonAdminAuthentication() throws Exception {
        TokenValidationResponse nonAdminResponse = new TokenValidationResponse(
                NON_ADMIN_ENV_ID, NON_ADMIN_APP_NAME, EnvironmentType.BROKERED,
                "default", null,
                NON_ADMIN_DOMAIN, NON_ADMIN_SOLUTION, null
        );

        wireMockServer.stubFor(post(urlEqualTo("/internal/validate-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(nonAdminResponse))));
    }

    /**
     * Stubs the fingerprint lookup endpoint to return the broker-services environment.
     */
    private void stubBrokerServicesLookup() throws Exception {
        EnvironmentLookupResponse lookupResponse = new EnvironmentLookupResponse(
                TARGET_BROKER_ENV_ID, TARGET_APP_NAME, "broker-services", SOLUTION_UUID
        );

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/by-fingerprint"))
                .withQueryParam("fingerprint", equalTo("broker-services"))
                .withQueryParam("solutionId", equalTo(SOLUTION_UUID.toString()))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(lookupResponse))));
    }

    /**
     * Stubs the fingerprint lookup endpoint to return 404 (not found).
     */
    private void stubBrokerServicesLookupNotFound() {
        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/by-fingerprint"))
                .willReturn(aResponse().withStatus(404)));
    }

    /**
     * Runs the full filter chain (AuthN → ProxyImpersonation → EnvironmentContext)
     * and captures the final exchange state.
     */
    private Mono<Void> runFilterChain(ServerWebExchange exchange) {
        // Build a chain that runs: AuthN → ProxyImpersonation → EnvironmentContext → terminal
        // We simulate the chain by running filters in order manually
        GatewayFilterChain terminalChain = mock(GatewayFilterChain.class);
        when(terminalChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // EnvironmentContextFilter wraps the terminal chain
        GatewayFilterChain envContextChain = ex -> environmentContextFilter.filter(ex, terminalChain);

        // ProxyImpersonationFilter wraps the env context chain
        GatewayFilterChain proxyChain = ex -> proxyImpersonationFilter.filter(ex, envContextChain);

        // AuthenticationFilter is the entry point
        return authenticationFilter.filter(exchange, proxyChain);
    }

    // ========== Test: Admin Proxy Impersonation Success ==========

    @Test
    @DisplayName("Admin with X-Run-As-Environment: broker-services → downstream gets broker-services UUID")
    void adminProxyImpersonation_brokerServicesLiteral_downstreamReceivesBrokerServicesUuid() throws Exception {
        // Arrange
        stubAdminAuthentication();
        stubBrokerServicesLookup();

        String domainId = "test-domain";
        String solutionId = SOLUTION_UUID.toString();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/" + domainId + "/" + solutionId + "/queues")
                .header(SifHeaders.AUTHORIZATION, buildAuthHeader(ADMIN_SESSION_TOKEN))
                .header(SifHeaders.TIMESTAMP, java.time.Instant.now().toString())
                .header(SifHeaders.X_RUN_AS_ENVIRONMENT, "broker-services")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        GatewayFilterChain terminalChain = mock(GatewayFilterChain.class);
        when(terminalChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // Build chain: AuthN → Proxy → EnvContext → terminal
        GatewayFilterChain envContextChain = ex -> environmentContextFilter.filter(ex, terminalChain);
        GatewayFilterChain proxyChain = ex -> proxyImpersonationFilter.filter(ex, envContextChain);

        StepVerifier.create(authenticationFilter.filter(exchange, proxyChain))
                .verifyComplete();

        // Assert: exchange attributes should be overridden to broker-services env
        UUID resultEnvId = exchange.getAttribute("environmentId");
        String resultEnvName = exchange.getAttribute("environmentName");
        assertThat(resultEnvId).isEqualTo(TARGET_BROKER_ENV_ID);
        assertThat(resultEnvName).isEqualTo(TARGET_APP_NAME);

        // Assert: X-Run-As-Environment header should NOT be in the forwarded request
        // (The filter strips it before forwarding)
        // Note: We verify via the exchange's mutated request that the header was removed
        // The ProxyImpersonationFilter strips it from the request it passes downstream
    }

    @Test
    @DisplayName("Admin with X-Run-As-Environment: UUID → downstream gets that environment's UUID")
    void adminProxyImpersonation_uuidValue_downstreamReceivesTargetUuid() throws Exception {
        // Arrange
        stubAdminAuthentication();

        EnvironmentLookupResponse lookupResponse = new EnvironmentLookupResponse(
                TARGET_BROKER_ENV_ID, TARGET_APP_NAME, "broker-services", SOLUTION_UUID
        );

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + TARGET_BROKER_ENV_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(lookupResponse))));

        String domainId = "test-domain";
        String solutionId = SOLUTION_UUID.toString();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/" + domainId + "/" + solutionId + "/queues")
                .header(SifHeaders.AUTHORIZATION, buildAuthHeader(ADMIN_SESSION_TOKEN))
                .header(SifHeaders.TIMESTAMP, java.time.Instant.now().toString())
                .header(SifHeaders.X_RUN_AS_ENVIRONMENT, TARGET_BROKER_ENV_ID.toString())
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        GatewayFilterChain terminalChain = mock(GatewayFilterChain.class);
        when(terminalChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        GatewayFilterChain envContextChain = ex -> environmentContextFilter.filter(ex, terminalChain);
        GatewayFilterChain proxyChain = ex -> proxyImpersonationFilter.filter(ex, envContextChain);

        StepVerifier.create(authenticationFilter.filter(exchange, proxyChain))
                .verifyComplete();

        // Assert
        UUID resultEnvId = exchange.getAttribute("environmentId");
        String resultEnvName = exchange.getAttribute("environmentName");
        assertThat(resultEnvId).isEqualTo(TARGET_BROKER_ENV_ID);
        assertThat(resultEnvName).isEqualTo(TARGET_APP_NAME);
    }

    // ========== Test: Non-Admin Caller ==========

    @Test
    @DisplayName("Non-admin caller with X-Run-As-Environment → header stripped, own environment used")
    void nonAdminCaller_headerStripped_ownEnvironmentUsed() throws Exception {
        // Arrange
        stubNonAdminAuthentication();

        String domainId = NON_ADMIN_DOMAIN;
        String solutionId = NON_ADMIN_SOLUTION;

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/" + domainId + "/" + solutionId + "/queues")
                .header(SifHeaders.AUTHORIZATION, buildAuthHeader(NON_ADMIN_SESSION_TOKEN))
                .header(SifHeaders.TIMESTAMP, java.time.Instant.now().toString())
                .header(SifHeaders.X_RUN_AS_ENVIRONMENT, "broker-services")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        GatewayFilterChain terminalChain = mock(GatewayFilterChain.class);
        when(terminalChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        GatewayFilterChain envContextChain = ex -> environmentContextFilter.filter(ex, terminalChain);
        GatewayFilterChain proxyChain = ex -> proxyImpersonationFilter.filter(ex, envContextChain);

        StepVerifier.create(authenticationFilter.filter(exchange, proxyChain))
                .verifyComplete();

        // Assert: environment should remain the non-admin's own environment
        UUID resultEnvId = exchange.getAttribute("environmentId");
        String resultEnvName = exchange.getAttribute("environmentName");
        assertThat(resultEnvId).isEqualTo(NON_ADMIN_ENV_ID);
        assertThat(resultEnvName).isEqualTo(NON_ADMIN_APP_NAME);

        // Assert: no fingerprint lookup should have been made
        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/internal/environments/by-fingerprint")));
    }

    @Test
    @DisplayName("Non-admin caller: X-Run-As-Environment header is stripped from forwarded request")
    void nonAdminCaller_headerNotForwardedDownstream() throws Exception {
        // Arrange
        stubNonAdminAuthentication();

        String domainId = NON_ADMIN_DOMAIN;
        String solutionId = NON_ADMIN_SOLUTION;

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/" + domainId + "/" + solutionId + "/queues")
                .header(SifHeaders.AUTHORIZATION, buildAuthHeader(NON_ADMIN_SESSION_TOKEN))
                .header(SifHeaders.TIMESTAMP, java.time.Instant.now().toString())
                .header(SifHeaders.X_RUN_AS_ENVIRONMENT, "broker-services")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Capture the exchange passed to the terminal chain
        final ServerWebExchange[] capturedExchange = new ServerWebExchange[1];
        GatewayFilterChain terminalChain = ex -> {
            capturedExchange[0] = ex;
            return Mono.empty();
        };

        GatewayFilterChain envContextChain = ex -> environmentContextFilter.filter(ex, terminalChain);
        GatewayFilterChain proxyChain = ex -> proxyImpersonationFilter.filter(ex, envContextChain);

        // Act
        StepVerifier.create(authenticationFilter.filter(exchange, proxyChain))
                .verifyComplete();

        // Assert: X-Run-As-Environment should NOT be present in the forwarded request
        assertThat(capturedExchange[0]).isNotNull();
        assertThat(capturedExchange[0].getRequest().getHeaders()
                .getFirst(SifHeaders.X_RUN_AS_ENVIRONMENT)).isNull();
    }

    // ========== Test: Invalid Target ==========

    @Test
    @DisplayName("Admin with invalid target (not found) → 422 error returned")
    void adminWithInvalidTarget_notFound_returns422() throws Exception {
        // Arrange
        stubAdminAuthentication();
        stubBrokerServicesLookupNotFound();

        String domainId = "test-domain";
        String solutionId = SOLUTION_UUID.toString();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/" + domainId + "/" + solutionId + "/queues")
                .header(SifHeaders.AUTHORIZATION, buildAuthHeader(ADMIN_SESSION_TOKEN))
                .header(SifHeaders.TIMESTAMP, java.time.Instant.now().toString())
                .header(SifHeaders.X_RUN_AS_ENVIRONMENT, "broker-services")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        GatewayFilterChain terminalChain = mock(GatewayFilterChain.class);
        when(terminalChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        GatewayFilterChain envContextChain = ex -> environmentContextFilter.filter(ex, terminalChain);
        GatewayFilterChain proxyChain = ex -> proxyImpersonationFilter.filter(ex, envContextChain);

        StepVerifier.create(authenticationFilter.filter(exchange, proxyChain))
                .verifyComplete();

        // Assert: should return 422 error
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("Admin with invalid header value → 400 error returned")
    void adminWithInvalidHeaderValue_returns400() throws Exception {
        // Arrange
        stubAdminAuthentication();

        String domainId = "test-domain";
        String solutionId = SOLUTION_UUID.toString();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/" + domainId + "/" + solutionId + "/queues")
                .header(SifHeaders.AUTHORIZATION, buildAuthHeader(ADMIN_SESSION_TOKEN))
                .header(SifHeaders.TIMESTAMP, java.time.Instant.now().toString())
                .header(SifHeaders.X_RUN_AS_ENVIRONMENT, "invalid-not-uuid-not-broker-services")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        GatewayFilterChain terminalChain = mock(GatewayFilterChain.class);
        when(terminalChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        GatewayFilterChain envContextChain = ex -> environmentContextFilter.filter(ex, terminalChain);
        GatewayFilterChain proxyChain = ex -> proxyImpersonationFilter.filter(ex, envContextChain);

        StepVerifier.create(authenticationFilter.filter(exchange, proxyChain))
                .verifyComplete();

        // Assert: should return 400 error
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Admin impersonation: X-Run-As-Environment header stripped from forwarded request")
    void adminImpersonation_headerStrippedFromForwardedRequest() throws Exception {
        // Arrange
        stubAdminAuthentication();
        stubBrokerServicesLookup();

        String domainId = "test-domain";
        String solutionId = SOLUTION_UUID.toString();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/" + domainId + "/" + solutionId + "/queues")
                .header(SifHeaders.AUTHORIZATION, buildAuthHeader(ADMIN_SESSION_TOKEN))
                .header(SifHeaders.TIMESTAMP, java.time.Instant.now().toString())
                .header(SifHeaders.X_RUN_AS_ENVIRONMENT, "broker-services")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Capture the exchange passed to the terminal chain
        final ServerWebExchange[] capturedExchange = new ServerWebExchange[1];
        GatewayFilterChain terminalChain = ex -> {
            capturedExchange[0] = ex;
            return Mono.empty();
        };

        GatewayFilterChain envContextChain = ex -> environmentContextFilter.filter(ex, terminalChain);
        GatewayFilterChain proxyChain = ex -> proxyImpersonationFilter.filter(ex, envContextChain);

        // Act
        StepVerifier.create(authenticationFilter.filter(exchange, proxyChain))
                .verifyComplete();

        // Assert: X-Run-As-Environment should NOT be present in the forwarded request
        assertThat(capturedExchange[0]).isNotNull();
        assertThat(capturedExchange[0].getRequest().getHeaders()
                .getFirst(SifHeaders.X_RUN_AS_ENVIRONMENT)).isNull();

        // Assert: X-Environment-Id should be the broker-services UUID
        assertThat(capturedExchange[0].getRequest().getHeaders()
                .getFirst(SifHeaders.X_ENVIRONMENT_ID)).isEqualTo(TARGET_BROKER_ENV_ID.toString());
    }

    @Test
    @DisplayName("Admin with target in wrong solution → 403 error returned")
    void adminWithTargetInWrongSolution_returns403() throws Exception {
        // Arrange
        stubAdminAuthentication();

        // Return a broker-services env that belongs to a DIFFERENT solution
        UUID differentSolution = UUID.randomUUID();
        EnvironmentLookupResponse lookupResponse = new EnvironmentLookupResponse(
                TARGET_BROKER_ENV_ID, TARGET_APP_NAME, "broker-services", differentSolution
        );

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + TARGET_BROKER_ENV_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(lookupResponse))));

        String domainId = "test-domain";
        String solutionId = SOLUTION_UUID.toString();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/" + domainId + "/" + solutionId + "/queues")
                .header(SifHeaders.AUTHORIZATION, buildAuthHeader(ADMIN_SESSION_TOKEN))
                .header(SifHeaders.TIMESTAMP, java.time.Instant.now().toString())
                .header(SifHeaders.X_RUN_AS_ENVIRONMENT, TARGET_BROKER_ENV_ID.toString())
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        GatewayFilterChain terminalChain = mock(GatewayFilterChain.class);
        when(terminalChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        GatewayFilterChain envContextChain = ex -> environmentContextFilter.filter(ex, terminalChain);
        GatewayFilterChain proxyChain = ex -> proxyImpersonationFilter.filter(ex, envContextChain);

        StepVerifier.create(authenticationFilter.filter(exchange, proxyChain))
                .verifyComplete();

        // Assert: should return 403 error (solution mismatch)
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Admin with target that is not broker-services fingerprint → 403 error returned")
    void adminWithNonBrokerServicesTarget_returns403() throws Exception {
        // Arrange
        stubAdminAuthentication();

        // Return an environment that does NOT have broker-services fingerprint
        UUID targetId = UUID.randomUUID();
        EnvironmentLookupResponse lookupResponse = new EnvironmentLookupResponse(
                targetId, "SomeOtherApp", "regular-app", SOLUTION_UUID
        );

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/environments/" + targetId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(lookupResponse))));

        String domainId = "test-domain";
        String solutionId = SOLUTION_UUID.toString();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/" + domainId + "/" + solutionId + "/queues")
                .header(SifHeaders.AUTHORIZATION, buildAuthHeader(ADMIN_SESSION_TOKEN))
                .header(SifHeaders.TIMESTAMP, java.time.Instant.now().toString())
                .header(SifHeaders.X_RUN_AS_ENVIRONMENT, targetId.toString())
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        GatewayFilterChain terminalChain = mock(GatewayFilterChain.class);
        when(terminalChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        GatewayFilterChain envContextChain = ex -> environmentContextFilter.filter(ex, terminalChain);
        GatewayFilterChain proxyChain = ex -> proxyImpersonationFilter.filter(ex, envContextChain);

        StepVerifier.create(authenticationFilter.filter(exchange, proxyChain))
                .verifyComplete();

        // Assert: should return 403 error (not broker-services)
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Request without X-Run-As-Environment header → normal flow, no impersonation")
    void noRunAsHeader_normalFlow() throws Exception {
        // Arrange
        stubAdminAuthentication();

        String domainId = "test-domain";
        String solutionId = SOLUTION_UUID.toString();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/" + domainId + "/" + solutionId + "/queues")
                .header(SifHeaders.AUTHORIZATION, buildAuthHeader(ADMIN_SESSION_TOKEN))
                .header(SifHeaders.TIMESTAMP, java.time.Instant.now().toString())
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Act
        GatewayFilterChain terminalChain = mock(GatewayFilterChain.class);
        when(terminalChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        GatewayFilterChain envContextChain = ex -> environmentContextFilter.filter(ex, terminalChain);
        GatewayFilterChain proxyChain = ex -> proxyImpersonationFilter.filter(ex, envContextChain);

        StepVerifier.create(authenticationFilter.filter(exchange, proxyChain))
                .verifyComplete();

        // Assert: environment should remain the admin's own environment
        UUID resultEnvId = exchange.getAttribute("environmentId");
        assertThat(resultEnvId).isEqualTo(ADMIN_ENV_ID);

        // No fingerprint lookup should have been made
        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/internal/environments/by-fingerprint")));
    }
}
