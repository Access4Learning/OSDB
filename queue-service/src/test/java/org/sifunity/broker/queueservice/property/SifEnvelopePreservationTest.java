// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.queueservice.controller.QueueController;
import org.sifunity.broker.queueservice.service.QueueCleanupService;
import org.sifunity.broker.queueservice.service.QueueService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Preservation Property Tests for queue-service.
 *
 * <p>These tests capture the EXISTING behavior of endpoints that must remain unchanged
 * after the SIF 3.7 envelope alignment fix. They run on UNFIXED code and MUST PASS,
 * confirming the baseline behavior to preserve.</p>
 *
 * <p>Preservation scope: collection GETs, DELETEs, stats, purge, ownership enforcement.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.7, 3.8</strong></p>
 */
class SifEnvelopePreservationTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.findAndRegisterModules();
    }

    private QueueService queueService;
    private QueueCleanupService queueCleanupService;

    private MockMvc buildMockMvc() {
        queueService = mock(QueueService.class);
        queueCleanupService = mock(QueueCleanupService.class);
        QueueController controller = new QueueController(queueService, queueCleanupService, "test-internal-token");
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    /**
     * Property: GET /{domainId}/{solutionId}/queues returns 200 with
     * {"queues":{"queue":[...]}} via SifCollection.
     *
     * <p><strong>Validates: Requirements 3.1</strong></p>
     */
    @Property(tries = 20)
    @Label("GET /queues collection returns 200 with queues.queue array")
    void getQueuesCollectionReturns200WithSifCollectionFormat(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        MockMvc mockMvc = buildMockMvc();
        UUID ownerId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        QueueDTO queue = new QueueDTO(
                queueId, "test-queue", ownerId, "IMMEDIATE",
                "uri/" + queueId, 1, Instant.now(), null, null, 3, 5000L, 0L);

        when(queueService.getQueuesByOwner(any(UUID.class), eq(domainId), eq(solutionId)))
                .thenReturn(List.of(queue));

        MvcResult result = mockMvc.perform(get("/{domainId}/{solutionId}/queues", domainId, solutionId)
                        .header("X-Environment-Id", ownerId.toString()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.has("queues"))
                .as("Response should have 'queues' top-level key")
                .isTrue();
        assertThat(json.get("queues").has("queue"))
                .as("Response 'queues' should contain 'queue' array")
                .isTrue();
        assertThat(json.get("queues").get("queue").isArray())
                .as("'queue' should be an array")
                .isTrue();
    }

    /**
     * Property: DELETE /{domainId}/{solutionId}/queues/{id} returns 202 Accepted.
     *
     * <p><strong>Validates: Requirements 3.2</strong></p>
     */
    @Property(tries = 20)
    @Label("DELETE /queues/{id} returns 202 Accepted")
    void deleteQueueReturns202Accepted(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        MockMvc mockMvc = buildMockMvc();
        UUID ownerId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        QueueDTO queue = new QueueDTO(
                queueId, "test-queue", ownerId, "IMMEDIATE",
                "uri/" + queueId, 1, Instant.now(), null, null, 3, 5000L, 0L);

        when(queueService.getQueue(eq(queueId))).thenReturn(queue);
        doNothing().when(queueCleanupService).submitDelete(any(), anyString(), anyString(), any());

        MvcResult result = mockMvc.perform(delete("/{domainId}/{solutionId}/queues/{queueId}",
                        domainId, solutionId, queueId)
                        .header("X-Environment-Id", ownerId.toString()))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("DELETE /queues/{id} should return 202 Accepted")
                .isEqualTo(202);
    }

    /**
     * Property: GET /{domainId}/{solutionId}/queues/{id}/stats returns 200.
     *
     * <p><strong>Validates: Requirements 3.7</strong></p>
     */
    @Property(tries = 20)
    @Label("GET /queues/{id}/stats returns 200 with stats object")
    void getQueueStatsReturns200(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        MockMvc mockMvc = buildMockMvc();
        UUID ownerId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        QueueDTO queue = new QueueDTO(
                queueId, "test-queue", ownerId, "IMMEDIATE",
                "uri/" + queueId, 1, Instant.now(), null, null, 3, 5000L, 0L);

        when(queueService.getQueue(eq(queueId))).thenReturn(queue);
        when(queueService.getQueueStatistics(eq(queueId), eq(domainId), eq(solutionId), any(UUID.class)))
                .thenReturn(new QueueService.QueueStatistics(queueId, 5L, 2L, 1L, 8L));

        MvcResult result = mockMvc.perform(get("/{domainId}/{solutionId}/queues/{queueId}/stats",
                        domainId, solutionId, queueId)
                        .header("X-Environment-Id", ownerId.toString()))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("GET /queues/{id}/stats should return 200")
                .isEqualTo(200);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.has("queueId"))
                .as("Stats response should contain queueId field")
                .isTrue();
    }

    /**
     * Property: DELETE /{domainId}/{solutionId}/queues/{id}/messages (purge) returns 202 Accepted.
     *
     * <p><strong>Validates: Requirements 3.8</strong></p>
     */
    @Property(tries = 20)
    @Label("DELETE /queues/{id}/messages (purge) returns 202 Accepted")
    void purgeQueueReturns202Accepted(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        MockMvc mockMvc = buildMockMvc();
        UUID ownerId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        QueueDTO queue = new QueueDTO(
                queueId, "test-queue", ownerId, "IMMEDIATE",
                "uri/" + queueId, 1, Instant.now(), null, null, 3, 5000L, 0L);

        when(queueService.getQueue(eq(queueId))).thenReturn(queue);
        doNothing().when(queueCleanupService).submitPurge(any(), anyString(), anyString(), any());

        MvcResult result = mockMvc.perform(delete("/{domainId}/{solutionId}/queues/{queueId}/messages",
                        domainId, solutionId, queueId)
                        .header("X-Environment-Id", ownerId.toString()))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("DELETE /queues/{id}/messages should return 202 Accepted")
                .isEqualTo(202);
    }

    /**
     * Property: Ownership enforcement — request with X-Environment-Id != queue ownerId returns 403.
     *
     * <p><strong>Validates: Requirements 3.3</strong></p>
     */
    @Property(tries = 20)
    @Label("Non-owner access to queue returns 403 Forbidden")
    void nonOwnerAccessReturns403(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        MockMvc mockMvc = buildMockMvc();
        UUID ownerId = UUID.randomUUID();
        UUID differentEnvId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        QueueDTO queue = new QueueDTO(
                queueId, "test-queue", ownerId, "IMMEDIATE",
                "uri/" + queueId, 1, Instant.now(), null, null, 3, 5000L, 0L);

        when(queueService.getQueue(eq(queueId), eq(domainId), eq(solutionId)))
                .thenReturn(queue);

        MvcResult result = mockMvc.perform(get("/{domainId}/{solutionId}/queues/{queueId}",
                        domainId, solutionId, queueId)
                        .header("X-Environment-Id", differentEnvId.toString()))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("Non-owner access should return 403 Forbidden")
                .isEqualTo(403);
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> domainIds() {
        return Arbitraries.of("test-domain", "school-district-1", "state-edu");
    }

    @Provide
    Arbitrary<String> solutionIds() {
        return Arbitraries.of("prod-solution", "test-solution", "staging");
    }
}
