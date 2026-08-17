// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.unit;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.metrics.ConnectorMetricsService;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.util.HeaderSanitizer;
import org.sifunity.responsesconnector.controller.ResponseController;
import org.sifunity.responsesconnector.dto.ResponseDTO;
import org.sifunity.responsesconnector.dto.ResponseResultDTO;
import org.sifunity.responsesconnector.service.ResponseDestinationValidator;
import org.sifunity.responsesconnector.service.ResponseRoutingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ResponseController.
 *
 * Tests the SIF 3.7 header-based endpoints using relativeServicePath.
 */
@ExtendWith(MockitoExtension.class)
class ResponseControllerTest {

    @Mock
    private ResponseRoutingService routingService;

    @Mock
    private HeaderSanitizer headerSanitizer;

    @Mock
    private ConnectorMetricsService connectorMetricsService;

    @Mock
    private HttpServletRequest httpServletRequest;

    private ResponseController controller;

    @BeforeEach
    void setUp() {
        controller = new ResponseController(routingService, headerSanitizer, connectorMetricsService, mock(ResponseDestinationValidator.class));
        lenient().when(headerSanitizer.sanitize(any(HttpServletRequest.class))).thenReturn(Collections.emptyMap());
        lenient().when(httpServletRequest.getMethod()).thenReturn("POST");
    }

    // ========================================
    // Response endpoint with relativeServicePath
    // ========================================

    @Nested
    class ResponseEndpoint {

        @Test
        void shouldHandleResponseWithRelativeServicePath() {
            UUID environmentId = UUID.randomUUID();
            String environmentName = "test-environment";
            UUID queueId = UUID.randomUUID();
            String requestId = UUID.randomUUID().toString();

            Map<String, Object> payload = new HashMap<>();
            payload.put("refId", "abc123");
            payload.put("name", "John Doe");

            UUID messageId = UUID.randomUUID();
            ResponseResultDTO expectedResult = ResponseResultDTO.builder()
                    .messageId(messageId)
                    .queueId(queueId)
                    .status("DELIVERED")
                    .timestamp(Instant.now())
                    .build();

            when(routingService.routeResponse(eq("test-domain"), eq("test-solution"),
                    eq(environmentId), eq(environmentName), eq(queueId), eq(requestId),
                    any(ResponseDTO.class), any(), any(), any(), any()))
                    .thenReturn(expectedResult);

            ResponseEntity<ResponseResultDTO> response = controller.handleResponse(
                    "test-domain", "test-solution",
                    environmentId, environmentName, queueId, requestId,
                    "StudentPersonals", "OBJECT", null, null, payload, httpServletRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessageId()).isEqualTo(messageId);
            assertThat(response.getBody().getQueueId()).isEqualTo(queueId);
            assertThat(response.getBody().getStatus()).isEqualTo("DELIVERED");
        }

        @Test
        void shouldParseServiceNameFromRelativeServicePathWithMatrixParams() {
            UUID environmentId = UUID.randomUUID();
            UUID queueId = UUID.randomUUID();
            String requestId = UUID.randomUUID().toString();

            Map<String, Object> payload = Map.of("refId", "abc123");

            ResponseResultDTO expectedResult = ResponseResultDTO.builder()
                    .messageId(UUID.randomUUID())
                    .queueId(queueId)
                    .status("DELIVERED")
                    .timestamp(Instant.now())
                    .build();

            when(routingService.routeResponse(anyString(), anyString(), any(), anyString(),
                    any(), any(), any(ResponseDTO.class), any(), any(), any(), any()))
                    .thenReturn(expectedResult);

            // relativeServicePath with matrix params
            controller.handleResponse("test-domain", "test-solution",
                    environmentId, "test-env", queueId, requestId,
                    "StudentPersonals;contextId=DEFAULT;zoneId=test-zone",
                    "OBJECT", null, null, payload, httpServletRequest);

            // serviceName in DTO should be "StudentPersonals" (parsed from relativeServicePath)
            verify(routingService).routeResponse(anyString(), anyString(), any(), anyString(),
                    any(), any(), argThat(dto ->
                            "StudentPersonals".equals(dto.getServiceName()) &&
                            "OBJECT".equals(dto.getServiceType()) &&
                            dto.getPayload() != null
                    ), any(), any(), any(), any());
        }

