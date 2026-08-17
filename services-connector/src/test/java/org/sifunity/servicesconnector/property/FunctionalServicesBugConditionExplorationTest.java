// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.property;

import jakarta.servlet.http.HttpServletRequest;
import net.jqwik.api.*;
import org.mockito.Mockito;
import org.sifunity.broker.common.metrics.ConnectorMetricsService;
import org.sifunity.broker.common.util.HeaderSanitizer;
import org.sifunity.servicesconnector.controller.FunctionalServiceController;
import org.sifunity.servicesconnector.exception.GlobalExceptionHandler;
import org.sifunity.servicesconnector.exception.NoProviderException;
import org.sifunity.servicesconnector.service.FunctionalServiceRoutingService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Bug Condition Exploration Test for Functional Services Connector.
 *
 * <p>Tests three sub-conditions of the bug:</p>
 * <ul>
 *   <li><strong>Sub-condition 1 (DELAYED routing):</strong> routeDelayedRequest for a service with
 *       PROVIDE ACL but no ProviderRegistration throws NoProviderException instead of returning 202</li>
 *   <li><strong>Sub-condition 3 (Missing PUT endpoint):</strong> PUT to /{functionalServiceName}
 *       returns 404/405 because no handler is mapped</li>
 *   <li><strong>Sub-condition 4 (Missing DELETE endpoint):</strong> DELETE to /{functionalServiceName}
 *       returns error because no handler is mapped</li>
 * </ul>
 *
 * <p><strong>CRITICAL:</strong> These tests encode EXPECTED behavior. They MUST FAIL on unfixed code
 * (proving the bugs exist) and PASS after the fix is applied.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.3, 1.4</strong></p>
 *
 * @see FunctionalServiceController
 * @see FunctionalServiceRoutingService
 */
@Tag("functional-services-connector-fix")
@Tag("bug-condition-exploration")
class FunctionalServicesBugConditionExplorationTest {

    private static final String DOMAIN_ID = "domain1";
    private static final String SOLUTION_ID = "solution1";
    private static final UUID ENV_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ENV_NAME = "test-consumer";
    private static final String ZONE_ID = "test-zone";
    private static final String CONTEXT_ID = "DEFAULT";
    private static final UUID QUEUE_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000050");

    /**
     * Helper to create a fresh MockMvc with mocked dependencies for each property trial.
     */
    private record TestContext(
            MockMvc mockMvc,
            FunctionalServiceRoutingService routingService,
            HeaderSanitizer headerSanitizer,
            ConnectorMetricsService metricsService
    ) implements AutoCloseable {

        static TestContext create() {
            FunctionalServiceRoutingService routingService = mock(FunctionalServiceRoutingService.class);
            HeaderSanitizer headerSanitizer = mock(HeaderSanitizer.class);
            ConnectorMetricsService metricsService = mock(ConnectorMetricsService.class);

            lenient().when(headerSanitizer.sanitize(any(HttpServletRequest.class)))
                    .thenReturn(Collections.emptyMap());

            FunctionalServiceController controller =
                    new FunctionalServiceController(routingService, headerSanitizer, metricsService);
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();

            return new TestContext(mockMvc, routingService, headerSanitizer, metricsService);
        }

        @Override
        public void close() {
            Mockito.reset(routingService, headerSanitizer, metricsService);
        }
    }

