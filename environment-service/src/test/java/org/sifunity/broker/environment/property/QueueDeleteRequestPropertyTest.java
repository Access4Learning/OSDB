// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.ArgumentCaptor;
import org.sifunity.broker.environment.service.QueueServiceClient;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based test for DELETE issued per owned queue with correct headers.
 *
 * <p><strong>Property 3: DELETE issued for each owned queue with correct headers</strong></p>
 *
 * <p>For any non-empty set of owned queues, the QueueServiceClient SHALL issue exactly one
 * DELETE request per queue to {@code {baseUrl}/{domainId}/{solutionId}/queues/{queueId}}
 * with {@code X-Environment-Id} set to the environment's UUID and
 * {@code X-Environment-Fingerprint} set to {@code "broker-services"}.</p>
 *
 * <p><strong>Validates: Requirements 2.1, 2.5</strong></p>
 */
class QueueDeleteRequestPropertyTest {

    private static final String BASE_URL = "http://queue-service:8080";

    private RestTemplate restTemplate;
    private QueueServiceClient queueServiceClient;

    @BeforeProperty
    void setUp() {
        restTemplate = mock(RestTemplate.class);

        RestTemplateBuilder restTemplateBuilder = mock(RestTemplateBuilder.class);
        when(restTemplateBuilder.setConnectTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(any(Duration.class))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);

        ObjectMapper objectMapper = new ObjectMapper();

        queueServiceClient = new QueueServiceClient(
                BASE_URL,
                restTemplateBuilder,
                5000,
                10000,
                objectMapper);
    }

    /**
     * Property 3: DELETE issued for each owned queue with correct headers
     *
     * <p><strong>Validates: Requirements 2.1, 2.5</strong></p>
     *
     * <p>Generates random sets of 1–20 owned queue UUIDs. Mocks RestTemplate, verifies
     * exactly one DELETE per queue. Verifies each DELETE URL matches
     * {@code {baseUrl}/{domainId}/{solutionId}/queues/{queueId}}. Verifies headers include
     * {@code X-Environment-Id} and {@code X-Environment-Fingerprint: broker-services}.</p>
     */
    @Property(tries = 20)
    @Tag("Feature: environment-delete-queue-cleanup, Property 3: DELETE issued per owned queue")
    void deleteIssuedPerOwnedQueueWithCorrectHeaders(
            @ForAll("testScenarios") TestScenario scenario) {

        // Reset mock to isolate each try
        reset(restTemplate);

        UUID environmentId = scenario.environmentId();
        String domainId = scenario.domainId();
        String solutionId = scenario.solutionId();
        List<UUID> ownedQueueIds = scenario.ownedQueueIds();

        // Build JSON response where ALL queues have ownerId == environmentId
        String jsonResponse = buildAllOwnedQueueListJson(ownedQueueIds, environmentId);

        // Mock GET to return queue list
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(jsonResponse, HttpStatus.OK));

        // Mock DELETE to return 202 Accepted
        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.ACCEPTED));

        // Call method under test
        queueServiceClient.deleteQueuesForEnvironment(environmentId, domainId, solutionId);

        // Capture all DELETE calls
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Void>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        verify(restTemplate, times(ownedQueueIds.size()))
                .exchange(urlCaptor.capture(), eq(HttpMethod.DELETE), entityCaptor.capture(), eq(Void.class));

        List<String> capturedUrls = urlCaptor.getAllValues();
        List<HttpEntity<Void>> capturedEntities = entityCaptor.getAllValues();

        // Verify exactly one DELETE per queue UUID
        assertThat(capturedUrls).hasSize(ownedQueueIds.size());

        // Verify each DELETE URL matches {baseUrl}/{domainId}/{solutionId}/queues/{queueId}
        for (UUID queueId : ownedQueueIds) {
            String expectedUrl = BASE_URL + "/" + domainId + "/" + solutionId + "/queues/" + queueId;
            assertThat(capturedUrls)
                    .as("Expected DELETE URL for queue %s", queueId)
                    .contains(expectedUrl);
        }

        // Verify each DELETE request has correct headers
        for (HttpEntity<Void> entity : capturedEntities) {
            assertThat(entity.getHeaders().getFirst("X-Environment-Id"))
                    .as("X-Environment-Id header")
                    .isEqualTo(environmentId.toString());

            assertThat(entity.getHeaders().getFirst("X-Environment-Fingerprint"))
                    .as("X-Environment-Fingerprint header")
                    .isEqualTo("broker-services");
        }
    }

    // ========== Helpers ==========

    private String buildAllOwnedQueueListJson(List<UUID> queueIds, UUID environmentId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"queues\":{\"queue\":[");
        for (int i = 0; i < queueIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"id\":\"").append(queueIds.get(i)).append("\",")
              .append("\"ownerId\":\"").append(environmentId).append("\",")
              .append("\"name\":\"queue-").append(i).append("\"}");
        }
        sb.append("]}}");
        return sb.toString();
    }

    // ========== Arbitraries ==========

    /**
     * Generates test scenarios with random environmentId, domainId, solutionId,
     * and a list of 1–20 owned queue UUIDs.
     */
    @Provide
    Arbitrary<TestScenario> testScenarios() {
        Arbitrary<UUID> envIds = Arbitraries.randomValue(random -> UUID.randomUUID());
        Arbitrary<String> domainIds = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(3)
                .ofMaxLength(20);
        Arbitrary<String> solutionIds = Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(3)
                .ofMaxLength(20);
        Arbitrary<List<UUID>> queueIdLists = Arbitraries.randomValue(random -> UUID.randomUUID())
                .list()
                .ofMinSize(1)
                .ofMaxSize(20);

        return Combinators.combine(envIds, domainIds, solutionIds, queueIdLists)
                .as(TestScenario::new);
    }

    // ========== Records ==========

    /**
     * Test scenario containing environmentId, domainId, solutionId, and owned queue UUIDs.
     */
    record TestScenario(UUID environmentId, String domainId, String solutionId, List<UUID> ownedQueueIds) {}
}