        @Test
        void shouldAcceptNullPayloadForEmptyQueryResults() {
            UUID environmentId = UUID.randomUUID();
            UUID queueId = UUID.randomUUID();

            ResponseResultDTO expectedResult = ResponseResultDTO.builder()
                    .messageId(UUID.randomUUID())
                    .queueId(queueId)
                    .status("DELIVERED")
                    .timestamp(Instant.now())
                    .build();

            when(routingService.routeResponse(anyString(), anyString(), any(), anyString(),
                    any(), any(), any(ResponseDTO.class), any(), any(), any(), any()))
                    .thenReturn(expectedResult);

            ResponseEntity<ResponseResultDTO> response = controller.handleResponse(
                    "test-domain", "test-solution",
                    environmentId, "test-env", queueId, null,
                    "StudentPersonals", "OBJECT", null, null, null, httpServletRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }

        @Test
        void shouldDefaultServiceTypeToObject() {
            UUID environmentId = UUID.randomUUID();
            UUID queueId = UUID.randomUUID();

            ResponseResultDTO expectedResult = ResponseResultDTO.builder()
                    .messageId(UUID.randomUUID())
                    .queueId(queueId)
                    .status("DELIVERED")
                    .timestamp(Instant.now())
                    .build();

            when(routingService.routeResponse(anyString(), anyString(), any(), anyString(),
                    any(), any(), any(ResponseDTO.class), any(), any(), any(), any()))
                    .thenReturn(expectedResult);

            controller.handleResponse("test-domain", "test-solution",
                    environmentId, "test-env", queueId, null,
                    "StudentPersonals", "OBJECT", null, null, Map.of(), httpServletRequest);

            verify(routingService).routeResponse(anyString(), anyString(), any(), anyString(),
                    any(), any(), argThat(dto -> "OBJECT".equals(dto.getServiceType())), any(), any(), any(), any());
        }
    }


    // ========================================
    // extractServiceName unit tests
    // ========================================

    @Nested
    class ExtractServiceName {

        @Test
        void shouldExtractServiceNameFromPlainRelativeServicePath() {
            assertThat(ResponseController.extractServiceName("StudentPersonals"))
                    .isEqualTo("StudentPersonals");
        }

        @Test
        void shouldExtractServiceNameBeforeSemicolon() {
            assertThat(ResponseController.extractServiceName("StudentPersonals;contextId=DEFAULT"))
                    .isEqualTo("StudentPersonals");
        }

        @Test
        void shouldExtractServiceNameWithMultipleMatrixParams() {
            assertThat(ResponseController.extractServiceName(
                    "StudentPersonals;contextId=DEFAULT;zoneId=test-zone"))
                    .isEqualTo("StudentPersonals");
        }

        @Test
        void shouldStripLeadingSlash() {
            assertThat(ResponseController.extractServiceName("/StudentPersonals"))
                    .isEqualTo("StudentPersonals");
        }

        @Test
        void shouldStripLeadingSlashWithMatrixParams() {
            assertThat(ResponseController.extractServiceName("/StudentPersonals;contextId=DEFAULT"))
                    .isEqualTo("StudentPersonals");
        }

        @Test
        void shouldTruncateAtQueryParams() {
            assertThat(ResponseController.extractServiceName("StudentPersonals?param=value"))
                    .isEqualTo("StudentPersonals");
        }

        @Test
        void shouldTruncateAtQueryParamsWithLeadingSlash() {
            assertThat(ResponseController.extractServiceName("/StudentPersonals?param=value&other=x"))
                    .isEqualTo("StudentPersonals");
        }