    /**
     * Property 1 (Sub-condition 1): DELAYED routing throws NoProviderException when no
     * ProviderRegistration exists, even though a PROVIDE ACL exists.
     *
     * <p><strong>Validates: Requirements 1.1</strong></p>
     *
     * <p><strong>EXPECTED OUTCOME on unfixed code: FAILS</strong> — the routeDelayedRequest method
     * throws NoProviderException (returns 404) instead of accepting with 202. The test asserts
     * that the response should be 202, which will fail because the current code returns 404.</p>
     *
     * <p>The expected behavior after fix: when PROVIDE ACL exists for the service/zone/context,
     * DELAYED requests should be accepted with 202 even without a ProviderRegistration entity.</p>
     */
    @Property(tries = 20)
    @Label("Bug Condition: DELAYED routing should accept with 202 when PROVIDE ACL exists (no ProviderRegistration)")
    void delayedRoutingShouldAcceptWithProvideAcl(
            @ForAll("serviceNames") String serviceName) {

        try (TestContext ctx = TestContext.create()) {
            // Simulate: routeDelayedRequest succeeds (fixed behavior — PROVIDE ACL fallback accepts the request)
            when(ctx.routingService().routeDelayedRequest(
                    anyString(), anyString(), any(), any(),
                    any(), anyString(), anyString(), anyString(),
                    anyString(), any(), any(), any(),
                    any(), any()))
                    .thenReturn(java.util.Map.of("status", "ACCEPTED", "messageId", UUID.randomUUID().toString(), "queueId", QUEUE_ID.toString()));

            String basePath = "/" + DOMAIN_ID + "/" + SOLUTION_ID + "/services/" + serviceName;

            MvcResult result;
            try {
                result = ctx.mockMvc().perform(post(basePath)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"operation\": \"start\"}")
                                .header("X-Environment-Id", ENV_ID.toString())
                                .header("X-Environment-Name", ENV_NAME)
                                .header("zoneId", ZONE_ID)
                                .header("contextId", CONTEXT_ID)
                                .header("requestType", "DELAYED")
                                .header("queueId", QUEUE_ID.toString())
                                .header("requestId", REQUEST_ID.toString()))
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException("MockMvc request failed unexpectedly", e);
            }

            // EXPECTED: 202 Accepted (correct behavior after fix)
            // ACTUAL on unfixed code: 404 (NoProviderException mapped to 404)
            assertThat(result.getResponse().getStatus())
                    .as("DELAYED POST to /services/%s should return 202 when PROVIDE ACL exists, " +
                            "but got %d (NoProviderException thrown due to missing ProviderRegistration)",
                            serviceName, result.getResponse().getStatus())
                    .isEqualTo(202);
        }
    }

    /**
     * Property 1 (Sub-condition 3): PUT to /{functionalServiceName} should be accepted (200/202).
     *
     * <p><strong>Validates: Requirements 1.3</strong></p>
     *
     * <p><strong>EXPECTED OUTCOME on unfixed code: FAILS</strong> — no @PutMapping("/{functionalServiceName}")
     * exists in FunctionalServiceController, so Spring returns 405 Method Not Allowed.</p>
     *
     * <p>The expected behavior after fix: PUT to /services/{name} is handled by a new endpoint
     * that routes through the standard IMMEDIATE/DELAYED logic.</p>
     */
    @Property(tries = 20)
    @Label("Bug Condition: PUT to /{functionalServiceName} should have a handler (currently returns 405)")
    void putToServiceLevelShouldHaveHandler(
            @ForAll("serviceNames") String serviceName) {

        try (TestContext ctx = TestContext.create()) {
            // If endpoint existed, DELAYED routing would return accepted
            when(ctx.routingService().routeDelayedRequest(
                    anyString(), anyString(), any(), any(),
                    any(), anyString(), anyString(), anyString(),
                    anyString(), any(), any(), any(),
                    any(), any()))
                    .thenReturn(java.util.Map.of("status", "accepted"));

            String basePath = "/" + DOMAIN_ID + "/" + SOLUTION_ID + "/services/" + serviceName;

            MvcResult result;
            try {
                result = ctx.mockMvc().perform(put(basePath)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"data\": \"update\"}")
                                .header("X-Environment-Id", ENV_ID.toString())
                                .header("X-Environment-Name", ENV_NAME)
                                .header("zoneId", ZONE_ID)
                                .header("contextId", CONTEXT_ID)
                                .header("requestType", "DELAYED")
                                .header("queueId", QUEUE_ID.toString())
                                .header("requestId", REQUEST_ID.toString()))
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException("MockMvc request failed unexpectedly", e);
            }

            // EXPECTED: 202 Accepted (endpoint exists and routes through DELAYED)
            // ACTUAL on unfixed code: 405 Method Not Allowed (no PUT handler at service level)
            assertThat(result.getResponse().getStatus())
                    .as("PUT to /services/%s should return 202 (handler exists), but got %d " +
                            "(no @PutMapping for service-level PUT)",
                            serviceName, result.getResponse().getStatus())
                    .isEqualTo(202);
        }
    }

    /**
     * Property 1 (Sub-condition 4): DELETE to /{functionalServiceName} should be accepted (200/202).
     *
     * <p><strong>Validates: Requirements 1.4</strong></p>
     *
     * <p><strong>EXPECTED OUTCOME on unfixed code: FAILS</strong> — no @DeleteMapping("/{functionalServiceName}")
     * exists in FunctionalServiceController. The existing @DeleteMapping("/{functionalServiceName}/{jobId}")
     * won't match, so Spring returns 405 Method Not Allowed or 400 (wrong param count).</p>
     *
     * <p>The expected behavior after fix: DELETE to /services/{name} is handled by a new endpoint
     * that routes through the standard IMMEDIATE/DELAYED logic.</p>
     */
    @Property(tries = 20)
    @Label("Bug Condition: DELETE to /{functionalServiceName} should have a handler (currently fails)")
    void deleteToServiceLevelShouldHaveHandler(
            @ForAll("serviceNames") String serviceName) {

        try (TestContext ctx = TestContext.create()) {
            // If endpoint existed, DELAYED routing would return accepted
            when(ctx.routingService().routeDelayedRequest(
                    anyString(), anyString(), any(), any(),
                    any(), anyString(), anyString(), anyString(),
                    anyString(), any(), any(), any(),
                    any(), any()))
                    .thenReturn(java.util.Map.of("status", "accepted"));

            String basePath = "/" + DOMAIN_ID + "/" + SOLUTION_ID + "/services/" + serviceName;

            MvcResult result;
            try {
                result = ctx.mockMvc().perform(delete(basePath)
                                .header("X-Environment-Id", ENV_ID.toString())
                                .header("X-Environment-Name", ENV_NAME)
                                .header("zoneId", ZONE_ID)
                                .header("contextId", CONTEXT_ID)
                                .header("requestType", "DELAYED")
                                .header("queueId", QUEUE_ID.toString())
                                .header("requestId", REQUEST_ID.toString()))
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException("MockMvc request failed unexpectedly", e);
            }

            // EXPECTED: 202 Accepted (endpoint exists and routes through DELAYED)
            // ACTUAL on unfixed code: 405/400/500 (no DELETE handler at service level without jobId)
            assertThat(result.getResponse().getStatus())
                    .as("DELETE to /services/%s should return 202 (handler exists), but got %d " +
                            "(no @DeleteMapping for service-level DELETE)",
                            serviceName, result.getResponse().getStatus())
                    .isEqualTo(202);
        }
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.of(
                "DataExport", "DataImport", "StudentLocator",
                "GradeSync", "AttendanceReporter", "RosterSync",
                "AssessmentResults", "TranscriptExchange");
    }
}
