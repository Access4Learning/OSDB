// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link QueueServiceClient}.
 *
 * <p>Validates disabled/enabled behavior, HTTP response handling,
 * error resilience, and URL scheme acceptance.</p>
 */
@ExtendWith(MockitoExtension.class)
class QueueServiceClientTest {

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    private QueueServiceClient createClient(String baseUrl) {
        restTemplate = mock(RestTemplate.class);
        RestTemplateBuilder builder = mock(RestTemplateBuilder.class);
        when(builder.setConnectTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.setReadTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.build()).thenReturn(restTemplate);
        objectMapper = new ObjectMapper();
        return new QueueServiceClient(baseUrl, builder, 5000, 10000, objectMapper);
    }

    // ========== isEnabled() tests ==========

    @Test
    void isEnabled_returnsFalse_whenBaseUrlIsEmpty() {
        QueueServiceClient client = createClient("");
        assertThat(client.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_returnsFalse_whenBaseUrlIsNull() {
        QueueServiceClient client = createClient(null);
        assertThat(client.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_returnsFalse_whenBaseUrlIsBlank() {
        QueueServiceClient client = createClient("   ");
        assertThat(client.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_returnsTrue_whenBaseUrlIsConfigured() {
        QueueServiceClient client = createClient("http://queue-service:8080");
        assertThat(client.isEnabled()).isTrue();
    }

    // ========== deleteQueuesForEnvironment when disabled ==========

    @Test
    void deleteQueuesForEnvironment_returnsImmediately_whenDisabled() {
        QueueServiceClient client = createClient("");
        UUID envId = UUID.randomUUID();

        client.deleteQueuesForEnvironment(envId, "domain1", "solution1");

        // No HTTP calls should be made
        verifyNoInteractions(restTemplate);
    }

    // ========== Empty queue list ==========

    @Test
    void deleteQueuesForEnvironment_noDeleteCalls_whenQueueListEmpty() {
        QueueServiceClient client = createClient("http://queue-service:8080");
        UUID envId = UUID.randomUUID();

        String emptyResponse = "{\"queues\":{\"queue\":[]}}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(emptyResponse, HttpStatus.OK));

        client.deleteQueuesForEnvironment(envId, "domain1", "solution1");

        // Only GET call, no DELETE calls
        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class));
        verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.DELETE), any(), eq(Void.class));
    }

    // ========== HTTP 202 response handled without error ==========

    @Test
    void deleteQueuesForEnvironment_handles202_withoutError() {
        QueueServiceClient client = createClient("http://queue-service:8080");
        UUID envId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        String responseWithQueue = buildQueueListResponse(queueId, envId);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(responseWithQueue, HttpStatus.OK));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.ACCEPTED));

        client.deleteQueuesForEnvironment(envId, "domain1", "solution1");

        // Verify DELETE was called for the queue
        verify(restTemplate, times(1)).exchange(
                contains("/queues/" + queueId), eq(HttpMethod.DELETE), any(), eq(Void.class));
    }

    // ========== HTTP 404 response continues to next queue ==========

    @Test
    void deleteQueuesForEnvironment_handles404_andContinuesToNextQueue() {
        QueueServiceClient client = createClient("http://queue-service:8080");
        UUID envId = UUID.randomUUID();
        UUID queueId1 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();

        String responseWithQueues = buildQueueListResponse(envId, queueId1, queueId2);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(responseWithQueues, HttpStatus.OK));

        // First DELETE returns 404, second returns 202
        when(restTemplate.exchange(contains(queueId1.toString()), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));
        when(restTemplate.exchange(contains(queueId2.toString()), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.ACCEPTED));

        client.deleteQueuesForEnvironment(envId, "domain1", "solution1");

        // Both DELETEs attempted
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.DELETE), any(), eq(Void.class));
    }

    // ========== Connection timeout continues to next queue ==========

    @Test
    void deleteQueuesForEnvironment_handlesConnectionTimeout_andContinuesToNextQueue() {
        QueueServiceClient client = createClient("http://queue-service:8080");
        UUID envId = UUID.randomUUID();
        UUID queueId1 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();

        String responseWithQueues = buildQueueListResponse(envId, queueId1, queueId2);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(responseWithQueues, HttpStatus.OK));

        // First DELETE times out, second succeeds
        when(restTemplate.exchange(contains(queueId1.toString()), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new ResourceAccessException("Connection timed out", new SocketTimeoutException("connect timed out")));
        when(restTemplate.exchange(contains(queueId2.toString()), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.ACCEPTED));

        client.deleteQueuesForEnvironment(envId, "domain1", "solution1");

        // Both DELETEs attempted
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.DELETE), any(), eq(Void.class));
    }

    // ========== HTTP 5xx response continues to next queue ==========

    @Test
    void deleteQueuesForEnvironment_handles5xx_andContinuesToNextQueue() {
        QueueServiceClient client = createClient("http://queue-service:8080");
        UUID envId = UUID.randomUUID();
        UUID queueId1 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();

        String responseWithQueues = buildQueueListResponse(envId, queueId1, queueId2);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(responseWithQueues, HttpStatus.OK));

        // First DELETE returns 500, second returns 202
        when(restTemplate.exchange(contains(queueId1.toString()), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", HttpHeaders.EMPTY, new byte[0], null));
        when(restTemplate.exchange(contains(queueId2.toString()), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.ACCEPTED));

        client.deleteQueuesForEnvironment(envId, "domain1", "solution1");

        // Both DELETEs attempted
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.DELETE), any(), eq(Void.class));
    }

    // ========== Both HTTP and HTTPS base URLs accepted ==========

    @Test
    void isEnabled_acceptsHttpBaseUrl() {
        QueueServiceClient client = createClient("http://queue-service:8080");
        assertThat(client.isEnabled()).isTrue();
    }

    @Test
    void isEnabled_acceptsHttpsBaseUrl() {
        QueueServiceClient client = createClient("https://queue-service.internal:443");
        assertThat(client.isEnabled()).isTrue();
    }

    @Test
    void deleteQueuesForEnvironment_worksWithHttpsBaseUrl() {
        QueueServiceClient client = createClient("https://queue-service.internal:443");
        UUID envId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        String responseWithQueue = buildQueueListResponse(queueId, envId);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(responseWithQueue, HttpStatus.OK));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.ACCEPTED));

        client.deleteQueuesForEnvironment(envId, "domain1", "solution1");

        // Verify HTTPS URL used in GET call
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(), eq(String.class));
        assertThat(urlCaptor.getValue()).startsWith("https://queue-service.internal:443/");
    }

    // ========== Helper methods ==========

    /**
     * Builds a JSON response with a single queue owned by the given environment.
     */
    private String buildQueueListResponse(UUID queueId, UUID ownerId) {
        return String.format(
                "{\"queues\":{\"queue\":[{\"id\":\"%s\",\"ownerId\":\"%s\",\"name\":\"test-queue\"}]}}",
                queueId, ownerId);
    }

    /**
     * Builds a JSON response with multiple queues all owned by the given environment.
     */
    private String buildQueueListResponse(UUID ownerId, UUID... queueIds) {
        StringBuilder sb = new StringBuilder("{\"queues\":{\"queue\":[");
        for (int i = 0; i < queueIds.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format(
                    "{\"id\":\"%s\",\"ownerId\":\"%s\",\"name\":\"queue-%d\"}",
                    queueIds[i], ownerId, i));
        }
        sb.append("]}}");
        return sb.toString();
    }
}
