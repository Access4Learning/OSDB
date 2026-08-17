// SPDX-License-Identifier: MIT
package org.sifunity.gateway.filter.unit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.EnvironmentLookupResponse;
import org.sifunity.broker.common.exception.ServiceUnavailableException;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import org.sifunity.gateway.filter.ProxyImpersonationFilter;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProxyImpersonationFilter.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Filter order verification (getOrder() == 5)</li>
 *   <li>"broker-services" literal triggers fingerprint lookup (not UUID lookup)</li>
 *   <li>UUID value triggers ID lookup (not fingerprint lookup)</li>
 *   <li>Audit logging: INFO on success, WARN on rejection, DEBUG on non-admin ignore</li>
 *   <li>Service unavailability produces 503 with PROXY_001</li>
 *   <li>All error codes: PROXY_001 through PROXY_005</li>
 * </ul>
 *
 * <p>Requirements: 1.1, 1.2, 1.3, 2.1, 3.1, 3.2, 3.3, 4.1, 4.2, 4.3, 4.4,
 * 5.1, 5.2, 6.1, 8.1, 8.2, 8.3, 9.1, 9.2, 9.3, 9.4</p>
 */
@DisplayName("ProxyImpersonationFilter Unit Tests")
class ProxyImpersonationFilterTest {

    private EnvironmentServiceClient mockClient;
    private ProxyImpersonationFilter filter;
    private GatewayFilterChain mockChain;
    private ObjectMapper objectMapper;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger filterLogger;

    private static final UUID ADMIN_ENV_ID = UUID.randomUUID();
    private static final UUID TARGET_ENV_ID = UUID.randomUUID();
    private static final UUID SOLUTION_ID = UUID.randomUUID();
    private static final String DOMAIN_ID = "d1";
    private static final String TARGET_APP_NAME = "internal-broker-services-" + SOLUTION_ID;

