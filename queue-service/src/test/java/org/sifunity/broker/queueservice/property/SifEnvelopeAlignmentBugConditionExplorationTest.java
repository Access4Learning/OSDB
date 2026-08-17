// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.queueservice.controller.QueueController;
import org.sifunity.broker.queueservice.dto.CreateQueueRequest;
import org.sifunity.broker.queueservice.service.QueueCleanupService;
import org.sifunity.broker.queueservice.service.QueueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bug Condition Exploration Test for SIF 3.7 Envelope and URL Alignment in queue-service.
 *
 * <p><strong>CRITICAL: This test MUST FAIL on unfixed code — failure confirms the bug exists.</strong></p>
 *
 * <p>The queue-service currently uses collection URLs ({@code POST /queues}) with flat
 * request/response bodies instead of singular resource URLs ({@code POST /queues/queue})
 * with wrapped envelopes ({@code {"queue": {...}}}). Single-resource GET responses also
 * return flat QueueDTO objects instead of wrapped envelopes.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5</strong></p>
 */
class SifEnvelopeAlignmentBugConditionExplorationTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private QueueService queueService;

    private MockMvc buildMockMvc() {
        queueService = mock(QueueService.class);
        QueueCleanupService cleanupService = mock(QueueCleanupService.class);
        QueueController controller = new QueueController(queueService, cleanupService, "test-internal-token");
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    /**
     * Property 1: POST to singular URL {@code /{domainId}/{solutionId}/queues/queue} with
     * wrapped body {@code {"queue":{"name":"...","ownerId":"..."}}} should return 201 with
     * {@code "queue"} key in response.
     *
     * <p><strong>Expected on UNFIXED code:</strong> FAILS — returns 404 (no handler at /queue sub-path).</p>
     * <p><strong>Expected on FIXED code:</strong> PASSES — returns 201 with wrapped response.</p>
     *
     * <p><strong>Validates: Requirements 1.1, 1.2, 1.3</strong></p>
     */
    @Property(tries = 20)
    @Label("POST to /queues/queue with wrapped body should return 201 with queue key in response")
    void postToSingularUrlWithWrappedBodyShouldReturn201(
            @ForAll("queueNames") String name,
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        mockMvc = buildMockMvc();
        UUID ownerId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        QueueDTO createdQueue = new QueueDTO(
                queueId, name, ownerId, "IMMEDIATE",
                "uri/" + queueId, 1, Instant.now(), null, null, 3, 5000L, 0L);

        when(queueService.createQueue(eq(name), eq(ownerId), isNull(), isNull(), isNull(),
                eq(domainId), eq(solutionId), any()))
                .thenReturn(createdQueue);

        String requestBody = String.format(
                "{\"queue\":{\"name\":\"%s\",\"ownerId\":\"%s\"}}", name, ownerId);

        MvcResult result = mockMvc.perform(post("/{domainId}/{solutionId}/queues/queue", domainId, solutionId)
                        .header("X-Environment-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();

        // On UNFIXED code: 404 — no handler at /queues/queue
        // On FIXED code: 201 with {"queue":{...}}
        assertThat(result.getResponse().getStatus())
                .as("POST to /queues/queue should return 201 Created")
                .isEqualTo(HttpStatus.CREATED.value());

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody)
                .as("Response body should contain 'queue' wrapper key")
                .contains("\"queue\"");
    }

    /**
     * Property 2: POST to collection URL {@code /{domainId}/{solutionId}/queues} should
     * return 405 Method Not Allowed.
     *
     * <p><strong>Expected on UNFIXED code:</strong> FAILS — returns 201 (currently creates queue at collection URL).</p>
     * <p><strong>Expected on FIXED code:</strong> PASSES — returns 405.</p>
     *
     * <p><strong>Validates: Requirements 1.5</strong></p>
     */
    @Property(tries = 20)
    @Label("POST to /queues collection URL should return 405 Method Not Allowed")
    void postToCollectionUrlShouldReturn405(
            @ForAll("queueNames") String name,
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        mockMvc = buildMockMvc();
        UUID ownerId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        QueueDTO createdQueue = new QueueDTO(
                queueId, name, ownerId, "IMMEDIATE",
                "uri/" + queueId, 1, Instant.now(), null, null, 3, 5000L, 0L);

        when(queueService.createQueue(anyString(), any(), any(), any(), any(),
                anyString(), anyString(), any()))
                .thenReturn(createdQueue);

        String requestBody = String.format(
                "{\"name\":\"%s\",\"ownerId\":\"%s\"}", name, ownerId);

        MvcResult result = mockMvc.perform(post("/{domainId}/{solutionId}/queues", domainId, solutionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();

        // On UNFIXED code: 201 — currently creates queue at collection URL (BUG)
        // On FIXED code: 405 Method Not Allowed
        assertThat(result.getResponse().getStatus())
                .as("POST to /queues collection URL should return 405 Method Not Allowed")
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
    }

    /**
     * Property 3: GET {@code /{domainId}/{solutionId}/queues/{id}} should return response
     * body containing {@code "queue"} wrapper key.
     *
     * <p><strong>Expected on UNFIXED code:</strong> FAILS — currently returns flat QueueDTO without wrapper.</p>
     * <p><strong>Expected on FIXED code:</strong> PASSES — returns {"queue":{...}}.</p>
     *
     * <p><strong>Validates: Requirements 1.4</strong></p>
     */
    @Property(tries = 20)
    @Label("GET /queues/{id} should return response body with queue wrapper key")
    void getByIdShouldReturnWrappedResponse(
            @ForAll("queueNames") String name,
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        mockMvc = buildMockMvc();
        UUID ownerId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        QueueDTO queue = new QueueDTO(
                queueId, name, ownerId, "IMMEDIATE",
                "uri/" + queueId, 1, Instant.now(), null, null, 3, 5000L, 0L);

        when(queueService.getQueue(eq(queueId), eq(domainId), eq(solutionId)))
                .thenReturn(queue);

        MvcResult result = mockMvc.perform(get("/{domainId}/{solutionId}/queues/{queueId}",
                        domainId, solutionId, queueId)
                        .header("X-Environment-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        // Parse the JSON and check for "queue" wrapper key at root level
        var jsonNode = objectMapper.readTree(responseBody);

        // On UNFIXED code: root keys are "id", "name", "ownerId" etc. (flat QueueDTO)
        // On FIXED code: root key is "queue" containing the QueueDTO fields
        assertThat(jsonNode.has("queue"))
                .as("GET /queues/{id} response should have 'queue' wrapper key at root level, "
                        + "but got: %s", responseBody)
                .isTrue();
    }

    /**
     * Property 4: POST to {@code /{domainId}/{solutionId}/queues/queue} with body missing
     * the {@code "queue"} key (e.g., {@code {"name":"q1"}}) should return 400 Bad Request.
     *
     * <p><strong>Expected on UNFIXED code:</strong> FAILS — returns 404 (no handler at /queue sub-path).</p>
     * <p><strong>Expected on FIXED code:</strong> PASSES — returns 400 for missing wrapper key.</p>
     *
     * <p><strong>Validates: Requirements 1.2</strong></p>
     */
    @Property(tries = 20)
    @Label("POST to /queues/queue with missing queue wrapper key should return 400")
    void postToSingularUrlWithoutWrapperKeyShouldReturn400(
            @ForAll("queueNames") String name,
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        mockMvc = buildMockMvc();

        // Send body without "queue" wrapper key — just flat fields
        String requestBody = String.format("{\"name\":\"%s\"}", name);

        MvcResult result = mockMvc.perform(post("/{domainId}/{solutionId}/queues/queue", domainId, solutionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();

        // On UNFIXED code: 404 — no handler at /queues/queue
        // On FIXED code: 400 Bad Request (missing "queue" wrapper key)
        assertThat(result.getResponse().getStatus())
                .as("POST to /queues/queue without 'queue' wrapper key should return 400 Bad Request")
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> queueNames() {
        return Arbitraries.of("test-queue", "events-q", "student-data", "notifications", "audit-log");
    }

    @Provide
    Arbitrary<String> domainIds() {
        return Arbitraries.of("test-domain", "school-district-1", "state-edu");
    }

    @Provide
    Arbitrary<String> solutionIds() {
        return Arbitraries.of("prod-solution", "test-solution", "staging");
    }
}
