// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.util.HeaderSanitizer;
import org.sifunity.eventsconnector.dto.DeliveryStatus;
import org.sifunity.eventsconnector.dto.EventDistributionResponse;
import org.sifunity.eventsconnector.dto.QueueDeliveryResult;
import org.sifunity.eventsconnector.dto.DeliveryResultStatus;
import org.sifunity.eventsconnector.exception.EventValidationException;
import org.sifunity.eventsconnector.service.EventDistributionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for EventsController.
 * Tests REST API endpoint for event publication.
 */
@WebMvcTest(EventsController.class)
@org.springframework.test.context.TestPropertySource(properties = "observability.metrics.enabled=false")
class EventsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventDistributionService distributionService;

    @MockBean
    private HeaderSanitizer headerSanitizer;

    @MockBean
    private org.sifunity.broker.common.metrics.ConnectorMetricsService connectorMetricsService;

    @MockBean
    private org.sifunity.eventsconnector.metrics.EventMetrics eventMetrics;

    @BeforeEach
    void setUp() {
        when(headerSanitizer.sanitize(any(HttpServletRequest.class)))
            .thenReturn(Collections.emptyMap());
    }

    @Test
    void shouldAcceptValidEvent() throws Exception {
        // Arrange
        String messageId = UUID.randomUUID().toString();
        Instant timestamp = Instant.now();
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\",\"objectType\":\"StudentPersonal\"}");

        EventDistributionResponse response = new EventDistributionResponse(
            messageId,
            timestamp,
            3,
            DeliveryStatus.SUCCESS,
            List.of()
        );

        when(distributionService.distributeEvent(anyString(), anyString(), any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                .header("contextId", "DEFAULT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.toString()))
            .andExpect(status().isAccepted());
    }

    @Test
    void shouldRejectEventWithMissingServiceName() throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("zoneId", "zone-1")
                .header("contextId", "DEFAULT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.toString()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectEventWithMissingZoneId() throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("contextId", "DEFAULT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.toString()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectEventWithMissingEnvironmentContext() throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                .header("contextId", "DEFAULT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.toString()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn207ForPartialDelivery() throws Exception {
        // Arrange
        String messageId = UUID.randomUUID().toString();
        Instant timestamp = Instant.now();
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");

        List<QueueDeliveryResult> deliveryResults = List.of(
            new QueueDeliveryResult("queue-1", DeliveryResultStatus.SUCCESS, null),
            new QueueDeliveryResult("queue-2", DeliveryResultStatus.SUCCESS, null),
            new QueueDeliveryResult("queue-3", DeliveryResultStatus.FAILED, "Queue Service unavailable")
        );

        EventDistributionResponse response = new EventDistributionResponse(
            messageId,
            timestamp,
            3,
            DeliveryStatus.PARTIAL,
            deliveryResults
        );

        when(distributionService.distributeEvent(anyString(), anyString(), any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                .header("contextId", "DEFAULT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.toString()))
            .andExpect(status().isAccepted());
    }

    @Test
    void shouldReturn500ForAllFailures() throws Exception {
        // Arrange
        String messageId = UUID.randomUUID().toString();
        Instant timestamp = Instant.now();
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");

        List<QueueDeliveryResult> deliveryResults = List.of(
            new QueueDeliveryResult("queue-1", DeliveryResultStatus.FAILED, "Queue Service unavailable"),
            new QueueDeliveryResult("queue-2", DeliveryResultStatus.FAILED, "Queue Service unavailable")
        );

        EventDistributionResponse response = new EventDistributionResponse(
            messageId,
            timestamp,
            2,
            DeliveryStatus.FAILED,
            deliveryResults
        );

        when(distributionService.distributeEvent(anyString(), anyString(), any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                .header("contextId", "DEFAULT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.toString()))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldReturn202ForNoSubscribers() throws Exception {
        // Arrange
        String messageId = UUID.randomUUID().toString();
        Instant timestamp = Instant.now();
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");

        EventDistributionResponse response = new EventDistributionResponse(
            messageId,
            timestamp,
            0,
            DeliveryStatus.NO_SUBSCRIBERS,
            List.of()
        );

        when(distributionService.distributeEvent(anyString(), anyString(), any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                .header("contextId", "DEFAULT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.toString()))
            .andExpect(status().isAccepted());
    }

    @Test
    void shouldDefaultContextIdToDefaultWhenNotProvided() throws Exception {
        // Arrange
        String messageId = UUID.randomUUID().toString();
        Instant timestamp = Instant.now();
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");

        EventDistributionResponse response = new EventDistributionResponse(
            messageId,
            timestamp,
            1,
            DeliveryStatus.SUCCESS,
            List.of()
        );

        when(distributionService.distributeEvent(anyString(), anyString(), any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                // No contextId header
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.toString()))
            .andExpect(status().isAccepted());
    }

    @Test
    void shouldGenerateMessageIdWhenNotProvided() throws Exception {
        // Arrange
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");

        EventDistributionResponse response = new EventDistributionResponse(
            UUID.randomUUID().toString(),
            Instant.now(),
            1,
            DeliveryStatus.SUCCESS,
            List.of()
        );

        when(distributionService.distributeEvent(anyString(), anyString(), any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                // No messageId header
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.toString()))
            .andExpect(status().isAccepted());
    }

    @Test
    void shouldUseProvidedMessageId() throws Exception {
        // Arrange
        String providedMessageId = UUID.randomUUID().toString();
        JsonNode payload = objectMapper.readTree("{\"eventAction\":\"CREATE\"}");

        EventDistributionResponse response = new EventDistributionResponse(
            providedMessageId,
            Instant.now(),
            1,
            DeliveryStatus.SUCCESS,
            List.of()
        );

        when(distributionService.distributeEvent(anyString(), anyString(), any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/iowa/production/events")
                .header("X-Environment-Id", UUID.randomUUID().toString())
                .header("X-Environment-Name", "test-env")
                .header("serviceName", "StudentPersonal")
                .header("zoneId", "zone-1")
                .header("messageId", providedMessageId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload.toString()))
            .andExpect(status().isAccepted());
    }
}