    @BeforeEach
    void setUp() {
        mockClient = mock(EnvironmentServiceClient.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        filter = new ProxyImpersonationFilter(mockClient, objectMapper);

        mockChain = mock(GatewayFilterChain.class);
        when(mockChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // Set up log capture
        filterLogger = (Logger) LoggerFactory.getLogger(ProxyImpersonationFilter.class);
        filterLogger.setLevel(Level.DEBUG);
        logAppender = new ListAppender<>();
        logAppender.start();
        filterLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    /**
     * Builds a MockServerWebExchange with admin attributes and the given header value.
     */
    private MockServerWebExchange buildAdminExchange(String runAsHeaderValue) {
        MockServerHttpRequest.BaseBuilder<?> requestBuilder = MockServerHttpRequest
                .get("/" + SOLUTION_ID + "/queues");

        if (runAsHeaderValue != null) {
            requestBuilder.header(SifHeaders.X_RUN_AS_ENVIRONMENT, runAsHeaderValue);
        }

        MockServerWebExchange exchange = MockServerWebExchange.from(
                (MockServerHttpRequest) requestBuilder.build());

        // Set admin attributes
        exchange.getAttributes().put("tokenDomainId", GlobalAdminConstants.GLOBAL_DOMAIN);
        exchange.getAttributes().put("tokenSolutionId", GlobalAdminConstants.GLOBAL_SOLUTION);
        exchange.getAttributes().put("environmentId", ADMIN_ENV_ID);
        exchange.getAttributes().put("environmentName", "AdminApp");
        exchange.getAttributes().put("domainId", DOMAIN_ID);
        exchange.getAttributes().put("solutionId", SOLUTION_ID.toString());

        return exchange;
    }

    /**
     * Builds a MockServerWebExchange for a non-admin caller.
     */
    private MockServerWebExchange buildNonAdminExchange(String runAsHeaderValue) {
        MockServerHttpRequest.BaseBuilder<?> requestBuilder = MockServerHttpRequest
                .get("/" + SOLUTION_ID + "/queues");

        if (runAsHeaderValue != null) {
            requestBuilder.header(SifHeaders.X_RUN_AS_ENVIRONMENT, runAsHeaderValue);
        }

        MockServerWebExchange exchange = MockServerWebExchange.from(
                (MockServerHttpRequest) requestBuilder.build());

        // Set non-admin attributes
        exchange.getAttributes().put("tokenDomainId", "some-domain");
        exchange.getAttributes().put("tokenSolutionId", "some-solution");
        exchange.getAttributes().put("environmentId", UUID.randomUUID());
        exchange.getAttributes().put("environmentName", "RegularApp");
        exchange.getAttributes().put("domainId", DOMAIN_ID);
        exchange.getAttributes().put("solutionId", SOLUTION_ID.toString());

        return exchange;
    }

    private EnvironmentLookupResponse buildValidTarget() {
        return new EnvironmentLookupResponse(
                TARGET_ENV_ID, TARGET_APP_NAME, "broker-services", SOLUTION_ID);
    }

    private String extractResponseBody(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString()
                .block();
    }

    // ========== Filter Order ==========

    @Test
    @DisplayName("getOrder() returns 6 — after MembershipValidationFilter, before EnvironmentContextFilter")
    void filterOrder_isFive() {
        assertThat(filter.getOrder()).isEqualTo(6);
    }

    // ========== Lookup Routing ==========

    @Nested
    @DisplayName("Target Resolution Routing")
    class TargetResolutionRouting {

        @Test
        @DisplayName("'broker-services' literal triggers fingerprint lookup, not UUID lookup")
        void brokerServicesLiteral_triggersFingerPrintLookup() {
            // Arrange
            EnvironmentLookupResponse target = buildValidTarget();
            when(mockClient.lookupEnvironmentByFingerprint(eq("broker-services"), eq(DOMAIN_ID), eq(SOLUTION_ID.toString())))
                    .thenReturn(Mono.just(target));

            MockServerWebExchange exchange = buildAdminExchange("broker-services");

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                    .verifyComplete();

            // Assert — fingerprint lookup called, ID lookup NOT called
            verify(mockClient).lookupEnvironmentByFingerprint("broker-services", DOMAIN_ID, SOLUTION_ID.toString());
            verify(mockClient, never()).lookupEnvironmentById(any(UUID.class));
        }

        @Test
        @DisplayName("UUID value triggers ID lookup, not fingerprint lookup")
        void uuidValue_triggersIdLookup() {
            // Arrange
            UUID targetId = UUID.randomUUID();
            EnvironmentLookupResponse target = new EnvironmentLookupResponse(
                    targetId, TARGET_APP_NAME, "broker-services", SOLUTION_ID);
            when(mockClient.lookupEnvironmentById(eq(targetId)))
                    .thenReturn(Mono.just(target));

            MockServerWebExchange exchange = buildAdminExchange(targetId.toString());

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                    .verifyComplete();

            // Assert — ID lookup called, fingerprint lookup NOT called
            verify(mockClient).lookupEnvironmentById(targetId);
            verify(mockClient, never()).lookupEnvironmentByFingerprint(any(), any(), any());
        }
    }

    // ========== Audit Logging ==========

    @Nested
    @DisplayName("Audit Logging")
    class AuditLogging {

        @Test
        @DisplayName("INFO log emitted on successful impersonation")
        void successfulImpersonation_emitsInfoLog() {
            // Arrange
            EnvironmentLookupResponse target = buildValidTarget();
            when(mockClient.lookupEnvironmentByFingerprint(eq("broker-services"), eq(DOMAIN_ID), eq(SOLUTION_ID.toString())))
                    .thenReturn(Mono.just(target));

            MockServerWebExchange exchange = buildAdminExchange("broker-services");

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                    .verifyComplete();

            // Assert — INFO log with admin UUID, target UUID, target name, path
            assertThat(logAppender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.INFO);
                assertThat(event.getFormattedMessage())
                        .contains("Proxy impersonation applied")
                        .contains(ADMIN_ENV_ID.toString())
                        .contains(TARGET_ENV_ID.toString())
                        .contains(TARGET_APP_NAME);
            });
        }

        @Test
        @DisplayName("WARN log emitted on rejection (target not found)")
        void targetNotFound_emitsWarnLog() {
            // Arrange
            when(mockClient.lookupEnvironmentByFingerprint(eq("broker-services"), eq(DOMAIN_ID), eq(SOLUTION_ID.toString())))
                    .thenReturn(Mono.empty());

            MockServerWebExchange exchange = buildAdminExchange("broker-services");

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                    .verifyComplete();

            // Assert — WARN log emitted
            assertThat(logAppender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage())
                        .contains("Target environment not found");
            });
        }

