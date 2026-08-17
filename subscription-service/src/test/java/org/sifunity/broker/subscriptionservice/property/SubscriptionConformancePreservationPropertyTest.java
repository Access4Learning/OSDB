// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.common.exception.SubscriptionNotFoundException;
import org.sifunity.broker.subscriptionservice.controller.SubscriptionController;
import org.sifunity.broker.subscriptionservice.exception.GlobalExceptionHandler;
import org.sifunity.broker.subscriptionservice.service.SubscriptionService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Preservation properties for the subscription operations.
 *
 * <p><strong>These MUST pass both before and after wave 2's task 8 and wave 3's task 12.</strong></p>
 *
 * <h2>Why each one is here</h2>
 *
 * <p>Task 8 moves the ACL failure off 404, and task 12 adds required-field constraints. The realistic
 * collateral:</p>
 *
 * <ul>
 *   <li><b>The declared 404s.</b> {@code GET} and {@code DELETE /subscriptions/{id}} legitimately
 *       return 404 for a missing subscription — the spec declares it and the tasks say explicitly to
 *       leave them alone. A change aimed at the ACL path that also moves these would be conformant
 *       nowhere and would break every consumer that distinguishes "gone" from "forbidden".</li>
 *   <li><b>405 on the collection URL.</b> SIF requires POST to the singular
 *       {@code /subscriptions/subscription}; the collection URL must keep rejecting with 405. It is a
 *       neighbouring mapping in the same controller.</li>
 *   <li><b>201 on a valid create.</b> The counterweight to task 12: once required fields are enforced,
 *       a complete body must still succeed.</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements 3.9, 3.11 (preservation)</strong></p>
 */
@DisplayName("SIF wire-format conformance — subscription preservation")
class SubscriptionConformancePreservationPropertyTest {

    private static final String DOMAIN = "test-domain";
    private static final String SOLUTION = "test-solution";

    private MockMvc mockMvc;
    private SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        subscriptionService = mock(SubscriptionService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SubscriptionController(subscriptionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * The declared 404. Task 8 must move only the ACL-failure status, not this one.
     */
    @Test
    @DisplayName("GET of a missing subscription still returns the declared 404")
    void getMissingSubscriptionStillReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(subscriptionService.getSubscription(any(UUID.class)))
                .thenThrow(new SubscriptionNotFoundException("Subscription not found: " + id));

        MvcResult result = mockMvc.perform(
                get("/{d}/{s}/subscriptions/{id}", DOMAIN, SOLUTION, id)).andReturn();

        assertThat(result.getResponse().getStatus())
                .as("404 on GET /subscriptions/{id} IS declared and conformant — task 8 targets the "
                        + "ACL path only and must not disturb it")
                .isEqualTo(404);
    }

    /**
     * SIF requires POST to the singular resource URL; the collection URL keeps rejecting with 405.
     */
    @Test
    @DisplayName("POST to the subscriptions collection URL still returns 405")
    void collectionPostStillReturns405() throws Exception {
        MvcResult result = mockMvc.perform(
                post("/{d}/{s}/subscriptions", DOMAIN, SOLUTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("SIF mandates POST to /subscriptions/subscription; the collection URL must stay 405")
                .isEqualTo(405);
    }

    /**
     * The counterweight to task 12: enforcing required fields must not make valid creates fail.
     */
    @Test
    @DisplayName("A valid create still returns 201 with the subscription wrapper")
    void validCreateStillReturns201() throws Exception {
        when(subscriptionService.createSubscription(anyString(), anyString(), any(SubscriptionDTO.class)))
                .thenAnswer(inv -> inv.getArgument(2));

        MvcResult result = mockMvc.perform(
                post("/{d}/{s}/subscriptions/subscription", DOMAIN, SOLUTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("subscription", completeSubscription()))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getContentAsString())
                .as("the SIF 3.7 'subscription' response wrapper must be retained")
                .contains("\"subscription\"");
    }

    /**
     * The envelope check on the request side, which task 12 also touches.
     */
    @Test
    @DisplayName("A body missing the subscription wrapper still returns 400")
    void missingWrapperStillReturns400() throws Exception {
        MvcResult result = mockMvc.perform(
                post("/{d}/{s}/subscriptions/subscription", DOMAIN, SOLUTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("the SIF 3.7 envelope convention requires the 'subscription' wrapper key")
                .isEqualTo(400);
    }

    private static Map<String, Object> completeSubscription() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("queueId", UUID.randomUUID().toString());
        m.put("serviceName", "StudentPersonals");
        m.put("serviceType", "OBJECT");
        m.put("zoneId", "test-zone");
        m.put("contextId", "DEFAULT");
        m.put("messageType", "EVENT");
        return m;
    }
}
