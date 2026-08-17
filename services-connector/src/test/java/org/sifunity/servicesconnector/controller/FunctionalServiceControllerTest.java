// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.metrics.ConnectorMetricsService;
import org.sifunity.broker.common.util.HeaderSanitizer;
import org.sifunity.servicesconnector.exception.GlobalExceptionHandler;
import org.sifunity.servicesconnector.service.FunctionalServiceRoutingService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link FunctionalServiceController} route mappings.
 *
 * <p>Uses standalone MockMvc setup to avoid Spring context loading issues
 * (Redis, ProviderRegistryCache).</p>
 *
 * <p>Validates:</p>
 * <ul>
 *   <li>Requirements 1.1–1.9: All 9 URL patterns map to the correct handler</li>
 *   <li>Requirement 5.2: IMMEDIATE vs DELAYED branching based on requestType header</li>
 *   <li>Requirement 5.5: 400 when DELAYED request missing queueId header</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FunctionalServiceController Route Mapping Tests")
class FunctionalServiceControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FunctionalServiceRoutingService routingService;

    @Mock
    private HeaderSanitizer headerSanitizer;

    @Mock
    private ConnectorMetricsService connectorMetricsService;

    private static final String DOMAIN_ID = "domain-1";
    private static final String SOLUTION_ID = "solution-1";
    private static final String SERVICE_NAME = "DataExport";
    private static final String JOB_ID = "job-123";
    private static final String PHASE_NAME = "export";
    private static final UUID ENV_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ENV_NAME = "test-env";
    private static final String ZONE_ID = "test-zone";
    private static final String CONTEXT_ID = "test-context";
    private static final UUID QUEUE_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    private static final String BASE = "/" + DOMAIN_ID + "/" + SOLUTION_ID + "/services";

    @BeforeEach
    void setUp() {
        FunctionalServiceController controller =
                new FunctionalServiceController(routingService, headerSanitizer, connectorMetricsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        lenient().when(headerSanitizer.sanitize(any(HttpServletRequest.class)))
                .thenReturn(Collections.emptyMap());
    }

    private void stubImmediateRouting() {
        when(routingService.routeImmediateRequest(
                anyString(), anyString(), any(), any(),
                any(), anyString(), anyString(), anyString(), anyString(), any(),
                any(), any()))
                .thenReturn(Map.of("status", "ok"));
    }

    private void stubDelayedRouting() {
        when(routingService.routeDelayedRequest(
                anyString(), anyString(), any(), any(),
                any(), anyString(), anyString(), anyString(),
                anyString(), any(), any(), any(),
                any(), any()))
                .thenReturn(Map.of("status", "accepted"));
    }

    // ── Requirement 1.1: POST /services/{functionalServiceName} ─────────

    @Nested
    @DisplayName("Req 1.1 - Create Job: POST /services/{functionalServiceName}")
    class CreateJob {

        @Test
        @DisplayName("IMMEDIATE create job returns 200")
        void immediateCreateJob() throws Exception {
            stubImmediateRouting();

            mockMvc.perform(post(BASE + "/" + SERVICE_NAME)
                            .header("X-Environment-Id", ENV_ID)
                            .header("X-Environment-Name", ENV_NAME)
                            .header("zoneId", ZONE_ID)
                            .header("contextId", CONTEXT_ID)
                            .header("requestType", "IMMEDIATE")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"test-job\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"));

            verify(routingService).routeImmediateRequest(
                    eq("POST"), eq(SERVICE_NAME), isNull(), isNull(),
                    eq(ENV_ID), eq(ENV_NAME), eq(DOMAIN_ID), eq(SOLUTION_ID), eq(ZONE_ID), eq(CONTEXT_ID),
                    any(), any());
        }

        @Test
        @DisplayName("DELAYED create job returns 202")
        void delayedCreateJob() throws Exception {
            stubDelayedRouting();

            mockMvc.perform(post(BASE + "/" + SERVICE_NAME)
                            .header("X-Environment-Id", ENV_ID)
                            .header("X-Environment-Name", ENV_NAME)
                            .header("zoneId", ZONE_ID)
                            .header("contextId", CONTEXT_ID)
                            .header("requestType", "DELAYED")
                            .header("queueId", QUEUE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"test-job\"}"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("accepted"));

            verify(routingService).routeDelayedRequest(
                    eq("POST"), eq(SERVICE_NAME), isNull(), isNull(),
                    eq(ENV_ID), eq(ENV_NAME), eq(DOMAIN_ID), eq(SOLUTION_ID),
                    eq(ZONE_ID), eq(CONTEXT_ID), eq(QUEUE_ID),
                    any(),
                    any(), any());
        }
    }

    // ── Requirement 1.2: GET /services/{functionalServiceName} ──────────

    @Test
    @DisplayName("Req 1.2 - IMMEDIATE list jobs returns 200")
    void immediateListJobs() throws Exception {
        stubImmediateRouting();
        mockMvc.perform(get(BASE + "/" + SERVICE_NAME)
                        .header("X-Environment-Id", ENV_ID)
                        .header("X-Environment-Name", ENV_NAME)
                        .header("zoneId", ZONE_ID)
                        .header("requestType", "IMMEDIATE"))
                .andExpect(status().isOk());
        verify(routingService).routeImmediateRequest(
                eq("GET"), eq(SERVICE_NAME), isNull(), isNull(),
                eq(ENV_ID), eq(ENV_NAME), eq(DOMAIN_ID), eq(SOLUTION_ID), eq(ZONE_ID), eq("DEFAULT"),
                isNull(), any());
    }

    // ── Requirement 1.3: GET /services/{name}/{jobId} ───────────────────

    @Test
    @DisplayName("Req 1.3 - IMMEDIATE get job returns 200")
    void immediateGetJob() throws Exception {
        stubImmediateRouting();
        mockMvc.perform(get(BASE + "/" + SERVICE_NAME + "/" + JOB_ID)
                        .header("X-Environment-Id", ENV_ID)
                        .header("X-Environment-Name", ENV_NAME)
                        .header("zoneId", ZONE_ID)
                        .header("requestType", "IMMEDIATE"))
                .andExpect(status().isOk());
        verify(routingService).routeImmediateRequest(
                eq("GET"), eq(SERVICE_NAME), eq(JOB_ID), isNull(),
                eq(ENV_ID), eq(ENV_NAME), eq(DOMAIN_ID), eq(SOLUTION_ID), eq(ZONE_ID), eq("DEFAULT"),
                isNull(), any());
    }

    // ── Requirement 1.4: DELETE /services/{name}/{jobId} ────────────────

    @Test
    @DisplayName("Req 1.4 - IMMEDIATE delete job returns 200")
    void immediateDeleteJob() throws Exception {
        stubImmediateRouting();
        mockMvc.perform(delete(BASE + "/" + SERVICE_NAME + "/" + JOB_ID)
                        .header("X-Environment-Id", ENV_ID)
                        .header("X-Environment-Name", ENV_NAME)
                        .header("zoneId", ZONE_ID)
                        .header("requestType", "IMMEDIATE"))
                .andExpect(status().isOk());
        verify(routingService).routeImmediateRequest(
                eq("DELETE"), eq(SERVICE_NAME), eq(JOB_ID), isNull(),
                eq(ENV_ID), eq(ENV_NAME), eq(DOMAIN_ID), eq(SOLUTION_ID), eq(ZONE_ID), eq("DEFAULT"),
                isNull(), any());
    }

    // ── Requirement 1.5: POST /services/{name}/{jobId}/{phaseName} ──────

    @Test
    @DisplayName("Req 1.5 - IMMEDIATE create phase data returns 200")
    void immediateCreatePhaseData() throws Exception {
        stubImmediateRouting();
        mockMvc.perform(post(BASE + "/" + SERVICE_NAME + "/" + JOB_ID + "/" + PHASE_NAME)
                        .header("X-Environment-Id", ENV_ID)
                        .header("X-Environment-Name", ENV_NAME)
                        .header("zoneId", ZONE_ID)
                        .header("requestType", "IMMEDIATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"phase-payload\"}"))
                .andExpect(status().isOk());
        verify(routingService).routeImmediateRequest(
                eq("POST"), eq(SERVICE_NAME), eq(JOB_ID), eq(PHASE_NAME),
                eq(ENV_ID), eq(ENV_NAME), eq(DOMAIN_ID), eq(SOLUTION_ID), eq(ZONE_ID), eq("DEFAULT"),
                any(), any());
    }

    // ── Requirement 1.6: PUT /services/{name}/{jobId}/{phaseName} ───────

    @Test
    @DisplayName("Req 1.6 - IMMEDIATE update phase data returns 200")
    void immediateUpdatePhaseData() throws Exception {
        stubImmediateRouting();
        mockMvc.perform(put(BASE + "/" + SERVICE_NAME + "/" + JOB_ID + "/" + PHASE_NAME)
                        .header("X-Environment-Id", ENV_ID)
                        .header("X-Environment-Name", ENV_NAME)
                        .header("zoneId", ZONE_ID)
                        .header("requestType", "IMMEDIATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"updated\"}"))
                .andExpect(status().isOk());
        verify(routingService).routeImmediateRequest(
                eq("PUT"), eq(SERVICE_NAME), eq(JOB_ID), eq(PHASE_NAME),
                eq(ENV_ID), eq(ENV_NAME), eq(DOMAIN_ID), eq(SOLUTION_ID), eq(ZONE_ID), eq("DEFAULT"),
                any(), any());
    }

    // ── Requirement 1.7: GET /services/{name}/{jobId}/{phaseName} ───────

    @Test
    @DisplayName("Req 1.7 - IMMEDIATE get phase data returns 200")
    void immediateGetPhaseData() throws Exception {
        stubImmediateRouting();
        mockMvc.perform(get(BASE + "/" + SERVICE_NAME + "/" + JOB_ID + "/" + PHASE_NAME)
                        .header("X-Environment-Id", ENV_ID)
                        .header("X-Environment-Name", ENV_NAME)
                        .header("zoneId", ZONE_ID)
                        .header("requestType", "IMMEDIATE"))
                .andExpect(status().isOk());
        verify(routingService).routeImmediateRequest(
                eq("GET"), eq(SERVICE_NAME), eq(JOB_ID), eq(PHASE_NAME),
                eq(ENV_ID), eq(ENV_NAME), eq(DOMAIN_ID), eq(SOLUTION_ID), eq(ZONE_ID), eq("DEFAULT"),
                isNull(), any());
    }

    // ── Requirement 1.8: GET /services/{name}/{jobId}/{phase}/states ────

    @Test
    @DisplayName("Req 1.8 - IMMEDIATE get phase states returns 200")
    void immediateGetPhaseStates() throws Exception {
        stubImmediateRouting();
        mockMvc.perform(get(BASE + "/" + SERVICE_NAME + "/" + JOB_ID + "/" + PHASE_NAME + "/states")
                        .header("X-Environment-Id", ENV_ID)
                        .header("X-Environment-Name", ENV_NAME)
                        .header("zoneId", ZONE_ID)
                        .header("requestType", "IMMEDIATE"))
                .andExpect(status().isOk());
        verify(routingService).routeImmediateRequest(
                eq("GET"), eq(SERVICE_NAME), eq(JOB_ID), eq(PHASE_NAME + "/states"),
                eq(ENV_ID), eq(ENV_NAME), eq(DOMAIN_ID), eq(SOLUTION_ID), eq(ZONE_ID), eq("DEFAULT"),
                isNull(), any());
    }

    // ── Requirement 1.9: POST /services/{name}/{jobId}/{phase}/states/state

    @Test
    @DisplayName("Req 1.9 - IMMEDIATE create/update phase state returns 200")
    void immediateCreateOrUpdatePhaseState() throws Exception {
        stubImmediateRouting();
        mockMvc.perform(post(BASE + "/" + SERVICE_NAME + "/" + JOB_ID + "/" + PHASE_NAME + "/states/state")
                        .header("X-Environment-Id", ENV_ID)
                        .header("X-Environment-Name", ENV_NAME)
                        .header("zoneId", ZONE_ID)
                        .header("requestType", "IMMEDIATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"INPROGRESS\"}"))
                .andExpect(status().isOk());
        verify(routingService).routeImmediateRequest(
                eq("POST"), eq(SERVICE_NAME), eq(JOB_ID), eq(PHASE_NAME + "/states/state"),
                eq(ENV_ID), eq(ENV_NAME), eq(DOMAIN_ID), eq(SOLUTION_ID), eq(ZONE_ID), eq("DEFAULT"),
                any(), any());
    }

    // ── Requirement 5.2: IMMEDIATE vs DELAYED branching ─────────────────

    @Test
    @DisplayName("Req 5.2 - IMMEDIATE calls routeImmediateRequest, not routeDelayedRequest")
    void immediateRequestCallsImmediateRoute() throws Exception {
        stubImmediateRouting();
        mockMvc.perform(get(BASE + "/" + SERVICE_NAME + "/" + JOB_ID)
                        .header("X-Environment-Id", ENV_ID)
                        .header("X-Environment-Name", ENV_NAME)
                        .header("zoneId", ZONE_ID)
                        .header("requestType", "IMMEDIATE"))
                .andExpect(status().isOk());
        verify(routingService).routeImmediateRequest(
                anyString(), anyString(), any(), any(),
                any(), anyString(), anyString(), anyString(), anyString(), any(), any(), any());
        verify(routingService, never()).routeDelayedRequest(
                anyString(), anyString(), any(), any(),
                any(), anyString(), anyString(), anyString(),
                anyString(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Req 5.2 - DELAYED calls routeDelayedRequest and returns 202")
    void delayedRequestCallsDelayedRoute() throws Exception {
        stubDelayedRouting();
        mockMvc.perform(post(BASE + "/" + SERVICE_NAME)
                        .header("X-Environment-Id", ENV_ID)
                        .header("X-Environment-Name", ENV_NAME)
                        .header("zoneId", ZONE_ID)
                        .header("requestType", "DELAYED")
                        .header("queueId", QUEUE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"delayed-job\"}"))
                .andExpect(status().isAccepted());
        verify(routingService).routeDelayedRequest(
                anyString(), anyString(), any(), any(),
                any(), anyString(), anyString(), anyString(),
                anyString(), any(), any(), any(), any(), any());
        verify(routingService, never()).routeImmediateRequest(
                anyString(), anyString(), any(), any(),
                any(), anyString(), anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("Req 5.2 - Invalid requestType returns 400")
    void invalidRequestTypeReturns400() throws Exception {
        mockMvc.perform(post(BASE + "/" + SERVICE_NAME)
                        .header("X-Environment-Id", ENV_ID)
                        .header("X-Environment-Name", ENV_NAME)
                        .header("zoneId", ZONE_ID)
                        .header("requestType", "INVALID")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Requirement 5.5: DELAYED missing queueId returns 400 ────────────

    @Test
    @DisplayName("Req 5.5 - DELAYED request without queueId returns 400")
    void delayedWithoutQueueIdReturns400() throws Exception {
        mockMvc.perform(post(BASE + "/" + SERVICE_NAME)
                        .header("X-Environment-Id", ENV_ID)
                        .header("X-Environment-Name", ENV_NAME)
                        .header("zoneId", ZONE_ID)
                        .header("requestType", "DELAYED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"delayed-job\"}"))
                .andExpect(status().isBadRequest());
    }
}