        @Test
        @DisplayName("WARN log emitted on rejection (invalid header value)")
        void invalidHeader_emitsWarnLog() {
            // Arrange
            MockServerWebExchange exchange = buildAdminExchange("not-a-uuid-and-not-broker-services");

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                    .verifyComplete();

            // Assert — WARN log emitted
            assertThat(logAppender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage())
                        .contains("Invalid X-Run-As-Environment header value");
            });
        }

        @Test
        @DisplayName("WARN log emitted on rejection (fingerprint mismatch)")
        void fingerprintMismatch_emitsWarnLog() {
            // Arrange
            UUID targetId = UUID.randomUUID();
            EnvironmentLookupResponse target = new EnvironmentLookupResponse(
                    targetId, "SomeApp", "not-broker-services", SOLUTION_ID);
            when(mockClient.lookupEnvironmentById(eq(targetId)))
                    .thenReturn(Mono.just(target));

            MockServerWebExchange exchange = buildAdminExchange(targetId.toString());

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                    .verifyComplete();

            // Assert — WARN log about fingerprint rejection
            assertThat(logAppender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage())
                        .contains("Impersonation rejected")
                        .contains("not broker-services");
            });
        }

        @Test
        @DisplayName("WARN log emitted when non-admin caller has header (ignored)")
        void nonAdminCaller_emitsWarnLog() {
            // Arrange
            MockServerWebExchange exchange = buildNonAdminExchange("broker-services");

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                    .verifyComplete();

            // Assert — WARN, not DEBUG. The pass-through itself is intentional (see the filter),
            // but it must be visible in production: DEBUG is disabled there, so an ignored
            // run-as header was previously invisible while being the most likely explanation
            // for "my impersonated request was rejected".
            assertThat(logAppender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage())
                        .contains("X-Run-As-Environment ignored: caller is not a global admin");
            });
        }
    }

    // ========== Error Codes ==========

    @Nested
    @DisplayName("Error Codes")
    class ErrorCodes {

        @Test
        @DisplayName("PROXY_001: Service unavailability produces 503")
        void proxy001_serviceUnavailable() {
            // Arrange
            when(mockClient.lookupEnvironmentByFingerprint(eq("broker-services"), eq(DOMAIN_ID), eq(SOLUTION_ID.toString())))
                    .thenReturn(Mono.error(new ServiceUnavailableException(
                            "environment-service",
                            "Environment lookup by fingerprint service unavailable")));

            MockServerWebExchange exchange = buildAdminExchange("broker-services");

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                    .verifyComplete();

            // Assert
            assertThat(exchange.getResponse().getStatusCode())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            String body = extractResponseBody(exchange);
            assertThat(body).contains("PROXY_001");
            verify(mockChain, never()).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("PROXY_002: Target not found produces 422")
        void proxy002_targetNotFound() {
            // Arrange
            when(mockClient.lookupEnvironmentByFingerprint(eq("broker-services"), eq(DOMAIN_ID), eq(SOLUTION_ID.toString())))
                    .thenReturn(Mono.empty());

            MockServerWebExchange exchange = buildAdminExchange("broker-services");

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                    .verifyComplete();

            // Assert
            assertThat(exchange.getResponse().getStatusCode())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            String body = extractResponseBody(exchange);
            assertThat(body).contains("PROXY_002");
            verify(mockChain, never()).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("PROXY_003: Target not broker-services produces 403")
        void proxy003_notBrokerServices() {
            // Arrange
            UUID targetId = UUID.randomUUID();
            EnvironmentLookupResponse target = new EnvironmentLookupResponse(
                    targetId, "RegularApp", "student-info-system", SOLUTION_ID);
            when(mockClient.lookupEnvironmentById(eq(targetId)))
                    .thenReturn(Mono.just(target));

            MockServerWebExchange exchange = buildAdminExchange(targetId.toString());

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                    .verifyComplete();

            // Assert
            assertThat(exchange.getResponse().getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            String body = extractResponseBody(exchange);
            assertThat(body).contains("PROXY_003");
            verify(mockChain, never()).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("PROXY_004: Target solution mismatch produces 403")
        void proxy004_solutionMismatch() {
            // Arrange
            UUID targetId = UUID.randomUUID();
            UUID differentSolution = UUID.randomUUID();
            EnvironmentLookupResponse target = new EnvironmentLookupResponse(
                    targetId, "BrokerSvc", "broker-services", differentSolution);
            when(mockClient.lookupEnvironmentById(eq(targetId)))
                    .thenReturn(Mono.just(target));

            MockServerWebExchange exchange = buildAdminExchange(targetId.toString());

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                    .verifyComplete();

            // Assert
            assertThat(exchange.getResponse().getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            String body = extractResponseBody(exchange);
            assertThat(body).contains("PROXY_004");
            verify(mockChain, never()).filter(any(ServerWebExchange.class));
        }

        @Test
        @DisplayName("PROXY_005: Invalid header value produces 400")
        void proxy005_invalidHeaderValue() {
            // Arrange
            MockServerWebExchange exchange = buildAdminExchange("not-a-uuid-and-not-broker-services");

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                    .verifyComplete();

            // Assert
            assertThat(exchange.getResponse().getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            String body = extractResponseBody(exchange);
            assertThat(body).contains("PROXY_005");
            verify(mockChain, never()).filter(any(ServerWebExchange.class));
        }
    }

    // ========== Successful Impersonation ==========

    @Nested
    @DisplayName("Successful Impersonation")
    class SuccessfulImpersonation {

        @Test
        @DisplayName("Overrides environmentId and environmentName on success")
        void successfulImpersonation_overridesAttributes() {
            // Arrange
            EnvironmentLookupResponse target = buildValidTarget();
            when(mockClient.lookupEnvironmentByFingerprint(eq("broker-services"), eq(DOMAIN_ID), eq(SOLUTION_ID.toString())))
                    .thenReturn(Mono.just(target));

            MockServerWebExchange exchange = buildAdminExchange("broker-services");

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                    .verifyComplete();

            // Assert — chain was called (impersonation succeeded)
            verify(mockChain).filter(any(ServerWebExchange.class));
            // Attributes overridden
            assertThat((UUID) exchange.getAttribute("environmentId")).isEqualTo(TARGET_ENV_ID);
            assertThat((String) exchange.getAttribute("environmentName")).isEqualTo(TARGET_APP_NAME);
        }
    }

    // ========== Correlation ID Preservation ==========

    @Nested
    @DisplayName("Correlation ID Preservation")
    class CorrelationIdPreservation {

        @Test
        @DisplayName("Error response preserves X-Correlation-Id from request")
        void errorResponse_preservesCorrelationId() {
            // Arrange
            String correlationId = "test-correlation-" + UUID.randomUUID();
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/" + SOLUTION_ID + "/queues")
                    .header(SifHeaders.X_RUN_AS_ENVIRONMENT, "invalid-value!!!")
                    .header(SifHeaders.X_CORRELATION_ID, correlationId)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            exchange.getAttributes().put("tokenDomainId", GlobalAdminConstants.GLOBAL_DOMAIN);
            exchange.getAttributes().put("tokenSolutionId", GlobalAdminConstants.GLOBAL_SOLUTION);
            exchange.getAttributes().put("environmentId", ADMIN_ENV_ID);
            exchange.getAttributes().put("solutionId", SOLUTION_ID.toString());

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                    .verifyComplete();

            // Assert — correlation ID in response headers
            assertThat(exchange.getResponse().getHeaders().getFirst(SifHeaders.X_CORRELATION_ID))
                    .isEqualTo(correlationId);
        }
    }

    // ========== Non-Admin Bypass ==========

    @Nested
    @DisplayName("Non-Admin Bypass")
    class NonAdminBypass {

        @Test
        @DisplayName("Non-admin caller: header ignored, chain proceeds, no error")
        void nonAdmin_headerIgnored_chainProceeds() {
            // Arrange
            MockServerWebExchange exchange = buildNonAdminExchange("broker-services");

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                    .verifyComplete();

            // Assert — chain called, no client lookups, no error
            verify(mockChain).filter(any(ServerWebExchange.class));
            verify(mockClient, never()).lookupEnvironmentByFingerprint(any(), any(), any());
            verify(mockClient, never()).lookupEnvironmentById(any());
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
    }

    // ========== Header Stripping ==========

    @Nested
    @DisplayName("Header Stripping")
    class HeaderStripping {

        @Test
        @DisplayName("X-Run-As-Environment header is stripped from forwarded request")
        void headerStripped_fromForwardedRequest() {
            // Arrange
            EnvironmentLookupResponse target = buildValidTarget();
            when(mockClient.lookupEnvironmentByFingerprint(eq("broker-services"), eq(DOMAIN_ID), eq(SOLUTION_ID.toString())))
                    .thenReturn(Mono.just(target));

            MockServerWebExchange exchange = buildAdminExchange("broker-services");

            // Capture the exchange passed to chain
            when(mockChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
                ServerWebExchange forwardedExchange = invocation.getArgument(0);
                // Verify header is stripped
                assertThat(forwardedExchange.getRequest().getHeaders()
                        .getFirst(SifHeaders.X_RUN_AS_ENVIRONMENT)).isNull();
                return Mono.empty();
            });

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                    .verifyComplete();

            // Assert — chain was called
            verify(mockChain).filter(any(ServerWebExchange.class));
        }
    }

    // ========== No Unhandled Exceptions ==========

    @Nested
    @DisplayName("No Unhandled Exceptions")
    class NoUnhandledExceptions {

        @Test
        @DisplayName("Unexpected exception from client produces 503, not 500")
        void unexpectedException_produces503() {
            // Arrange
            when(mockClient.lookupEnvironmentByFingerprint(eq("broker-services"), eq(DOMAIN_ID), eq(SOLUTION_ID.toString())))
                    .thenReturn(Mono.error(new RuntimeException("Unexpected network error")));

            MockServerWebExchange exchange = buildAdminExchange("broker-services");

            // Act
            StepVerifier.create(filter.filter(exchange, mockChain))
                    .verifyComplete();

            // Assert — 503 with PROXY_001, not a propagated exception
            assertThat(exchange.getResponse().getStatusCode())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            String body = extractResponseBody(exchange);
            assertThat(body).contains("PROXY_001");
            verify(mockChain, never()).filter(any(ServerWebExchange.class));
        }
    }
}
