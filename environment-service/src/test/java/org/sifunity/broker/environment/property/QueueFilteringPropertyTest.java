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
 * Property-based test for queue filtering by ownerId.
 *
 * <p><strong>Property 2: Queue filtering by ownerId</strong></p>
 *
 * <p>For any list of queues returned by the queue-service containing a mix of ownerIds,
 * the QueueServiceClient SHALL select for deletion only those queues whose {@code ownerId}
 * matches the environment being deleted. The count of selected queues SHALL equal the count
 * of queues in the response whose ownerId matches.</p>
 *
 * <p><strong>Validates: Requirements 1.3</strong></p>
 */
class QueueFilteringPropertyTest {

    private static final String BASE_URL = "http://queue-service:8080";
    private static final String DOMAIN_ID = "testDomain";
    private static final String SOLUTION_ID = "testSolution";

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
     * Property 2: Queue filtering by ownerId
     *
     * <p><strong>Validates: Requirements 1.3</strong></p>
     *
     * <p>Generates random queue lists with mixed ownerIds (1–50 queues, random UUIDs).
     * Verifies only queues whose ownerId matches environment UUID are selected for deletion.
     * Verifies count of selected queues equals count of matching queues in response.</p>
     */
    @Property(tries = 20)
    @Tag("Feature: environment-delete-queue-cleanup, Property 2: Queue filtering by ownerId")
    void onlyQueuesMatchingEnvironmentOwnerIdAreDeleted(
            @ForAll("testScenarios") TestScenario scenario) {

        // Reset mock to isolate each try
        reset(restTemplate);

        UUID environmentId = scenario.environmentId();
        List<QueueEntry> queueList = scenario.queues();

        // Build JSON response matching queue-service format
        String jsonResponse = buildQueueListJson(queueList);

        // Mock GET to return queue list
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(jsonResponse, HttpStatus.OK));

        // Mock DELETE to return 202 Accepted
        when(restTemplate.exchange(anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.ACCEPTED));

        // Call method under test
        queueServiceClient.deleteQueuesForEnvironment(environmentId, DOMAIN_ID, SOLUTION_ID);

        // Count how many queues in the list have ownerId matching environmentId
        long expectedDeleteCount = queueList.stream()
                .filter(q -> q.ownerId().equals(environmentId))
                .count();

        // Verify DELETE was called exactly N times where N = count of matching queues
        if (expectedDeleteCount == 0) {
            verify(restTemplate, never()).exchange(
                    anyString(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class));
        } else {
            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
            verify(restTemplate, times((int) expectedDeleteCount))
                    .exchange(urlCaptor.capture(), eq(HttpMethod.DELETE), any(HttpEntity.class), eq(Void.class));

            // Verify each DELETE URL contains the correct queueId
            List<String> deletedUrls = urlCaptor.getAllValues();
            List<UUID> expectedQueueIds = queueList.stream()
                    .filter(q -> q.ownerId().equals(environmentId))
                    .map(QueueEntry::id)
                    .toList();

            for (UUID expectedQueueId : expectedQueueIds) {
                String expectedUrlSuffix = "/queues/" + expectedQueueId;
                assertThat(deletedUrls)
                        .anyMatch(url -> url.contains(expectedUrlSuffix));
            }

            // Verify no non-matching queue was deleted
            List<UUID> nonMatchingQueueIds = queueList.stream()
                    .filter(q -> !q.ownerId().equals(environmentId))
                    .map(QueueEntry::id)
                    .toList();

            for (UUID nonMatchingId : nonMatchingQueueIds) {
                String unexpectedUrlSuffix = "/queues/" + nonMatchingId;
                assertThat(deletedUrls)
                        .noneMatch(url -> url.contains(unexpectedUrlSuffix));
            }
        }
    }

    // ========== Helpers ==========

    private String buildQueueListJson(List<QueueEntry> queues) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"queues\":{\"queue\":[");
        for (int i = 0; i < queues.size(); i++) {
            QueueEntry q = queues.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"id\":\"").append(q.id()).append("\",")
              .append("\"ownerId\":\"").append(q.ownerId()).append("\",")
              .append("\"name\":\"queue-").append(i).append("\"}");
        }
        sb.append("]}}");
        return sb.toString();
    }

    // ========== Arbitraries ==========

    /**
     * Generates test scenarios with a random environmentId and a queue list of 1–50 entries
     * where some queues have ownerId matching the environmentId and some do not.
     */
    @Provide
    Arbitrary<TestScenario> testScenarios() {
        return Arbitraries.randomValue(random -> {
            UUID environmentId = UUID.randomUUID();
            int size = random.nextInt(50) + 1; // 1–50 queues
            List<QueueEntry> queues = new ArrayList<>();

            for (int i = 0; i < size; i++) {
                UUID queueId = UUID.randomUUID();
                // ~40% chance of matching the environmentId
                UUID ownerId = random.nextBoolean() && random.nextBoolean()
                        ? UUID.randomUUID()
                        : environmentId;
                queues.add(new QueueEntry(queueId, ownerId));
            }

            return new TestScenario(environmentId, queues);
        });
    }

    // ========== Records ==========

    /**
     * Test scenario containing an environmentId and a list of queues with mixed ownerIds.
     */
    record TestScenario(UUID environmentId, List<QueueEntry> queues) {}

    /**
     * Simple record representing a queue entry with id and ownerId.
     */
    record QueueEntry(UUID id, UUID ownerId) {}
}
