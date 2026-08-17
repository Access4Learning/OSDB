// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.subscriptionservice.controller.SubscriptionController;
import org.sifunity.broker.subscriptionservice.service.SubscriptionService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Preservation Property Tests for subscription-service.
 *
 * <p>These tests capture the EXISTING behavior of endpoints that must remain unchanged
 * after the SIF 3.7 envelope alignment fix. They run on UNFIXED code and MUST PASS,
 * confirming the baseline behavior to preserve.</p>
 *
 * <p>Preservation scope: collection GETs, DELETEs, event queries, delayed-request queries.</p>
 *
 * <p><strong>Validates: Requirements 3.4, 3.5, 3.9, 3.10</strong></p>
 */
class SifEnvelopePreservationTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.findAndRegisterModules();
    }

    private SubscriptionService subscriptionService;

    private MockMvc buildMockMvc() {
        subscriptionService = mock(SubscriptionService.class);
        SubscriptionController controller = new SubscriptionController(subscriptionService);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    /**
     * Property: GET /{domainId}/{solutionId}/subscriptions returns 200 with
     * {"subscriptions":{"subscription":[...]}} via SifCollection.
     *
     * <p><strong>Validates: Requirements 3.4</strong></p>
     */
    @Property(tries = 20)
    @Label("GET /subscriptions collection returns 200 with subscriptions.subscription array")
    void getSubscriptionsCollectionReturns200WithSifCollectionFormat(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        MockMvc mockMvc = buildMockMvc();
        UUID environmentId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        SubscriptionDTO sub = new SubscriptionDTO(
                subscriptionId, queueId, null, "StudentPersonal",
                "OBJECT", null, null,
                SubscriptionType.EVENT, Instant.now());

        when(subscriptionService.findSubscriptionsByEnvironment(eq(environmentId)))
                .thenReturn(List.of(sub));

        MvcResult result = mockMvc.perform(get("/{domainId}/{solutionId}/subscriptions", domainId, solutionId)
                        .header("X-Environment-Id", environmentId.toString()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.has("subscriptions"))
                .as("Response should have 'subscriptions' top-level key")
                .isTrue();
        assertThat(json.get("subscriptions").has("subscription"))
                .as("Response 'subscriptions' should contain 'subscription' array")
                .isTrue();
        assertThat(json.get("subscriptions").get("subscription").isArray())
                .as("'subscription' should be an array")
                .isTrue();
    }

    /**
     * Property: DELETE /{domainId}/{solutionId}/subscriptions/{id} returns 204 No Content.
     *
     * <p><strong>Validates: Requirements 3.5</strong></p>
     */
    @Property(tries = 20)
    @Label("DELETE /subscriptions/{id} returns 204 No Content")
    void deleteSubscriptionReturns204(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        MockMvc mockMvc = buildMockMvc();
        UUID subscriptionId = UUID.randomUUID();

        doNothing().when(subscriptionService).deleteSubscription(eq(subscriptionId));

        MvcResult result = mockMvc.perform(delete("/{domainId}/{solutionId}/subscriptions/{id}",
                        domainId, solutionId, subscriptionId))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("DELETE /subscriptions/{id} should return 204 No Content")
                .isEqualTo(204);
    }

    /**
     * Property: GET /{domainId}/{solutionId}/subscriptions/events returns 200 with SifCollection.
     *
     * <p><strong>Validates: Requirements 3.9</strong></p>
     */
    @Property(tries = 20)
    @Label("GET /subscriptions/events returns 200 with SifCollection format")
    void getEventSubscriptionsReturns200WithSifCollection(
            @ForAll("serviceNames") String serviceName,
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        MockMvc mockMvc = buildMockMvc();
        UUID queueId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID contextUuid = UUID.randomUUID();

        SubscriptionDTO sub = new SubscriptionDTO(
                subscriptionId, queueId, null, serviceName,
                "OBJECT", null, contextUuid.toString(),
                SubscriptionType.EVENT, Instant.now());

        when(subscriptionService.findEventSubscriptions(eq(serviceName), eq(contextUuid)))
                .thenReturn(List.of(sub));

        MvcResult result = mockMvc.perform(get("/{domainId}/{solutionId}/subscriptions/events",
                        domainId, solutionId)
                        .param("serviceName", serviceName)
                        .param("contextUuid", contextUuid.toString()))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("GET /subscriptions/events should return 200")
                .isEqualTo(200);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.has("subscriptions"))
                .as("Event query response should have 'subscriptions' top-level key (SifCollection)")
                .isTrue();
        assertThat(json.get("subscriptions").has("subscription"))
                .as("Event query response should contain 'subscription' array")
                .isTrue();
    }

    /**
     * Property: GET /{domainId}/{solutionId}/subscriptions/delayed-requests returns 200.
     *
     * <p><strong>Validates: Requirements 3.10</strong></p>
     */
    @Property(tries = 20)
    @Label("GET /subscriptions/delayed-requests returns 200 with subscription")
    void getDelayedRequestSubscriptionReturns200(
            @ForAll("serviceNames") String serviceName,
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        MockMvc mockMvc = buildMockMvc();
        UUID queueId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID contextUuid = UUID.randomUUID();

        SubscriptionDTO sub = new SubscriptionDTO(
                subscriptionId, queueId, null, serviceName,
                "OBJECT", null, contextUuid.toString(),
                SubscriptionType.DELAYED_REQUEST, Instant.now());

        when(subscriptionService.findDelayedRequestSubscription(eq(serviceName), eq(contextUuid)))
                .thenReturn(sub);

        MvcResult result = mockMvc.perform(get("/{domainId}/{solutionId}/subscriptions/delayed-requests",
                        domainId, solutionId)
                        .param("serviceName", serviceName)
                        .param("contextUuid", contextUuid.toString()))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("GET /subscriptions/delayed-requests should return 200")
                .isEqualTo(200);
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.of("StudentPersonal", "SchoolInfo", "StaffPersonal", "Attendance", "Assessment");
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