        @Test
        void shouldTruncateAtSubPath() {
            assertThat(ResponseController.extractServiceName("StudentPersonals/uuid123"))
                    .isEqualTo("StudentPersonals");
        }

        @Test
        void shouldHandleCombinedFormat() {
            assertThat(ResponseController.extractServiceName("/StudentPersonals;zoneId=z1/uuid123?x=1"))
                    .isEqualTo("StudentPersonals");
        }

        @Test
        void shouldReturnNullWhenPathIsNull() {
            assertThat(ResponseController.extractServiceName(null)).isNull();
        }

        @Test
        void shouldReturnNullWhenPathIsBlank() {
            assertThat(ResponseController.extractServiceName("  ")).isNull();
        }
    }

    // ========================================
    // Shared behavior tests
    // ========================================

    @Test
    void shouldPassEnvironmentContextToRoutingService() {
        UUID environmentId = UUID.randomUUID();
        String environmentName = "production";
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();

        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
                .messageId(UUID.randomUUID())
                .queueId(queueId)
                .status("DELIVERED")
                .timestamp(Instant.now())
                .build();

        when(routingService.routeResponse(anyString(), anyString(), any(), anyString(),
                any(), any(), any(ResponseDTO.class), any(), any(), any(), any()))
                .thenReturn(expectedResult);

        controller.handleResponse("test-domain", "test-solution",
                environmentId, environmentName, queueId, requestId,
                "StudentPersonals", "OBJECT", null, null, new HashMap<>(), httpServletRequest);

        verify(routingService).routeResponse(anyString(), anyString(),
                eq(environmentId), eq(environmentName),
                eq(queueId), eq(requestId), any(ResponseDTO.class), any(), any(), any(), any());
    }

    @Test
    void shouldDelegateToRoutingServiceForResponse() {
        UUID environmentId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();

        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
                .messageId(UUID.randomUUID())
                .queueId(queueId)
                .status("DELIVERED")
                .timestamp(Instant.now())
                .build();

        when(routingService.routeResponse(anyString(), anyString(), any(), anyString(),
                any(), any(), any(ResponseDTO.class), any(), any(), any(), any()))
                .thenReturn(expectedResult);

        controller.handleResponse("test-domain", "test-solution",
                environmentId, "test-env", queueId, requestId,
                "StudentPersonals", "OBJECT", null, null, new HashMap<>(), httpServletRequest);

        verify(routingService, times(1)).routeResponse(
                eq("test-domain"), eq("test-solution"),
                eq(environmentId), eq("test-env"),
                eq(queueId), eq(requestId), any(ResponseDTO.class), any(), any(), any(), any());
    }

    @Test
    void shouldReturnResultFromRoutingService() {
        UUID environmentId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID expectedMessageId = UUID.randomUUID();
        Instant expectedTimestamp = Instant.now();

        ResponseResultDTO expectedResult = ResponseResultDTO.builder()
                .messageId(expectedMessageId)
                .queueId(queueId)
                .status("DELIVERED")
                .timestamp(expectedTimestamp)
                .build();

        when(routingService.routeResponse(anyString(), anyString(), any(), anyString(),
                any(), any(), any(ResponseDTO.class), any(), any(), any(), any()))
                .thenReturn(expectedResult);

        ResponseEntity<ResponseResultDTO> response = controller.handleResponse(
                "test-domain", "test-solution",
                environmentId, "test-env", queueId, requestId,
                "StudentPersonals", "OBJECT", null, null, new HashMap<>(), httpServletRequest);

        assertThat(response.getBody()).isEqualTo(expectedResult);
        assertThat(response.getBody().getMessageId()).isEqualTo(expectedMessageId);
        assertThat(response.getBody().getQueueId()).isEqualTo(queueId);
        assertThat(response.getBody().getStatus()).isEqualTo("DELIVERED");
        assertThat(response.getBody().getTimestamp()).isEqualTo(expectedTimestamp);
    }
}
