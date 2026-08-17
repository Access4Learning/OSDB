// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.util.HeaderSanitizer;
import org.sifunity.responsesconnector.controller.ResponseController;
import org.sifunity.responsesconnector.dto.ResponseResultDTO;
import org.sifunity.broker.common.metrics.ConnectorMetricsService;
import org.sifunity.responsesconnector.service.ResponseDestinationValidator;
import org.sifunity.responsesconnector.service.ResponseRoutingService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Certifies that the public error endpoint is gone and that removing it did not take the surviving
 * response endpoint with it.
 *
 * <p>{@code POST /{domainId}/{solutionId}/responses/error} corresponded to no SIF concept — the
 * specification conveys errors as an Error Object carried in the response payload, with the code in
 * the HTTP status field, and defines no separate error endpoint — and had no callers anywhere in the
 * repository. Errors are still fully supported: a provider posts its Error Object to
 * {@code /responses}, and the broker's own failure reports go through the token-gated
 * {@code POST /internal/responses/error}, which is retained.</p>
 *
 * <p>The second test is the one that matters. A removal test that only asserts "the old path 404s"
 * would stay green if the whole controller stopped routing, so the surviving path is asserted in the
 * same file.</p>
 *
 * <p><strong>Requirements:</strong> 2.8</p>
 */
@DisplayName("Public /responses/error removal")
class PublicErrorEndpointRemovedTest {

    private MockMvc mockMvc;
    private ResponseRoutingService routingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        routingService = mock(ResponseRoutingService.class);
        HeaderSanitizer headerSanitizer = mock(HeaderSanitizer.class);
        when(headerSanitizer.sanitize(any(jakarta.servlet.http.HttpServletRequest.class)))
            .thenReturn(Map.of());

        ResponseController controller = new ResponseController(
            routingService,
            headerSanitizer,
            mock(ConnectorMetricsService.class),
            mock(ResponseDestinationValidator.class));

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("The removed path does not route and never reaches the routing service")
    void removedErrorPathDoesNotRoute() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("error", Map.of(
            "code", "PROVIDER_ERROR",
            "message", "Provider failed to process request",
            "scope", "PROVIDER")));

        var result = mockMvc.perform(post("/test-domain/test-solution/responses/error")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("queueId", UUID.randomUUID().toString())
                .header("requestId", UUID.randomUUID().toString())
                .header("relativeServicePath", "StudentPersonals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andReturn();

        assertThat(result.getResponse().getStatus())
            .as("POST /{d}/{s}/responses/error must no longer be routable")
            .isIn(404, 405);

        // Nothing may slip through to the routing layer under a different handler.
        verify(routingService, never()).routeResponse(
            anyString(), anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("The surviving /responses endpoint still routes — the removal was surgical")
    void survivingResponseEndpointStillRoutes() throws Exception {
        UUID queueId = UUID.randomUUID();

        when(routingService.routeResponse(anyString(), anyString(), any(), any(), any(), any(),
                any(), any(), any(), any(), any()))
            .thenReturn(ResponseResultDTO.builder()
                .messageId(UUID.randomUUID())
                .queueId(queueId)
                .status("DELIVERED")
                .timestamp(Instant.now())
                .build());

        String body = objectMapper.writeValueAsString(Map.of(
            "serviceName", "StudentPersonal",
            "serviceType", "OBJECT",
            "payload", Map.of("refId", "abc123")));

        var result = mockMvc.perform(post("/test-domain/test-solution/responses")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("queueId", queueId.toString())
                .header("requestId", UUID.randomUUID().toString())
                .header("relativeServicePath", "StudentPersonals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andReturn();

        assertThat(result.getResponse().getStatus())
            .as("The response endpoint must still route; a 404/405 here would mean the removal "
                + "broke the surviving path and the test above would be passing for the wrong reason")
            .isNotIn(404, 405);
    }
}
