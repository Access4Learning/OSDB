// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import jakarta.servlet.http.HttpServletRequest;
import net.jqwik.api.*;
import org.mockito.Mockito;
import org.sifunity.broker.common.metrics.ConnectorMetricsService;
import org.sifunity.broker.common.util.HeaderSanitizer;
import org.sifunity.broker.common.util.TracingUtilsBase;
import org.sifunity.requestsconnector.cache.ProviderRegistryCache;
import org.sifunity.requestsconnector.controller.RequestController;
import org.sifunity.requestsconnector.dto.RequestResponseDTO;
import org.sifunity.requestsconnector.exception.GlobalExceptionHandler;
import org.sifunity.requestsconnector.exception.NoProviderException;
import org.sifunity.requestsconnector.service.RoutingService;
import org.sifunity.requestsconnector.util.HeaderExtractor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Preservation Property Tests: Legitimate requests unchanged (requests-connector).
 *
 * <p><strong>IMPORTANT: These tests MUST PASS on both unfixed and fixed code.</strong></p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6</strong></p>
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
            RoutingService routingService
    ) implements AutoCloseable {

        static TestContext create() {
            RoutingService routingService = mock(RoutingService.class);
            HeaderExtractor headerExtractor = mock(HeaderExtractor.class);
            HeaderSanitizer headerSanitizer = mock(HeaderSanitizer.class);
            ProviderRegistryCache providerCache = mock(ProviderRegistryCache.class);
            ConnectorMetricsService metricsService = mock(ConnectorMetricsService.class);
            TracingUtilsBase tracingUtils = mock(TracingUtilsBase.class);

            lenient().when(headerSanitizer.sanitize(any(HttpServletRequest.class)))
                    .thenReturn(Collections.emptyMap());

            RequestController controller = new RequestController(
                    routingService, headerExtractor, headerSanitizer, providerCache,
                    WebClient.builder(), metricsService, tracingUtils, List.of());
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
     * Preservation 1: Owner-matched DELAYED request returns 202.
     */
    @Property(tries = 20)
    @Label("Preservation: Owner-matched DELAYED GET returns 202")
    void ownerMatchedDelayedGetReturns202(
            @ForAll("queueIds") UUID ownedQueueId,
            @ForAll("serviceNames") String serviceName) {

        try (TestContext ctx = TestContext.create()) {
            UUID messageId = UUID.randomUUID();
            String requestId = UUID.randomUUID().toString();
            Instant timestamp = Instant.now();

            when(ctx.routingService().routeDelayedRequest(
                    any(), any(), anyString(), anyString(), anyString(), anyString(),
                    any(), any(), any(), anyString(), anyString(), any()))
                    .thenReturn(RequestResponseDTO.builder()
                            .messageId(messageId)
                            .queueId(ownedQueueId)
                            .requestId(requestId)
                            .status("ACCEPTED")
                            .timestamp(timestamp)
                            .build());

            String path = "/" + DOMAIN_ID + "/" + SOLUTION_ID + "/requests/" + serviceName;

            MvcResult result;
            try {
                result = ctx.mockMvc().perform(get(path)
                                .header("X-Environment-Id", ENV_ID.toString())
                                .header("X-Environment-Name", ENV_NAME)
                                .header("zoneId", ZONE_ID)
                                .header("contextId", "DEFAULT")
                                .header("requestType", "DELAYED")
                                .header("queueId", ownedQueueId.toString())
                                .header("serviceType", "OBJECT"))
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException("MockMvc request failed unexpectedly", e);
            }

            assertThat(result.getResponse().getStatus())
                    .as("Owner-matched DELAYED request must return 202")
                    .isEqualTo(202);

            // The response SHAPE must survive the fix, not merely the status code.
            // getContentAsByteArray() avoids the checked UnsupportedEncodingException that
            // getContentAsString() declares.
            String body = new String(result.getResponse().getContentAsByteArray(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertThat(body)
                    .as("202 payload must still carry messageId, queueId, requestId and status")
                    .contains(messageId.toString())
                    .contains(ownedQueueId.toString())
                    .contains(requestId)
                    .contains("ACCEPTED");
        }
    }

    /**
     * Preservation 2: IMMEDIATE requests never call routeDelayedRequest.
     */
    @Property(tries = 20)
    @Label("Preservation: IMMEDIATE GET does not invoke DELAYED routing")
    void immediateGetDoesNotInvokeDelayedRouting(
            @ForAll("serviceNames") String serviceName) {

        try (TestContext ctx = TestContext.create()) {
            String path = "/" + DOMAIN_ID + "/" + SOLUTION_ID + "/requests/" + serviceName;

            try {
                ctx.mockMvc().perform(get(path)
                                .header("X-Environment-Id", ENV_ID.toString())
                                .header("X-Environment-Name", ENV_NAME)
                                .header("zoneId", ZONE_ID)
                                .header("contextId", "DEFAULT")
                                .header("requestType", "IMMEDIATE")
                                .header("serviceType", "OBJECT"))
                        .andReturn();
            } catch (Exception e) {
                // proxyToProvider may fail in MockMvc context — that's fine,
                // we're only asserting routeDelayedRequest is never called
            }

            verify(ctx.routingService(), never()).routeDelayedRequest(
                    any(), any(), anyString(), anyString(), anyString(), anyString(),
                    any(), any(), any(), anyString(), anyString(), any());
        }
    }

    /**
     * Preservation 3: DELAYED without queueId returns error (existing behavior).
     */
    @Property(tries = 20)
    @Label("Preservation: DELAYED without queueId header returns error")
    void delayedWithoutQueueIdReturnsError(
            @ForAll("serviceNames") String serviceName) {

        try (TestContext ctx = TestContext.create()) {
            String path = "/" + DOMAIN_ID + "/" + SOLUTION_ID + "/requests/" + serviceName;

            MvcResult result;
            try {
                result = ctx.mockMvc().perform(get(path)
                                .header("X-Environment-Id", ENV_ID.toString())
                                .header("X-Environment-Name", ENV_NAME)
                                .header("zoneId", ZONE_ID)
                                .header("contextId", "DEFAULT")
                                .header("requestType", "DELAYED")
                                .header("serviceType", "OBJECT"))
                        .andReturn();
            } catch (Exception e) {
                throw new RuntimeException("MockMvc request failed unexpectedly", e);
            }

            assertThat(result.getResponse().getStatus())
                    .as("DELAYED without queueId must fail (400 or 500)")
                    .isIn(400, 500);
        }
    }

    /**
     * Preservation 4: NoProviderException still returns 404.
     */
    @Property(tries = 20)
    @Label("Preservation: NoProviderException still returns 404")
    void noProviderExceptionStillReturns404(
            @ForAll("queueIds") UUID queueId,
            @ForAll("serviceNames") String serviceName) {

        try (TestContext ctx = TestContext.create()) {
            when(ctx.routingService().routeDelayedRequest(
                    any(), any(), anyString(), anyString(), anyString(), anyString(),
                    any(), any(), any(), anyString(), anyString(), any()))
                    .thenThrow(new NoProviderException(serviceName, ZONE_ID, "DEFAULT"));

            String path = "/" + DOMAIN_ID + "/" + SOLUTION_ID + "/requests/" + serviceName;

            MvcResult result;
            try {
                result = ctx.mockMvc().perform(get(path)
                                .header("X-Environment-Id", ENV_ID.toString())
                                .header("X-Environment-Name", ENV_NAME)
                                .header("zoneId", ZONE_ID)
                                .header("contextId", "DEFAULT")
                                .header("requestType", "DELAYED")
                                .header("queueId", queueId.toString())
                                .header("serviceType", "OBJECT"))
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
        return Arbitraries.of(
                "StudentPersonal", "SchoolInfo", "StudentSchoolEnrollment",
                "StaffPersonal", "TeachingGroup");
    }
}
