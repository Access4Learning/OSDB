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

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Preservation Property Tests: Legitimate functional service requests unchanged.
 *
 * <p><strong>IMPORTANT: These tests MUST PASS on both unfixed and fixed code.</strong></p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.6, 3.7, 3.8</strong></p>
 */
@Tag("delayed-queue-ownership-fix")
@Tag("preservation")
class DelayedQueueOwnershipPreservationTest {

    private static final String DOMAIN_ID = "domain1";
    private static final String SOLUTION_ID = "solution1";
    private static final UUID ENV_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ENV_NAME = "test-consumer";
    private static final String ZONE_ID = "test-zone";

    private record TestContext(
            MockMvc mockMvc,
            FunctionalServiceRoutingService routingService
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

            return new TestContext(mockMvc, routingService);
        }

        @Override
        public void close() {
            Mockito.reset(routingService);
        }
    }

    /**
     * Preservation 1: Owner-matched DELAYED POST returns 202 with expected response.
     */
    @Property(tries = 20)
    @Label("Preservation: Owner-matched DELAYED POST returns 202 with messageId and queueId")
    void ownerMatchedDelayedPostReturns202(
            @ForAll("queueIds") UUID ownedQueueId,
            @ForAll("serviceNames") String serviceName) {

        try (TestContext ctx = TestContext.create()) {
            UUID messageId = UUID.randomUUID();

            when(ctx.routingService().routeDelayedRequest(
                    anyString(), anyString(), any(), any(),
                    any(), any(), anyString(), anyString(),
                    anyString(), anyString(), any(), any(),
                    any(), any()))
                    .thenReturn(Map.of(
                            "messageId", messageId.toString(),
                            "queueId", ownedQueueId.toString(),
                            "status", "ACCEPTED"));

            String path = "/" + DOMAIN_ID + "/" + SOLUTION_ID + "/services/" + serviceName;

            MvcResult result;
            try {
                result = ctx.mockMvc().perform(post(path)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"operation\": \"start\"}")
                                .header("X-Environment-Id", ENV_ID.toString())
                                .header("X-Environment-Name", ENV_NAME)
                                .header("zoneId", ZONE_ID)
                                .header("contextId", "DEFAULT")
                                .header("requestType", "DELAYED")
                                .header("queueId", ownedQueueId.toString())
                                .header("requestId", UUID.randomUUID().toString()))
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException("MockMvc request failed unexpectedly", e);
            }

            assertThat(result.getResponse().getStatus())
                    .as("Owner-matched DELAYED must return 202")
                    .isEqualTo(202);

            // The response SHAPE must survive the fix, not merely the status code.
            // getContentAsByteArray() avoids the checked UnsupportedEncodingException that
            // getContentAsString() declares.
            String body = new String(result.getResponse().getContentAsByteArray(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertThat(body)
                    .as("202 payload must still carry messageId, queueId and status")
                    .contains(messageId.toString())
                    .contains(ownedQueueId.toString())
                    .contains("ACCEPTED");
        }
    }

    /**
     * Preservation 2: IMMEDIATE request does not invoke routeDelayedRequest.
     */
    @Property(tries = 20)
    @Label("Preservation: IMMEDIATE POST proxies through — no DELAYED routing or queue lookup")
    void immediatePostDoesNotInvokeDelayedRouting(
            @ForAll("serviceNames") String serviceName) {

        try (TestContext ctx = TestContext.create()) {
            when(ctx.routingService().routeImmediateRequest(
                    anyString(), anyString(), any(), any(),
                    any(), any(), anyString(), anyString(),
                    anyString(), anyString(), any(), any()))
                    .thenReturn(Map.of("result", "provider-response"));

            String path = "/" + DOMAIN_ID + "/" + SOLUTION_ID + "/services/" + serviceName;

            MvcResult result;
            try {
                result = ctx.mockMvc().perform(post(path)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"operation\": \"start\"}")
                                .header("X-Environment-Id", ENV_ID.toString())
                                .header("X-Environment-Name", ENV_NAME)
                                .header("zoneId", ZONE_ID)
                                .header("contextId", "DEFAULT")
                                .header("requestType", "IMMEDIATE"))
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException("MockMvc request failed unexpectedly", e);
            }

            assertThat(result.getResponse().getStatus()).isEqualTo(200);

            // routeDelayedRequest must never be called for IMMEDIATE
            verify(ctx.routingService(), never()).routeDelayedRequest(
                    anyString(), anyString(), any(), any(),
                    any(), any(), anyString(), anyString(),
                    anyString(), anyString(), any(), any(),
                    any(), any());
        }
    }

    /**
     * Preservation 3: Missing queueId on DELAYED returns 400 (existing behavior).
     */
    @Property(tries = 20)
    @Label("Preservation: DELAYED without queueId returns 400/500 (unchanged)")
    void delayedWithoutQueueIdReturnsBadRequest(
            @ForAll("serviceNames") String serviceName) {

        try (TestContext ctx = TestContext.create()) {
            String path = "/" + DOMAIN_ID + "/" + SOLUTION_ID + "/services/" + serviceName;

            MvcResult result;
            try {
                result = ctx.mockMvc().perform(post(path)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"operation\": \"start\"}")
                                .header("X-Environment-Id", ENV_ID.toString())
                                .header("X-Environment-Name", ENV_NAME)
                                .header("zoneId", ZONE_ID)
                                .header("contextId", "DEFAULT")
                                .header("requestType", "DELAYED"))
                        // No queueId header
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException("MockMvc request failed unexpectedly", e);
            }

            assertThat(result.getResponse().getStatus())
                    .as("DELAYED without queueId must still fail")
                    .isIn(400, 500);
        }
    }

    /**
     * Preservation 4: NoProviderException still returns 404.
     */
    @Property(tries = 20)
    @Label("Preservation: NoProviderException still returns 404 (unchanged)")
    void noProviderExceptionStillReturns404(
            @ForAll("queueIds") UUID queueId,
            @ForAll("serviceNames") String serviceName) {

        try (TestContext ctx = TestContext.create()) {
            when(ctx.routingService().routeDelayedRequest(
                    anyString(), anyString(), any(), any(),
                    any(), any(), anyString(), anyString(),
                    anyString(), anyString(), any(), any(),
                    any(), any()))
                    .thenThrow(new NoProviderException(serviceName, ZONE_ID, "DEFAULT"));

            String path = "/" + DOMAIN_ID + "/" + SOLUTION_ID + "/services/" + serviceName;

            MvcResult result;
            try {
                result = ctx.mockMvc().perform(post(path)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"operation\": \"start\"}")
                                .header("X-Environment-Id", ENV_ID.toString())
                                .header("X-Environment-Name", ENV_NAME)
                                .header("zoneId", ZONE_ID)
                                .header("contextId", "DEFAULT")
                                .header("requestType", "DELAYED")
                                .header("queueId", queueId.toString())
                                .header("requestId", UUID.randomUUID().toString()))
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException("MockMvc request failed unexpectedly", e);
            }

            assertThat(result.getResponse().getStatus())
                    .as("NoProviderException must still return 404")
                    .isEqualTo(404);
        }
    }

    // ── Arbitraries ──────────────────────────────────────────────────────────

    @Provide
    Arbitrary<UUID> queueIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.of("StudentTranscript", "AggregateCharacteristics",
                "LearnerRecordTransfer", "ReportGeneration");
    }
}
