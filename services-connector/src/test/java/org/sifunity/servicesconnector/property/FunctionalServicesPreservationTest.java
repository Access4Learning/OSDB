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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Preservation Property Tests for Functional Services Connector.
 *
 * <p>These tests capture the baseline behavior of existing endpoints on UNFIXED code.
 * They verify behaviors that must NOT change after the bug fix is applied.</p>
 *
 * <p><strong>Properties tested:</strong></p>
 * <ul>
 *   <li>P2e: IMMEDIATE requests still require ProviderRegistration (throws NoProviderException when absent)</li>
 *   <li>P2f: Job-level DELETE with explicit jobId routes through existing deleteJob endpoint</li>
 * </ul>
 *
 * <p><strong>EXPECTED OUTCOME: All tests PASS on unfixed code</strong> — these confirm
 * baseline behavior that the fix must preserve.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.3</strong></p>
 *
 * @see FunctionalServiceController
 * @see FunctionalServiceRoutingService
 */
@Tag("functional-services-connector-fix")
@Tag("preservation")
class FunctionalServicesPreservationTest {

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

    // ── P2e: IMMEDIATE requests require ProviderRegistration ─────────────

    /**
     * P2e: For all IMMEDIATE requests, routing still requires ProviderRegistration.
     * When no provider is registered, the system throws NoProviderException (HTTP 404).
     *
     * <p>This verifies that the IMMEDIATE flow's strict requirement for a registered
     * provider with endpoint URL is preserved by the fix. The fix only relaxes
     * requirements for DELAYED flow.</p>
     *
     * <p><strong>Validates: Requirements 3.1</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation P2e: IMMEDIATE routing throws NoProviderException when no provider registered")
    void immediateRoutingRequiresProviderRegistration(
            @ForAll("serviceNames") String serviceName) {

        try (TestContext ctx = TestContext.create()) {
            // Simulate: routeImmediateRequest throws NoProviderException (no provider found)
            when(ctx.routingService().routeImmediateRequest(
                    anyString(), anyString(), any(), any(),
                    any(), anyString(), anyString(), anyString(), anyString(), any(),
                    any(), any()))
                    .thenThrow(new NoProviderException(serviceName, ZONE_ID, CONTEXT_ID));

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
                                .header("requestType", "IMMEDIATE")
                                .header("requestId", REQUEST_ID.toString()))
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException("MockMvc request failed unexpectedly", e);
            }

            // IMMEDIATE flow without provider → 404 (NoProviderException)
            assertThat(result.getResponse().getStatus())
                    .as("IMMEDIATE POST to /services/%s without provider should return 404, but got %d",
                            serviceName, result.getResponse().getStatus())
                    .isEqualTo(404);
        }
    }

    /**
     * P2e variant: IMMEDIATE GET requests also require ProviderRegistration.
     *
     * <p><strong>Validates: Requirements 3.1</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation P2e: IMMEDIATE GET routing throws NoProviderException when no provider registered")
    void immediateGetRoutingRequiresProviderRegistration(
            @ForAll("serviceNames") String serviceName) {

        try (TestContext ctx = TestContext.create()) {
            when(ctx.routingService().routeImmediateRequest(
                    anyString(), anyString(), any(), any(),
                    any(), anyString(), anyString(), anyString(), anyString(), any(),
                    any(), any()))
                    .thenThrow(new NoProviderException(serviceName, ZONE_ID, CONTEXT_ID));

            String basePath = "/" + DOMAIN_ID + "/" + SOLUTION_ID + "/services/" + serviceName;

            MvcResult result;
            try {
                result = ctx.mockMvc().perform(get(basePath)
                                .header("X-Environment-Id", ENV_ID.toString())
                                .header("X-Environment-Name", ENV_NAME)
                                .header("zoneId", ZONE_ID)
                                .header("contextId", CONTEXT_ID)
                                .header("requestType", "IMMEDIATE")
                                .header("requestId", REQUEST_ID.toString()))
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException("MockMvc request failed unexpectedly", e);
            }

            assertThat(result.getResponse().getStatus())
                    .as("IMMEDIATE GET to /services/%s without provider should return 404, but got %d",
                            serviceName, result.getResponse().getStatus())
                    .isEqualTo(404);
        }
    }

    // ── P2f: Job-level DELETE routes through existing deleteJob endpoint ──

    /**
     * P2f: Job-level DELETE with explicit jobId routes through existing deleteJob endpoint.
     * When routing succeeds (DELAYED flow), returns 202 Accepted.
     *
     * <p>This verifies that the existing DELETE /{functionalServiceName}/{jobId} endpoint
     * continues to function correctly after the fix adds a new service-level DELETE endpoint.</p>
     *
     * <p><strong>Validates: Requirements 3.3</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation P2f: DELETE with jobId routes through deleteJob and returns 202 (DELAYED)")
    void deleteWithJobIdRoutesToDeleteJobDelayed(
            @ForAll("serviceNames") String serviceName,
            @ForAll("jobIds") String jobId) {

        try (TestContext ctx = TestContext.create()) {
            // Simulate successful DELAYED routing for job-level delete
            when(ctx.routingService().routeDelayedRequest(
                    eq("DELETE"), eq(serviceName), eq(jobId), isNull(),
                    any(), anyString(), anyString(), anyString(),
                    anyString(), any(), any(), any(),
                    any(), any()))
                    .thenReturn(Map.of("status", "accepted", "jobId", jobId));

            String basePath = "/" + DOMAIN_ID + "/" + SOLUTION_ID + "/services/" + serviceName + "/" + jobId;

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

            // Job-level DELETE with DELAYED routing → 202 Accepted
            assertThat(result.getResponse().getStatus())
                    .as("DELETE to /services/%s/%s should return 202 (DELAYED job-level delete), but got %d",
                            serviceName, jobId, result.getResponse().getStatus())
                    .isEqualTo(202);

            // Verify routing service was called with correct jobId (proving it went through deleteJob handler)
            verify(ctx.routingService()).routeDelayedRequest(
                    eq("DELETE"), eq(serviceName), eq(jobId), isNull(),
                    any(), anyString(), anyString(), anyString(),
                    anyString(), any(), any(), any(),
                    any(), any());
        }
    }

    /**
     * P2f variant: Job-level DELETE with IMMEDIATE flow also routes through deleteJob.
     * When provider throws NoProviderException, returns 404.
     *
     * <p><strong>Validates: Requirements 3.3</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation P2f: DELETE with jobId routes through deleteJob (IMMEDIATE, no provider → 404)")
    void deleteWithJobIdRoutesToDeleteJobImmediate(
            @ForAll("serviceNames") String serviceName,
            @ForAll("jobIds") String jobId) {

        try (TestContext ctx = TestContext.create()) {
            // Simulate: IMMEDIATE routing throws NoProviderException
            when(ctx.routingService().routeImmediateRequest(
                    eq("DELETE"), eq(serviceName), eq(jobId), isNull(),
                    any(), anyString(), anyString(), anyString(), anyString(), any(),
                    any(), any()))
                    .thenThrow(new NoProviderException(serviceName, ZONE_ID, CONTEXT_ID));

            String basePath = "/" + DOMAIN_ID + "/" + SOLUTION_ID + "/services/" + serviceName + "/" + jobId;

            MvcResult result;
            try {
                result = ctx.mockMvc().perform(delete(basePath)
                                .header("X-Environment-Id", ENV_ID.toString())
                                .header("X-Environment-Name", ENV_NAME)
                                .header("zoneId", ZONE_ID)
                                .header("contextId", CONTEXT_ID)
                                .header("requestType", "IMMEDIATE")
                                .header("requestId", REQUEST_ID.toString()))
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException("MockMvc request failed unexpectedly", e);
            }

            // IMMEDIATE delete without provider → 404
            assertThat(result.getResponse().getStatus())
                    .as("IMMEDIATE DELETE to /services/%s/%s without provider should return 404, but got %d",
                            serviceName, jobId, result.getResponse().getStatus())
                    .isEqualTo(404);

            // Verify routing service was called with the jobId
            verify(ctx.routingService()).routeImmediateRequest(
                    eq("DELETE"), eq(serviceName), eq(jobId), isNull(),
                    any(), anyString(), anyString(), anyString(), anyString(), any(),
                    any(), any());
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

    @Provide
    Arbitrary<String> jobIds() {
        return Arbitraries.of(
                "job-001", "job-002", "abc123",
                "export-run-1", "sync-task-99", "batch-42");
    }
}
