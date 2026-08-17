// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.subscriptionservice.controller.SubscriptionController;
import org.sifunity.broker.subscriptionservice.service.SubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bug Condition Exploration Test for SIF 3.7 Envelope and URL Alignment in subscription-service.
 *
 * <p><strong>CRITICAL: This test MUST FAIL on unfixed code — failure confirms the bug exists.</strong></p>
 *
 * <p>The subscription-service currently uses collection URLs ({@code POST /subscriptions}) with flat
 * request/response bodies instead of singular resource URLs ({@code POST /subscriptions/subscription})
 * with wrapped envelopes ({@code {"subscription": {...}}}). Single-resource GET responses also
 * return flat SubscriptionDTO objects instead of wrapped envelopes.</p>
 *
 * <p><strong>Validates: Requirements 1.6, 1.7, 1.8, 1.9, 1.10</strong></p>
 */
class SifEnvelopeAlignmentBugConditionExplorationTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    static {
        objectMapper.findAndRegisterModules();
    }

    private MockMvc mockMvc;
    private SubscriptionService subscriptionService;

    private MockMvc buildMockMvc() {
        subscriptionService = mock(SubscriptionService.class);
        SubscriptionController controller = new SubscriptionController(subscriptionService);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    /**
     * Property 1: POST to singular URL {@code /{domainId}/{solutionId}/subscriptions/subscription}
     * with wrapped body {@code {"subscription":{"queueId":"...","serviceName":"..."}}} should
     * return 201 with {@code "subscription"} key in response.
     *
     * <p><strong>Expected on UNFIXED code:</strong> FAILS — returns 404 (no handler at /subscription sub-path).</p>
     * <p><strong>Expected on FIXED code:</strong> PASSES — returns 201 with wrapped response.</p>
     *
     * <p><strong>Validates: Requirements 1.6, 1.7, 1.8</strong></p>
     */
    @Property(tries = 20)
    @Label("POST to /subscriptions/subscription with wrapped body should return 201 with subscription key")
    void postToSingularUrlWithWrappedBodyShouldReturn201(
            @ForAll("serviceNames") String serviceName,
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        mockMvc = buildMockMvc();
        UUID queueId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID aclEntryId = UUID.randomUUID();
        UUID contextUuid = UUID.randomUUID();

        SubscriptionDTO created = new SubscriptionDTO(
                subscriptionId, queueId, aclEntryId, serviceName,
                "OBJECT", null, contextUuid.toString(),
                SubscriptionType.EVENT, Instant.now());

        when(subscriptionService.createSubscription(anyString(), anyString(), any(SubscriptionDTO.class)))
                .thenReturn(created);

        // D4 (2.10): the resolution-by-name mode must carry serviceType and zoneId. This test is about
        // the ENVELOPE shape (wrapped body -> 201 with a "subscription" key), so the body is completed
        // here rather than left partial — otherwise it 400s on validation and never reaches the
        // envelope assertion it exists to make.
        String requestBody = String.format(
                "{\"subscription\":{\"queueId\":\"%s\",\"serviceName\":\"%s\",\"serviceType\":\"OBJECT\","
                        + "\"zoneId\":\"test-zone\",\"messageType\":\"EVENT\",\"contextId\":\"%s\"}}",
                queueId, serviceName, contextUuid);

        MvcResult result = mockMvc.perform(post("/{domainId}/{solutionId}/subscriptions/subscription",
                        domainId, solutionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();

        // On UNFIXED code: 404 — no handler at /subscriptions/subscription
        // On FIXED code: 201 with {"subscription":{...}}
        assertThat(result.getResponse().getStatus())
                .as("POST to /subscriptions/subscription should return 201 Created")
                .isEqualTo(HttpStatus.CREATED.value());

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody)
                .as("Response body should contain 'subscription' wrapper key")
                .contains("\"subscription\"");
    }

    /**
     * Property 2: POST to collection URL {@code /{domainId}/{solutionId}/subscriptions}
     * should return 405 Method Not Allowed.
     *
     * <p><strong>Expected on UNFIXED code:</strong> FAILS — returns 201 (currently creates subscription at collection URL).</p>
     * <p><strong>Expected on FIXED code:</strong> PASSES — returns 405.</p>
     *
     * <p><strong>Validates: Requirements 1.10</strong></p>
     */
    @Property(tries = 20)
    @Label("POST to /subscriptions collection URL should return 405 Method Not Allowed")
    void postToCollectionUrlShouldReturn405(
            @ForAll("serviceNames") String serviceName,
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        mockMvc = buildMockMvc();
        UUID queueId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        SubscriptionDTO created = new SubscriptionDTO(
                subscriptionId, queueId, null, serviceName,
                "OBJECT", null, null,
                SubscriptionType.EVENT, Instant.now());

        when(subscriptionService.createSubscription(anyString(), anyString(), any(SubscriptionDTO.class)))
                .thenReturn(created);

        String requestBody = String.format(
                "{\"queueId\":\"%s\",\"serviceName\":\"%s\",\"messageType\":\"EVENT\"}",
                queueId, serviceName);

        MvcResult result = mockMvc.perform(post("/{domainId}/{solutionId}/subscriptions",
                        domainId, solutionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();

        // On UNFIXED code: 201 — currently creates subscription at collection URL (BUG)
        // On FIXED code: 405 Method Not Allowed
        assertThat(result.getResponse().getStatus())
                .as("POST to /subscriptions collection URL should return 405 Method Not Allowed")
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
    }

    /**
     * Property 3: GET {@code /{domainId}/{solutionId}/subscriptions/{id}} should return
     * response body containing {@code "subscription"} wrapper key.
     *
     * <p><strong>Expected on UNFIXED code:</strong> FAILS — currently returns flat SubscriptionDTO without wrapper.</p>
     * <p><strong>Expected on FIXED code:</strong> PASSES — returns {"subscription":{...}}.</p>
     *
     * <p><strong>Validates: Requirements 1.9</strong></p>
     */
    @Property(tries = 20)
    @Label("GET /subscriptions/{id} should return response body with subscription wrapper key")
    void getByIdShouldReturnWrappedResponse(
            @ForAll("serviceNames") String serviceName,
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        mockMvc = buildMockMvc();
        UUID queueId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        SubscriptionDTO subscription = new SubscriptionDTO(
                subscriptionId, queueId, null, serviceName,
                "OBJECT", null, null,
                SubscriptionType.EVENT, Instant.now());

        when(subscriptionService.getSubscription(eq(subscriptionId)))
                .thenReturn(subscription);

        MvcResult result = mockMvc.perform(get("/{domainId}/{solutionId}/subscriptions/{id}",
                        domainId, solutionId, subscriptionId))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        // Parse the JSON and check for "subscription" wrapper key at root level
        var jsonNode = objectMapper.readTree(responseBody);

        // On UNFIXED code: root keys are "id", "queueId", "serviceName" etc. (flat SubscriptionDTO)
        // On FIXED code: root key is "subscription" containing the SubscriptionDTO fields
        assertThat(jsonNode.has("subscription"))
                .as("GET /subscriptions/{id} response should have 'subscription' wrapper key at root level, "
                        + "but got: %s", responseBody)
                .isTrue();
    }

    /**
     * Property 4: POST to {@code /{domainId}/{solutionId}/subscriptions/subscription} with
     * body missing the {@code "subscription"} key (e.g., {@code {"queueId":"..."}}) should
     * return 400 Bad Request.
     *
     * <p><strong>Expected on UNFIXED code:</strong> FAILS — returns 404 (no handler at /subscription sub-path).</p>
     * <p><strong>Expected on FIXED code:</strong> PASSES — returns 400 for missing wrapper key.</p>
     *
     * <p><strong>Validates: Requirements 1.7</strong></p>
     */
    @Property(tries = 20)
    @Label("POST to /subscriptions/subscription with missing subscription wrapper key should return 400")
    void postToSingularUrlWithoutWrapperKeyShouldReturn400(
            @ForAll("serviceNames") String serviceName,
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        mockMvc = buildMockMvc();
        UUID queueId = UUID.randomUUID();

        // Send body without "subscription" wrapper key — just flat fields
        String requestBody = String.format(
                "{\"queueId\":\"%s\",\"serviceName\":\"%s\",\"messageType\":\"EVENT\"}",
                queueId, serviceName);

        MvcResult result = mockMvc.perform(post("/{domainId}/{solutionId}/subscriptions/subscription",
                        domainId, solutionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();

        // On UNFIXED code: 404 — no handler at /subscriptions/subscription
        // On FIXED code: 400 Bad Request (missing "subscription" wrapper key)
        assertThat(result.getResponse().getStatus())
                .as("POST to /subscriptions/subscription without 'subscription' wrapper key should return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
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
