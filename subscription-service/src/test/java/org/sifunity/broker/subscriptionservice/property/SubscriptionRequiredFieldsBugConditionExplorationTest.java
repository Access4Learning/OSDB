// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.subscriptionservice.controller.SubscriptionController;
import org.sifunity.broker.subscriptionservice.exception.AclEntryNotFoundException;
import org.sifunity.broker.subscriptionservice.exception.AclEntryNotHeldException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Bug-condition tests for D4 (subscription create omits spec-required fields) and D7's subscription
 * case (1.17 — undeclared 404 on an ACL the caller does not hold).
 *
 * <p><strong>THESE ARE EXPECTED TO FAIL until wave 2/3 fixes land</strong> (tasks 8 and 12). Failure
 * is the point: each is the executable statement of a defect. Do not weaken an assertion to make one
 * pass.</p>
 *
 * <h2>What D4 actually is</h2>
 *
 * <p>{@code SubscriptionDTO} constrains only {@code queueId} and {@code messageType} with
 * {@code @NotNull}. {@code serviceName}, {@code serviceType} and {@code zoneId} carry no constraint at
 * all, so a body omitting them passes Bean Validation and is accepted — and {@code serviceType} is
 * then defaulted to {@code "OBJECT"} silently, inventing a value the client never sent. Per
 * requirement 2.10 all four must be required and a body missing any of them must be rejected with
 * 400.</p>
 *
 * <p>The silent default is the more dangerous half: a rejected request is visible, whereas a
 * subscription quietly created for the wrong service type is not.</p>
 *
 * <h2>Why 403 and not 404 for the ACL case</h2>
 *
 * <p>{@code AclEntryNotFoundException} maps to 404, which {@code POST /subscriptions/subscription}
 * does not declare. Requirement 2.16 splits the two cases: an ACL the caller does not <em>hold</em>
 * is an authorization failure (403, declared), while a syntactically valid but nonexistent
 * {@code aclEntryId} is a bad request (400, declared). The 404s on {@code GET}/{@code DELETE
 * /subscriptions/{id}} are declared and conformant and must be left alone.</p>
 *
 * <p><strong>Validates: bug condition 1.10, 1.17; Requirements 2.10, 2.16</strong></p>
 */
@DisplayName("SIF wire-format conformance — subscription required fields and statuses (bug conditions)")
class SubscriptionRequiredFieldsBugConditionExplorationTest {

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

    // ==================== D4 — spec-required fields ====================

    @Nested
    @DisplayName("D4 — spec-required subscription fields")
    class RequiredFields {

        @Test
        @DisplayName("A body omitting serviceName is rejected with 400")
        void omittingServiceNameIsRejected() throws Exception {
            assertRejected(bodyOmitting("serviceName"), "serviceName");
        }

        @Test
        @DisplayName("A body omitting zoneId is rejected with 400")
        void omittingZoneIdIsRejected() throws Exception {
            assertRejected(bodyOmitting("zoneId"), "zoneId");
        }

        /**
         * The worst of the three: omitting {@code serviceType} does not merely pass, it causes the
         * broker to invent {@code "OBJECT"}. A subscription silently bound to the wrong service type
         * is far harder to notice than a rejected request.
         */
        @Test
        @DisplayName("A body omitting serviceType is rejected with 400 rather than defaulted to OBJECT")
        void omittingServiceTypeIsRejectedNotDefaulted() throws Exception {
            assertRejected(bodyOmitting("serviceType"), "serviceType");
        }

        /**
         * Preservation-flavoured control in the same file: a COMPLETE body must still be accepted.
         * Without this, "reject everything" would satisfy the three properties above.
         */
        @Test
        @DisplayName("CONTROL — a complete body is still accepted with 201")
        void completeBodyIsStillAccepted() throws Exception {
            when(subscriptionService.createSubscription(anyString(), anyString(), any(SubscriptionDTO.class)))
                    .thenAnswer(inv -> inv.getArgument(2));

            MvcResult result = mockMvc.perform(post("/{d}/{s}/subscriptions/subscription", DOMAIN, SOLUTION)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("subscription", completeSubscription()))))
                    .andReturn();

            assertThat(result.getResponse().getStatus())
                    .as("a fully-populated subscription must still be created; otherwise the "
                            + "required-field properties above could pass by rejecting everything")
                    .isEqualTo(201);
        }
    }

    // ==================== D7 (1.17) — undeclared status on the ACL path ====================

    @Nested
    @DisplayName("D7 — undeclared status on the ACL path")
    class UndeclaredAclStatus {

        /**
         * Bug condition 1.17 → Requirement 2.16. An ACL the caller does not hold is an authorization
         * outcome; 403 is declared for this operation and 404 is not.
         */
        @Test
        @DisplayName("An ACL the caller does not hold yields the declared 403, not the undeclared 404")
        void aclNotHeldYields403() throws Exception {
            when(subscriptionService.createSubscription(anyString(), anyString(), any(SubscriptionDTO.class)))
                    .thenThrow(new AclEntryNotHeldException("No matching ACL entry found for service=X"));

            MvcResult result = mockMvc.perform(post("/{d}/{s}/subscriptions/subscription", DOMAIN, SOLUTION)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("subscription", completeSubscription()))))
                    .andReturn();

            assertThat(result.getResponse().getStatus())
                    .as("POST /subscriptions/subscription declares 403 but not 404; an ACL the caller "
                            + "does not hold is an authorization failure")
                    .isEqualTo(403);
        }

        /**
         * The other half of requirement 2.16. A caller-supplied {@code aclEntryId} that does not
         * resolve is a malformed reference, so 400 — also declared, and also not 404.
         */
        @Test
        @DisplayName("An unresolvable caller-supplied aclEntryId yields the declared 400, not 404")
        void unresolvableAclEntryIdYields400() throws Exception {
            when(subscriptionService.createSubscription(anyString(), anyString(), any(SubscriptionDTO.class)))
                    .thenThrow(new AclEntryNotFoundException("ACL entry not found: " + UUID.randomUUID()));

            Map<String, Object> body = completeSubscription();
            body.put("aclEntryId", UUID.randomUUID().toString());

            MvcResult result = mockMvc.perform(post("/{d}/{s}/subscriptions/subscription", DOMAIN, SOLUTION)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("subscription", body))))
                    .andReturn();

            assertThat(result.getResponse().getStatus())
                    .as("an unresolvable aclEntryId is a bad request, not a missing resource")
                    .isEqualTo(400);
        }
    }

    // ==================== helpers ====================

    private void assertRejected(Map<String, Object> subscription, String omittedField) throws Exception {
        // Permissive stub: if validation wrongly lets the body through, the request reaches the
        // service and returns 201 — which is exactly the failure this test is meant to expose.
        when(subscriptionService.createSubscription(anyString(), anyString(), any(SubscriptionDTO.class)))
                .thenAnswer(inv -> inv.getArgument(2));

        MvcResult result = mockMvc.perform(post("/{d}/{s}/subscriptions/subscription", DOMAIN, SOLUTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("subscription", subscription))))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("'%s' is required by the SIF schema; a body omitting it must be rejected with 400, "
                        + "not accepted (and in serviceType's case, silently defaulted)", omittedField)
                .isEqualTo(400);
    }

    /** A subscription body with every spec-required field present. */
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

    private static Map<String, Object> bodyOmitting(String field) {
        Map<String, Object> m = completeSubscription();
        m.remove(field);
        return m;
    }
}
