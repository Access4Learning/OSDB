// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.ErrorCodes;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.EnvironmentLookupResponse;
import org.sifunity.broker.common.exception.ServiceUnavailableException;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import org.sifunity.gateway.filter.ProxyImpersonationFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for ProxyImpersonationFilter.
 *
 * <p>Feature: admin-proxy-impersonation</p>
 *
 * <p>Tests Properties 1-9 from the design document covering header stripping,
 * non-admin bypass, header validation, target resolution, fingerprint validation,
 * solution membership, context override, correlation ID preservation, and
 * exception safety.</p>
 */
class ProxyImpersonationPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // ========== Property 1: Header Stripping Invariant ==========

    /**
     * Property 1: Header Stripping Invariant.
     *
     * <p>For any request passing through the filter (admin, non-admin, header present/absent/valid/invalid),
     * X-Run-As-Environment SHALL NOT be present in the forwarded request.</p>
     *
     * <p><b>Validates: Requirements 1.4, 10.1, 10.2</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: admin-proxy-impersonation, Property 1: Header Stripping Invariant")
    void headerIsAlwaysStrippedFromForwardedRequest(
            @ForAll("headerValues") String headerValue,
            @ForAll("callerTypes") CallerType callerType) {

        // --- Arrange ---
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        ProxyImpersonationFilter filter = new ProxyImpersonationFilter(mockClient, objectMapper);

        UUID solutionId = UUID.randomUUID();
        MockServerHttpRequest.BaseBuilder<?> requestBuilder = MockServerHttpRequest
                .get("/{domainId}/{solutionId}/queues",
                        UUID.randomUUID().toString(), solutionId.toString());

        if (headerValue != null) {
            requestBuilder.header(SifHeaders.X_RUN_AS_ENVIRONMENT, headerValue);
        }

        MockServerWebExchange exchange = MockServerWebExchange.from(requestBuilder.build());

        // Set caller attributes
        if (callerType == CallerType.ADMIN) {
            exchange.getAttributes().put("tokenDomainId", GlobalAdminConstants.GLOBAL_DOMAIN);
            exchange.getAttributes().put("tokenSolutionId", GlobalAdminConstants.GLOBAL_SOLUTION);
        } else {
            exchange.getAttributes().put("tokenDomainId", UUID.randomUUID().toString());
            exchange.getAttributes().put("tokenSolutionId", UUID.randomUUID().toString());
        }
        exchange.getAttributes().put("environmentId", UUID.randomUUID());
        exchange.getAttributes().put("solutionId", solutionId.toString());

        // Mock client to return empty (target not found) — we only care about header stripping
        when(mockClient.lookupEnvironmentByFingerprint(any(), any(), any())).thenReturn(Mono.empty());
        when(mockClient.lookupEnvironmentById(any())).thenReturn(Mono.empty());

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
        // If chain was called, verify header is stripped
        if (capturedExchange.get() != null) {
            assertThat(capturedExchange.get().getRequest().getHeaders()
                    .getFirst(SifHeaders.X_RUN_AS_ENVIRONMENT))
                    .as("X-Run-As-Environment must be stripped from forwarded request")
                    .isNull();
        }
        // If chain was NOT called (error response), the header is not forwarded at all — also valid
    }

    // ========== Property 2: Non-Admin Bypass ==========

    /**
     * Property 2: Non-Admin Bypass.
     *
     * <p>For any non-admin caller with any X-Run-As-Environment value, exchange attributes
     * remain unchanged and no error is returned.</p>
     *
     * <p><b>Validates: Requirements 1.3, 2.1, 2.2</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: admin-proxy-impersonation, Property 2: Non-Admin Bypass")
    void nonAdminCallerBypassesFilterWithoutChanges(
            @ForAll("anyHeaderValues") String headerValue,
            @ForAll("nonAdminDomainIds") String domainId,
            @ForAll("nonAdminSolutionIds") String solutionId) {

        // --- Arrange ---
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        ProxyImpersonationFilter filter = new ProxyImpersonationFilter(mockClient, objectMapper);

        UUID originalEnvironmentId = UUID.randomUUID();
        String originalEnvironmentName = "OriginalApp_" + UUID.randomUUID();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/{domainId}/{solutionId}/queues",
                        UUID.randomUUID().toString(), UUID.randomUUID().toString())
                .header(SifHeaders.X_RUN_AS_ENVIRONMENT, headerValue)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Set non-admin caller attributes
        exchange.getAttributes().put("tokenDomainId", domainId);
        exchange.getAttributes().put("tokenSolutionId", solutionId);
        exchange.getAttributes().put("environmentId", originalEnvironmentId);
        exchange.getAttributes().put("environmentName", originalEnvironmentName);
        exchange.getAttributes().put("solutionId", UUID.randomUUID().toString());

        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // --- Act ---
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // --- Assert ---
        // Chain must be called (no error)
        verify(mockChain).filter(any(ServerWebExchange.class));

        // Exchange attributes must remain unchanged
        assertThat(exchange.getAttributes().get("environmentId"))
                .as("environmentId must not change for non-admin caller")
                .isEqualTo(originalEnvironmentId);
        assertThat(exchange.getAttributes().get("environmentName"))
                .as("environmentName must not change for non-admin caller")
                .isEqualTo(originalEnvironmentName);

        // No error response
        assertThat(exchange.getResponse().getStatusCode())
                .as("No error status for non-admin caller")
                .isNull();

        // Environment service should never be called for non-admin
        verify(mockClient, never()).lookupEnvironmentByFingerprint(any(), any(), any());
        verify(mockClient, never()).lookupEnvironmentById(any());
    }

    // ========== Property 3: Header Value Validation ==========

    /**
     * Property 3: Header Value Validation.
     *
     * <p>For any string value from a global admin, filter accepts iff value is valid UUID
     * or exact "broker-services"; all others → 400 PROXY_005.</p>
     *
     * <p><b>Validates: Requirements 1.2, 9.2</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: admin-proxy-impersonation, Property 3: Header Value Validation")
    void invalidHeaderValuesProduceBadRequest(
            @ForAll("invalidHeaderValues") String invalidValue) {

        // --- Arrange ---
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        ProxyImpersonationFilter filter = new ProxyImpersonationFilter(mockClient, objectMapper);

        UUID solutionId = UUID.randomUUID();
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/{domainId}/{solutionId}/queues",
                        UUID.randomUUID().toString(), solutionId.toString())
                .header(SifHeaders.X_RUN_AS_ENVIRONMENT, invalidValue)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Set admin caller
        exchange.getAttributes().put("tokenDomainId", GlobalAdminConstants.GLOBAL_DOMAIN);
        exchange.getAttributes().put("tokenSolutionId", GlobalAdminConstants.GLOBAL_SOLUTION);
        exchange.getAttributes().put("environmentId", UUID.randomUUID());
        exchange.getAttributes().put("solutionId", solutionId.toString());

        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // --- Act ---
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // --- Assert ---
        assertThat(exchange.getResponse().getStatusCode())
                .as("Invalid header value must produce 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Chain must NOT be called
        verify(mockChain, never()).filter(any(ServerWebExchange.class));
    }

    /**
     * Property 3 (positive variant): Valid header values are accepted (not rejected with 400).
     *
     * <p><b>Validates: Requirements 1.2, 9.2</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: admin-proxy-impersonation, Property 3: Valid header values are accepted")
    void validHeaderValuesAreAccepted(
            @ForAll("validHeaderValues") String validValue) {

        // --- Arrange ---
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        ProxyImpersonationFilter filter = new ProxyImpersonationFilter(mockClient, objectMapper);

        UUID solutionId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/{domainId}/{solutionId}/queues",
                        UUID.randomUUID().toString(), solutionId.toString())
                .header(SifHeaders.X_RUN_AS_ENVIRONMENT, validValue)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Set admin caller
        exchange.getAttributes().put("tokenDomainId", GlobalAdminConstants.GLOBAL_DOMAIN);
        exchange.getAttributes().put("tokenSolutionId", GlobalAdminConstants.GLOBAL_SOLUTION);
        exchange.getAttributes().put("environmentId", UUID.randomUUID());
        exchange.getAttributes().put("solutionId", solutionId.toString());

        // Mock successful lookup
        EnvironmentLookupResponse target = new EnvironmentLookupResponse(
                targetId, "BrokerServices", "broker-services", solutionId);
        when(mockClient.lookupEnvironmentByFingerprint(any(), any(), any())).thenReturn(Mono.just(target));
        when(mockClient.lookupEnvironmentById(any())).thenReturn(Mono.just(target));

        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // --- Act ---
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // --- Assert ---
        // Must NOT produce 400 (may produce other statuses if downstream validation fails, but not 400)
        assertThat(exchange.getResponse().getStatusCode())
                .as("Valid header value must not produce 400 BAD_REQUEST")
                .isNotEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ========== Property 4: Target Not Found Produces 422 ==========

    /**
     * Property 4: Target Not Found Produces 422.
     *
     * <p>For any valid header value from admin where lookup returns empty,
     * filter returns 422 PROXY_002.</p>
     *
     * <p><b>Validates: Requirements 3.3</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: admin-proxy-impersonation, Property 4: Target Not Found Produces 422")
    void targetNotFoundProduces422(
            @ForAll("validHeaderValues") String validValue) {

        // --- Arrange ---
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        ProxyImpersonationFilter filter = new ProxyImpersonationFilter(mockClient, objectMapper);

        UUID solutionId = UUID.randomUUID();
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/{domainId}/{solutionId}/queues",
                        UUID.randomUUID().toString(), solutionId.toString())
                .header(SifHeaders.X_RUN_AS_ENVIRONMENT, validValue)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Set admin caller
        exchange.getAttributes().put("tokenDomainId", GlobalAdminConstants.GLOBAL_DOMAIN);
        exchange.getAttributes().put("tokenSolutionId", GlobalAdminConstants.GLOBAL_SOLUTION);
        exchange.getAttributes().put("environmentId", UUID.randomUUID());
        exchange.getAttributes().put("solutionId", solutionId.toString());

        // Mock lookup returns empty (not found)
        when(mockClient.lookupEnvironmentByFingerprint(any(), any(), any())).thenReturn(Mono.empty());
        when(mockClient.lookupEnvironmentById(any())).thenReturn(Mono.empty());

        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // --- Act ---
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // --- Assert ---
        assertThat(exchange.getResponse().getStatusCode())
                .as("Target not found must produce 422 UNPROCESSABLE_ENTITY")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Chain must NOT be called
        verify(mockChain, never()).filter(any(ServerWebExchange.class));
    }

    // ========== Property 5: Fingerprint Validation Gate ==========

    /**
     * Property 5: Fingerprint Validation Gate.
     *
     * <p>For any resolved target, filter accepts iff fingerprint equals "broker-services";
     * otherwise → 403 PROXY_003.</p>
     *
     * <p><b>Validates: Requirements 4.1, 4.2</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: admin-proxy-impersonation, Property 5: Fingerprint Validation Gate")
    void nonBrokerServicesFingerprintProduces403(
            @ForAll("nonBrokerFingerprints") String fingerprint) {

        // --- Arrange ---
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        ProxyImpersonationFilter filter = new ProxyImpersonationFilter(mockClient, objectMapper);

        UUID solutionId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/{domainId}/{solutionId}/queues",
                        UUID.randomUUID().toString(), solutionId.toString())
                .header(SifHeaders.X_RUN_AS_ENVIRONMENT, targetId.toString())
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Set admin caller
        exchange.getAttributes().put("tokenDomainId", GlobalAdminConstants.GLOBAL_DOMAIN);
        exchange.getAttributes().put("tokenSolutionId", GlobalAdminConstants.GLOBAL_SOLUTION);
        exchange.getAttributes().put("environmentId", UUID.randomUUID());
        exchange.getAttributes().put("solutionId", solutionId.toString());

        // Mock lookup returns target with non-broker-services fingerprint
        EnvironmentLookupResponse target = new EnvironmentLookupResponse(
                targetId, "SomeApp", fingerprint, solutionId);
        when(mockClient.lookupEnvironmentById(any())).thenReturn(Mono.just(target));

        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // --- Act ---
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // --- Assert ---
        assertThat(exchange.getResponse().getStatusCode())
                .as("Non-broker-services fingerprint must produce 403")
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(mockChain, never()).filter(any(ServerWebExchange.class));
    }

    /**
     * Property 5 (positive variant): broker-services fingerprint passes validation.
     *
     * <p><b>Validates: Requirements 4.1, 4.2</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: admin-proxy-impersonation, Property 5: broker-services fingerprint passes")
    void brokerServicesFingerprintPassesValidation(
            @ForAll("applicationNames") String appName) {

        // --- Arrange ---
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        ProxyImpersonationFilter filter = new ProxyImpersonationFilter(mockClient, objectMapper);

        UUID solutionId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/{domainId}/{solutionId}/queues",
                        UUID.randomUUID().toString(), solutionId.toString())
                .header(SifHeaders.X_RUN_AS_ENVIRONMENT, targetId.toString())
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Set admin caller
        exchange.getAttributes().put("tokenDomainId", GlobalAdminConstants.GLOBAL_DOMAIN);
        exchange.getAttributes().put("tokenSolutionId", GlobalAdminConstants.GLOBAL_SOLUTION);
        exchange.getAttributes().put("environmentId", UUID.randomUUID());
        exchange.getAttributes().put("solutionId", solutionId.toString());

        // Mock lookup returns target with broker-services fingerprint AND matching solution
        EnvironmentLookupResponse target = new EnvironmentLookupResponse(
                targetId, appName, "broker-services", solutionId);
        when(mockClient.lookupEnvironmentById(any())).thenReturn(Mono.just(target));

        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // --- Act ---
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // --- Assert ---
        // Must NOT produce 403 for fingerprint mismatch
        assertThat(exchange.getResponse().getStatusCode())
                .as("broker-services fingerprint must not produce 403")
                .isNotEqualTo(HttpStatus.FORBIDDEN);

        // Chain must be called (impersonation succeeds)
        verify(mockChain).filter(any(ServerWebExchange.class));
    }

    // ========== Property 6: Solution Membership Validation ==========

    /**
     * Property 6: Solution Membership Validation.
     *
     * <p>For any target passing fingerprint check, filter accepts iff solutionUuid matches
     * URL path; otherwise → 403 PROXY_004.</p>
     *
     * <p><b>Validates: Requirements 4.3, 4.4</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: admin-proxy-impersonation, Property 6: Solution Membership Validation")
    void solutionMismatchProduces403(
            @ForAll("applicationNames") String appName) {

        // --- Arrange ---
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        ProxyImpersonationFilter filter = new ProxyImpersonationFilter(mockClient, objectMapper);

        UUID pathSolutionId = UUID.randomUUID();
        UUID targetSolutionId = UUID.randomUUID(); // Different from path
        UUID targetId = UUID.randomUUID();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/{domainId}/{solutionId}/queues",
                        UUID.randomUUID().toString(), pathSolutionId.toString())
                .header(SifHeaders.X_RUN_AS_ENVIRONMENT, targetId.toString())
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Set admin caller
        exchange.getAttributes().put("tokenDomainId", GlobalAdminConstants.GLOBAL_DOMAIN);
        exchange.getAttributes().put("tokenSolutionId", GlobalAdminConstants.GLOBAL_SOLUTION);
        exchange.getAttributes().put("environmentId", UUID.randomUUID());
        exchange.getAttributes().put("solutionId", pathSolutionId.toString());

        // Mock lookup returns target with broker-services fingerprint but DIFFERENT solution
        EnvironmentLookupResponse target = new EnvironmentLookupResponse(
                targetId, appName, "broker-services", targetSolutionId);
        when(mockClient.lookupEnvironmentById(any())).thenReturn(Mono.just(target));

        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // --- Act ---
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // --- Assert ---
        assertThat(exchange.getResponse().getStatusCode())
                .as("Solution mismatch must produce 403")
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(mockChain, never()).filter(any(ServerWebExchange.class));
    }

    // ========== Property 7: Successful Impersonation Context Override ==========

    /**
     * Property 7: Successful Impersonation Context Override.
     *
     * <p>For any fully valid impersonation, environmentId is set to target UUID
     * and environmentName to target applicationName.</p>
     *
     * <p><b>Validates: Requirements 5.1, 5.2</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: admin-proxy-impersonation, Property 7: Successful Impersonation Context Override")
    void successfulImpersonationOverridesContext(
            @ForAll("applicationNames") String targetAppName) {

        // --- Arrange ---
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        ProxyImpersonationFilter filter = new ProxyImpersonationFilter(mockClient, objectMapper);

        UUID solutionId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID adminEnvId = UUID.randomUUID();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/{domainId}/{solutionId}/queues",
                        UUID.randomUUID().toString(), solutionId.toString())
                .header(SifHeaders.X_RUN_AS_ENVIRONMENT, targetId.toString())
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Set admin caller
        exchange.getAttributes().put("tokenDomainId", GlobalAdminConstants.GLOBAL_DOMAIN);
        exchange.getAttributes().put("tokenSolutionId", GlobalAdminConstants.GLOBAL_SOLUTION);
        exchange.getAttributes().put("environmentId", adminEnvId);
        exchange.getAttributes().put("environmentName", "AdminApp");
        exchange.getAttributes().put("solutionId", solutionId.toString());

        // Mock lookup returns valid target (broker-services fingerprint, matching solution)
        EnvironmentLookupResponse target = new EnvironmentLookupResponse(
                targetId, targetAppName, "broker-services", solutionId);
        when(mockClient.lookupEnvironmentById(any())).thenReturn(Mono.just(target));

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
        verify(mockChain).filter(any(ServerWebExchange.class));

        // The exchange passed to chain must have overridden attributes
        ServerWebExchange forwarded = capturedExchange.get();
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getAttributes().get("environmentId"))
                .as("environmentId must be overridden to target UUID")
                .isEqualTo(targetId);
        assertThat(forwarded.getAttributes().get("environmentName"))
                .as("environmentName must be overridden to target applicationName")
                .isEqualTo(targetAppName);
    }

    // ========== Property 8: Correlation ID Preservation in Error Responses ==========

    /**
     * Property 8: Correlation ID Preservation in Error Responses.
     *
     * <p>For any error response (400, 403, 422, 503), if request had X-Correlation-Id,
     * it appears in response headers.</p>
     *
     * <p><b>Validates: Requirements 9.3</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: admin-proxy-impersonation, Property 8: Correlation ID Preservation in Error Responses")
    void correlationIdPreservedInErrorResponses(
            @ForAll("correlationIds") String correlationId,
            @ForAll("errorScenarios") ErrorScenario scenario) {

        // --- Arrange ---
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        ProxyImpersonationFilter filter = new ProxyImpersonationFilter(mockClient, objectMapper);

        UUID solutionId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        String headerValue = scenario == ErrorScenario.INVALID_HEADER
                ? "not-a-uuid-and-not-broker-services"
                : targetId.toString();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/{domainId}/{solutionId}/queues",
                        UUID.randomUUID().toString(), solutionId.toString())
                .header(SifHeaders.X_RUN_AS_ENVIRONMENT, headerValue)
                .header(SifHeaders.X_CORRELATION_ID, correlationId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Set admin caller
        exchange.getAttributes().put("tokenDomainId", GlobalAdminConstants.GLOBAL_DOMAIN);
        exchange.getAttributes().put("tokenSolutionId", GlobalAdminConstants.GLOBAL_SOLUTION);
        exchange.getAttributes().put("environmentId", UUID.randomUUID());
        exchange.getAttributes().put("solutionId", solutionId.toString());

        // Configure mock based on scenario
        switch (scenario) {
            case INVALID_HEADER:
                // No mock needed — filter rejects before calling client
                break;
            case TARGET_NOT_FOUND:
                when(mockClient.lookupEnvironmentById(any())).thenReturn(Mono.empty());
                break;
            case FINGERPRINT_MISMATCH:
                EnvironmentLookupResponse badFingerprint = new EnvironmentLookupResponse(
                        targetId, "App", "not-broker-services", solutionId);
                when(mockClient.lookupEnvironmentById(any())).thenReturn(Mono.just(badFingerprint));
                break;
            case SOLUTION_MISMATCH:
                EnvironmentLookupResponse badSolution = new EnvironmentLookupResponse(
                        targetId, "App", "broker-services", UUID.randomUUID());
                when(mockClient.lookupEnvironmentById(any())).thenReturn(Mono.just(badSolution));
                break;
            case SERVICE_UNAVAILABLE:
                when(mockClient.lookupEnvironmentById(any())).thenReturn(
                        Mono.error(new ServiceUnavailableException("Environment Service", "timeout")));
                break;
        }

        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // --- Act ---
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // --- Assert ---
        // Must be an error response
        HttpStatus status = (HttpStatus) exchange.getResponse().getStatusCode();
        assertThat(status).isNotNull();
        assertThat(status.isError())
                .as("Scenario %s must produce an error response", scenario)
                .isTrue();

        // Correlation ID must be preserved
        String responseCorrelationId = exchange.getResponse().getHeaders()
                .getFirst(SifHeaders.X_CORRELATION_ID);
        assertThat(responseCorrelationId)
                .as("Correlation ID must be preserved in error response for scenario %s", scenario)
                .isEqualTo(correlationId);
    }

    // ========== Property 9: No Unhandled Exceptions ==========

    /**
     * Property 9: No Unhandled Exceptions.
     *
     * <p>For any input (malformed headers, null attributes, service timeouts, unexpected formats),
     * filter returns Mono that completes chain or writes structured error — never propagates
     * unhandled exception.</p>
     *
     * <p><b>Validates: Requirements 9.4</b></p>
     */
    @Property(tries = 100)
    @Label("Feature: admin-proxy-impersonation, Property 9: No Unhandled Exceptions")
    void filterNeverPropagatesUnhandledException(
            @ForAll("chaosHeaderValues") String headerValue,
            @ForAll("chaosScenarios") ChaosScenario chaos) {

        // --- Arrange ---
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        ProxyImpersonationFilter filter = new ProxyImpersonationFilter(mockClient, objectMapper);

        UUID solutionId = UUID.randomUUID();

        MockServerHttpRequest.BaseBuilder<?> requestBuilder = MockServerHttpRequest
                .get("/{domainId}/{solutionId}/queues",
                        UUID.randomUUID().toString(), solutionId.toString());

        if (headerValue != null) {
            requestBuilder.header(SifHeaders.X_RUN_AS_ENVIRONMENT, headerValue);
        }

        MockServerWebExchange exchange = MockServerWebExchange.from(requestBuilder.build());

        // Set attributes based on chaos scenario
        switch (chaos) {
            case NULL_ATTRIBUTES:
                // Don't set any attributes — tokenDomainId, tokenSolutionId will be null
                break;
            case ADMIN_WITH_NULL_SOLUTION_ID:
                exchange.getAttributes().put("tokenDomainId", GlobalAdminConstants.GLOBAL_DOMAIN);
                exchange.getAttributes().put("tokenSolutionId", GlobalAdminConstants.GLOBAL_SOLUTION);
                exchange.getAttributes().put("environmentId", UUID.randomUUID());
                // solutionId attribute intentionally null
                break;
            case SERVICE_THROWS_RUNTIME:
                exchange.getAttributes().put("tokenDomainId", GlobalAdminConstants.GLOBAL_DOMAIN);
                exchange.getAttributes().put("tokenSolutionId", GlobalAdminConstants.GLOBAL_SOLUTION);
                exchange.getAttributes().put("environmentId", UUID.randomUUID());
                exchange.getAttributes().put("solutionId", solutionId.toString());
                when(mockClient.lookupEnvironmentById(any()))
                        .thenReturn(Mono.error(new RuntimeException("Unexpected failure")));
                when(mockClient.lookupEnvironmentByFingerprint(any(), any(), any()))
                        .thenReturn(Mono.error(new RuntimeException("Unexpected failure")));
                break;
            case SERVICE_TIMEOUT:
                exchange.getAttributes().put("tokenDomainId", GlobalAdminConstants.GLOBAL_DOMAIN);
                exchange.getAttributes().put("tokenSolutionId", GlobalAdminConstants.GLOBAL_SOLUTION);
                exchange.getAttributes().put("environmentId", UUID.randomUUID());
                exchange.getAttributes().put("solutionId", solutionId.toString());
                when(mockClient.lookupEnvironmentById(any()))
                        .thenReturn(Mono.error(new ServiceUnavailableException("Environment Service", "timeout")));
                when(mockClient.lookupEnvironmentByFingerprint(any(), any(), any()))
                        .thenReturn(Mono.error(new ServiceUnavailableException("Environment Service", "timeout")));
                break;
            case NORMAL_ADMIN:
                exchange.getAttributes().put("tokenDomainId", GlobalAdminConstants.GLOBAL_DOMAIN);
                exchange.getAttributes().put("tokenSolutionId", GlobalAdminConstants.GLOBAL_SOLUTION);
                exchange.getAttributes().put("environmentId", UUID.randomUUID());
                exchange.getAttributes().put("solutionId", solutionId.toString());
                when(mockClient.lookupEnvironmentById(any())).thenReturn(Mono.empty());
                when(mockClient.lookupEnvironmentByFingerprint(any(), any(), any())).thenReturn(Mono.empty());
                break;
        }

        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // --- Act & Assert ---
        // The filter must NEVER propagate an unhandled exception
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // If an error response was written, it must have a valid HTTP status
        HttpStatus status = (HttpStatus) exchange.getResponse().getStatusCode();
        if (status != null && status.isError()) {
            assertThat(status.value())
                    .as("Error responses must be structured (400, 403, 422, or 503)")
                    .isIn(400, 403, 422, 503);
        }
    }

    // ========== Enums ==========

    enum CallerType {
        ADMIN, NON_ADMIN
    }

    enum ErrorScenario {
        INVALID_HEADER,
        TARGET_NOT_FOUND,
        FINGERPRINT_MISMATCH,
        SOLUTION_MISMATCH,
        SERVICE_UNAVAILABLE
    }

    enum ChaosScenario {
        NULL_ATTRIBUTES,
        ADMIN_WITH_NULL_SOLUTION_ID,
        SERVICE_THROWS_RUNTIME,
        SERVICE_TIMEOUT,
        NORMAL_ADMIN
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> headerValues() {
        return Arbitraries.oneOf(
                Arbitraries.just((String) null),
                Arbitraries.just(""),
                Arbitraries.just("   "),
                Arbitraries.just("broker-services"),
                Arbitraries.create(() -> UUID.randomUUID().toString()),
                Arbitraries.of("invalid", "not-a-uuid", "BROKER-SERVICES", "123")
        );
    }

    @Provide
    Arbitrary<CallerType> callerTypes() {
        return Arbitraries.of(CallerType.values());
    }

    @Provide
    Arbitrary<String> anyHeaderValues() {
        return Arbitraries.oneOf(
                Arbitraries.just("broker-services"),
                Arbitraries.create(() -> UUID.randomUUID().toString()),
                Arbitraries.of("invalid-value", "not-a-uuid", "random-string", "BROKER-SERVICES")
        );
    }

    @Provide
    Arbitrary<String> nonAdminDomainIds() {
        return Arbitraries.oneOf(
                Arbitraries.create(() -> UUID.randomUUID().toString()),
                Arbitraries.of("SOME_DOMAIN", "other-domain", "test-domain")
        );
    }

    @Provide
    Arbitrary<String> nonAdminSolutionIds() {
        return Arbitraries.oneOf(
                Arbitraries.create(() -> UUID.randomUUID().toString()),
                Arbitraries.of("SOME_SOLUTION", "other-solution", "test-solution")
        );
    }

    @Provide
    Arbitrary<String> invalidHeaderValues() {
        // Note: values with leading/trailing whitespace around "broker-services" are NOT invalid
        // because the filter trims the header value before validation.
        return Arbitraries.of(
                "not-a-uuid",
                "invalid",
                "BROKER-SERVICES",
                "Broker-Services",
                "broker_services",
                "123-456",
                "abc",
                "broker-service",
                "broker-services-extra",
                "null",
                "undefined",
                "<script>alert(1)</script>",
                "'; DROP TABLE environments; --"
        );
    }

    @Provide
    Arbitrary<String> validHeaderValues() {
        return Arbitraries.oneOf(
                Arbitraries.just("broker-services"),
                Arbitraries.create(() -> UUID.randomUUID().toString())
        );
    }

    @Provide
    Arbitrary<String> nonBrokerFingerprints() {
        return Arbitraries.of(
                "student-info",
                "lms-connector",
                "assessment-provider",
                "admin-console",
                "data-warehouse",
                "reporting-engine",
                "custom-app",
                "BROKER-SERVICES",
                "Broker-Services"
        );
    }

    @Provide
    Arbitrary<String> applicationNames() {
        return Arbitraries.of(
                "BROKER_SERVICES_app",
                "TestApp",
                "AdminConsole",
                "StudentInfoSystem",
                "LMS_Integration",
                "DataWarehouse",
                "ReportingEngine"
        );
    }

    @Provide
    Arbitrary<String> correlationIds() {
        return Arbitraries.oneOf(
                Arbitraries.create(() -> UUID.randomUUID().toString()),
                Arbitraries.of("corr-123", "request-abc", "trace-xyz-456")
        );
    }

    @Provide
    Arbitrary<ErrorScenario> errorScenarios() {
        return Arbitraries.of(ErrorScenario.values());
    }

    @Provide
    Arbitrary<String> chaosHeaderValues() {
        return Arbitraries.oneOf(
                Arbitraries.just((String) null),
                Arbitraries.just(""),
                Arbitraries.just("broker-services"),
                Arbitraries.create(() -> UUID.randomUUID().toString()),
                Arbitraries.of("invalid", "not-a-uuid", "\u0000\u0001\u0002", "a".repeat(1000))
        );
    }

    @Provide
    Arbitrary<ChaosScenario> chaosScenarios() {
        return Arbitraries.of(ChaosScenario.values());
    }
}
