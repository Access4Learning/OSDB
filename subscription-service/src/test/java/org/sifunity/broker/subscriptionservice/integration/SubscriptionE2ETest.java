// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.common.health.JaegerHealthIndicator;
import org.sifunity.broker.common.health.LogstashHealthIndicator;
import org.sifunity.broker.subscriptionservice.client.QueueServiceClient;
import org.sifunity.broker.subscriptionservice.health.QueueServiceHealthIndicator;
import org.sifunity.broker.subscriptionservice.repository.SubscriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration test for the Subscription Service with ACL binding.
 *
 * <p>This test validates the complete subscription lifecycle with ACL entry binding:
 * <ol>
 *   <li>Create ACL entries, then create subscriptions bound to them</li>
 *   <li>Retrieve subscriptions and verify DTO fields populated from ACL JOIN</li>
 *   <li>Query EVENT subscriptions with exact context matching</li>
 *   <li>Query DELAYED_REQUEST subscriptions with exact matching</li>
 *   <li>Cascade deletion: deleting ACL entry removes subscriptions</li>
 *   <li>ACL resolution: create subscription via serviceName/contextUuid</li>
 * </ol>
 *
 * <p>Uses Testcontainers PostgreSQL with Flyway migrations and mocked Queue Service client.</p>
 *
 * <p><strong>Validates: Requirements 4.1, 4.2, 5.1, 5.2, 5.3, 6.1, 6.2, 6.3, 7.1, 7.2, 9.1, 9.3</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.cache.type=simple"
})
@DisplayName("Subscription Service End-to-End Integration Tests (ACL Binding)")
class SubscriptionE2ETest {

    private static final String TEST_DOMAIN_ID = "d47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String TEST_SOLUTION_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    private static final String BASE_PATH = "/" + TEST_DOMAIN_ID + "/" + TEST_SOLUTION_ID + "/subscriptions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private QueueServiceClient queueServiceClient;

    @MockBean
    private JaegerHealthIndicator jaegerHealthIndicator;

    @MockBean
    private LogstashHealthIndicator logstashHealthIndicator;

    @MockBean
    private QueueServiceHealthIndicator queueServiceHealthIndicator;

    @BeforeEach
    void setUp() {
        // Clean up database before each test (subscriptions first due to FK)
        subscriptionRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM acl_entries");
        jdbcTemplate.execute("DELETE FROM context_services");
        jdbcTemplate.execute("DELETE FROM environments");
        jdbcTemplate.execute("DELETE FROM solutions");
        jdbcTemplate.execute("DELETE FROM domains");

        // Mock queue service to always return true for queue existence
        when(queueServiceClient.queueExists(any(UUID.class))).thenReturn(true);

        // Mock health indicators to return UP
        when(jaegerHealthIndicator.health()).thenReturn(Health.up().build());
        when(logstashHealthIndicator.health()).thenReturn(Health.up().build());
        when(queueServiceHealthIndicator.health()).thenReturn(
            Health.up().withDetail("queue-service", "Available").build()
        );
    }

    @AfterEach
    void tearDown() {
        subscriptionRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM acl_entries");
        jdbcTemplate.execute("DELETE FROM context_services");
        jdbcTemplate.execute("DELETE FROM environments");
        jdbcTemplate.execute("DELETE FROM solutions");
        jdbcTemplate.execute("DELETE FROM domains");
    }

    // ========================================
    // Test 1: Full creation → retrieval round-trip
    // ========================================

    /**
     * Test: Create ACL entry, create subscription via service, retrieve by ID,
     * verify all DTO fields match.
     *
     * <p><strong>Validates: Requirements 4.1, 5.1, 5.2, 5.3</strong></p>
     */
    @Test
    @DisplayName("Full creation → retrieval round-trip with ACL binding")
    void testCreationRetrievalRoundTrip() throws Exception {
        // Step 1: Insert ACL entry with context_uuid
        UUID contextUuid = createContextForAcl();
        UUID aclEntryId = insertAclEntry("StudentPersonal", contextUuid,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);

        UUID queueId = UUID.randomUUID();

        // Step 2: Create subscription with aclEntryId (build JSON manually since aclEntryId is WRITE_ONLY)
        String createJson = String.format(
            "{\"subscription\":{\"queueId\":\"%s\",\"aclEntryId\":\"%s\",\"messageType\":\"EVENT\"}}",
            queueId, aclEntryId);

        MvcResult createResult = mockMvc.perform(post(BASE_PATH + "/subscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscription.id").exists())
                .andExpect(jsonPath("$.subscription.queueId").value(queueId.toString()))
                .andExpect(jsonPath("$.subscription.aclEntryId").doesNotExist())
                .andExpect(jsonPath("$.subscription.serviceName").value("StudentPersonal"))
                .andExpect(jsonPath("$.subscription.zoneId").exists())
                .andExpect(jsonPath("$.subscription.contextId").exists())
                .andExpect(jsonPath("$.subscription.messageType").value("EVENT"))
                .andExpect(jsonPath("$.subscription.createdAt").doesNotExist())
                .andReturn();

        String subscriptionId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("subscription").get("id").asText();

        // Step 3: Retrieve by ID and verify all fields
        mockMvc.perform(get(BASE_PATH + "/{id}", subscriptionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.id").value(subscriptionId))
                .andExpect(jsonPath("$.subscription.queueId").value(queueId.toString()))
                .andExpect(jsonPath("$.subscription.aclEntryId").doesNotExist())
                .andExpect(jsonPath("$.subscription.serviceName").value("StudentPersonal"))
                .andExpect(jsonPath("$.subscription.zoneId").exists())
                .andExpect(jsonPath("$.subscription.contextId").exists())
                .andExpect(jsonPath("$.subscription.messageType").value("EVENT"))
                .andExpect(jsonPath("$.subscription.createdAt").doesNotExist());

        // Step 4: Delete and verify gone
        mockMvc.perform(delete(BASE_PATH + "/{id}", subscriptionId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE_PATH + "/{id}", subscriptionId))
                .andExpect(status().isNotFound());
    }

    // ========================================
    // Test 2: Cascade deletion
    // ========================================

    /**
     * Test: Create ACL entry with subscriptions, delete ACL entry via native SQL,
     * verify subscriptions are gone.
     *
     * <p><strong>Validates: Requirements 7.1, 7.2</strong></p>
     */
    @Test
    @DisplayName("Cascade deletion: deleting ACL entry removes subscriptions")
    void testCascadeDeletion() throws Exception {
        // Step 1: Insert ACL entry and create subscriptions
        UUID contextUuid = createContextForAcl();
        UUID aclEntryId = insertAclEntry("StudentPersonal", contextUuid,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);

        UUID queueId1 = UUID.randomUUID();
        UUID queueId2 = UUID.randomUUID();

        createSubscriptionViaApi(aclEntryId, queueId1, SubscriptionType.EVENT);
        createSubscriptionViaApi(aclEntryId, queueId2, SubscriptionType.EVENT);

        // Verify 2 subscriptions exist
        assertThat(subscriptionRepository.count()).isEqualTo(2);

        // Step 2: Delete ACL entry via native SQL (simulating environment-service cascade)
        jdbcTemplate.update("DELETE FROM acl_entries WHERE id = ?", aclEntryId);

        // Step 3: Verify all subscriptions are gone (ON DELETE CASCADE)
        assertThat(subscriptionRepository.count())
                .as("All subscriptions should be cascade-deleted when ACL entry is removed")
                .isEqualTo(0);
    }

    // ========================================
    // Test 3: Query routing (EVENT exact + DELAYED_REQUEST exact)
    // ========================================

    /**
     * Test: Create multiple subscriptions with different ACL entries, verify
     * EVENT exact query and DELAYED_REQUEST exact query work correctly.
     *
     * <p><strong>Validates: Requirements 6.1, 6.2, 6.3</strong></p>
     */
    @Test
    @DisplayName("Query routing: EVENT exact and DELAYED_REQUEST exact matching")
    void testQueryRouting() throws Exception {
        // Create contexts for ACL entries
        UUID contextUuid1 = createContextForAcl();
        UUID contextUuid2 = createContextForAcl();

        // Create ACL entries — all with real context UUIDs (no wildcards)
        UUID aclExact = insertAclEntry("StudentPersonal", contextUuid1,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);
        UUID aclOther = insertAclEntry("StudentPersonal", contextUuid2,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);

        UUID aclDelayed = insertAclEntry("StudentPersonal", contextUuid1,
                AccessType.PROVIDE, AccessLevel.APPROVED);

        // Create EVENT subscriptions
        UUID q1 = UUID.randomUUID();
        UUID q3 = UUID.randomUUID();
        createSubscriptionViaApi(aclExact, q1, SubscriptionType.EVENT);
        createSubscriptionViaApi(aclOther, q3, SubscriptionType.EVENT);

        // Create DELAYED_REQUEST subscription
        UUID q4 = UUID.randomUUID();
        createSubscriptionViaApi(aclDelayed, q4, SubscriptionType.DELAYED_REQUEST);

        // EVENT query for contextUuid1 should return exact match only (1 result)
        mockMvc.perform(get(BASE_PATH + "/events")
                        .param("serviceName", "StudentPersonal")
                        .param("contextUuid", contextUuid1.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions.subscription", hasSize(1)))
                .andExpect(jsonPath("$.subscriptions.subscription[0].queueId",
                        is(q1.toString())));

        // EVENT query for contextUuid2 should return its own match only (1 result)
        mockMvc.perform(get(BASE_PATH + "/events")
                        .param("serviceName", "StudentPersonal")
                        .param("contextUuid", contextUuid2.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions.subscription", hasSize(1)))
                .andExpect(jsonPath("$.subscriptions.subscription[0].queueId",
                        is(q3.toString())));

        // DELAYED_REQUEST query for contextUuid1 should return exact match only
        mockMvc.perform(get(BASE_PATH + "/delayed-requests")
                        .param("serviceName", "StudentPersonal")
                        .param("contextUuid", contextUuid1.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueId").value(q4.toString()))
                .andExpect(jsonPath("$.messageType").value("DELAYED_REQUEST"));

        // DELAYED_REQUEST query for different context should return 404
        UUID nonExistentContext = UUID.randomUUID();
        mockMvc.perform(get(BASE_PATH + "/delayed-requests")
                        .param("serviceName", "StudentPersonal")
                        .param("contextUuid", nonExistentContext.toString()))
                .andExpect(status().isNotFound());
    }

    // ========================================
    // Test 4: ACL resolution from serviceName/contextUuid
    // ========================================

    /**
     * Test: Create subscription via serviceName/contextUuid (without aclEntryId),
     * verify it resolves correctly to the matching ACL entry.
     *
     * <p><strong>Validates: Requirements 4.2, 4.3</strong></p>
     */
    @Test
    @DisplayName("ACL resolution: create subscription via serviceName/zoneId/contextId")
    void testAclResolution() throws Exception {
        // Insert ACL entry — must use the same domainId/solutionId as BASE_PATH
        UUID contextUuid = createContextForAcl(TEST_DOMAIN_ID, TEST_SOLUTION_ID);
        // Retrieve the zone_id and context_id strings for the created context
        String ctxIdStr = jdbcTemplate.queryForObject(
            "SELECT context_id FROM contexts WHERE id = ?", String.class, contextUuid);
        String zoneIdStr = jdbcTemplate.queryForObject(
            "SELECT z.zone_id FROM zones z JOIN contexts c ON c.zone_uuid = z.id WHERE c.id = ?",
            String.class, contextUuid);

        UUID aclEntryId = insertAclEntry("SchoolInfo", contextUuid,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);

        UUID queueId = UUID.randomUUID();

        // Create subscription using serviceName/zoneId/contextId (no aclEntryId)
        String createJson = String.format(
            "{\"subscription\":{\"queueId\":\"%s\",\"serviceName\":\"SchoolInfo\",\"serviceType\":\"OBJECT\",\"zoneId\":\"%s\",\"contextId\":\"%s\",\"messageType\":\"EVENT\"}}",
            queueId, zoneIdStr, ctxIdStr);

        MvcResult result = mockMvc.perform(post(BASE_PATH + "/subscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscription.aclEntryId").doesNotExist())
                .andExpect(jsonPath("$.subscription.serviceName").value("SchoolInfo"))
                .andExpect(jsonPath("$.subscription.zoneId").value(zoneIdStr))
                .andExpect(jsonPath("$.subscription.contextId").value(ctxIdStr))
                .andExpect(jsonPath("$.subscription.messageType").value("EVENT"))
                .andReturn();

        // Verify the subscription was resolved to the correct ACL entry
        String subscriptionId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("subscription").get("id").asText();

        mockMvc.perform(get(BASE_PATH + "/{id}", subscriptionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.aclEntryId").doesNotExist());
    }

    // ========================================
    // Test 5: Duplicate subscription rejection
    // ========================================

    /**
     * Test: Duplicate subscription rejection with ACL binding.
     *
     * <p><strong>Validates: Requirements 2.3, 4.4</strong></p>
     */
    @Test
    @DisplayName("Duplicate subscription rejection with ACL binding")
    void testDuplicateSubscriptionRejection() throws Exception {
        UUID contextUuid = createContextForAcl();
        UUID aclEntryId = insertAclEntry("StudentPersonal", contextUuid,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);
        UUID queueId = UUID.randomUUID();

        // First creation succeeds
        createSubscriptionViaApi(aclEntryId, queueId, SubscriptionType.EVENT);

        // Second creation with same aclEntryId/queueId/type should fail
        String duplicateJson = String.format(
            "{\"subscription\":{\"queueId\":\"%s\",\"aclEntryId\":\"%s\",\"messageType\":\"EVENT\"}}",
            queueId, aclEntryId);

        mockMvc.perform(post(BASE_PATH + "/subscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("already exists")));
    }

    // ========================================
    // Test 6: Queue validation
    // ========================================

    /**
     * Test: Queue validation with mocked Queue Service.
     *
     * <p><strong>Validates: Requirements 3.4</strong></p>
     */
    @Test
    @DisplayName("Queue validation rejects non-existent queue")
    void testQueueValidation() throws Exception {
        UUID contextUuid = createContextForAcl();
        UUID aclEntryId = insertAclEntry("StudentPersonal", contextUuid,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);

        UUID nonExistentQueueId = UUID.randomUUID();
        when(queueServiceClient.queueExists(nonExistentQueueId)).thenReturn(false);

        String requestJson = String.format(
            "{\"subscription\":{\"queueId\":\"%s\",\"aclEntryId\":\"%s\",\"messageType\":\"EVENT\"}}",
            nonExistentQueueId, aclEntryId);

        mockMvc.perform(post(BASE_PATH + "/subscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value(containsString("Queue not found")));

        assertThat(subscriptionRepository.count()).isEqualTo(0);
    }

    // ========================================
    // Test 7: List subscriptions by queue
    // ========================================

    /**
     * Test: List subscriptions by queue with ACL data populated.
     *
     * <p><strong>Validates: Requirements 5.4</strong></p>
     */
    @Test
    @DisplayName("List subscriptions by queue returns ACL-populated DTOs")
    void testListSubscriptionsByQueue() throws Exception {
        UUID queueId = UUID.randomUUID();
        UUID contextUuid1 = createContextForAcl();
        UUID contextUuid2 = createContextForAcl();

        UUID acl1 = insertAclEntry("StudentPersonal", contextUuid1,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);
        UUID acl2 = insertAclEntry("SchoolInfo", contextUuid2,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);

        createSubscriptionViaApi(acl1, queueId, SubscriptionType.EVENT);
        createSubscriptionViaApi(acl2, queueId, SubscriptionType.EVENT);

        // Different queue
        UUID otherQueue = UUID.randomUUID();
        UUID contextUuid3 = createContextForAcl();
        UUID acl3 = insertAclEntry("StaffPersonal", contextUuid3,
                AccessType.SUBSCRIBE, AccessLevel.APPROVED);
        createSubscriptionViaApi(acl3, otherQueue, SubscriptionType.EVENT);

        mockMvc.perform(get(BASE_PATH)
                        .param("queueId", queueId.toString())
                        .header("X-Environment-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions.subscription", hasSize(2)))
                .andExpect(jsonPath("$.subscriptions.subscription[*].queueId",
                        everyItem(is(queueId.toString()))))
                .andExpect(jsonPath("$.subscriptions.subscription[*].serviceName",
                        containsInAnyOrder("StudentPersonal", "SchoolInfo")));

        // Empty queue
        mockMvc.perform(get(BASE_PATH)
                        .param("queueId", UUID.randomUUID().toString())
                        .header("X-Environment-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions.subscription", hasSize(0)));
    }

    // ========================================
    // Test 8: ACL entry not found
    // ========================================

    /**
     * Test: Creating subscription with non-existent ACL entry returns 404.
     *
     * <p><strong>Validates: Requirements 3.4</strong></p>
     */
    @Test
    @DisplayName("Creating subscription with non-existent ACL entry returns 404")
    void testAclEntryNotFound() throws Exception {
        UUID fakeAclId = UUID.randomUUID();
        UUID queueId = UUID.randomUUID();

        String requestJson = String.format(
            "{\"subscription\":{\"queueId\":\"%s\",\"aclEntryId\":\"%s\",\"messageType\":\"EVENT\"}}",
            queueId, fakeAclId);

        mockMvc.perform(post(BASE_PATH + "/subscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(containsString("ACL entry not found")));
    }

    // ========================================
    // Helpers
    // ========================================

    /**
     * Creates a valid environment_id by inserting the required domain → solution → environment
     * hierarchy. Returns the environment UUID for use as a FK in acl_entries.
     */
    private UUID createEnvironmentForAcl() {
        UUID domainId = UUID.randomUUID();
        String domainIdStr = "test-domain-" + domainId;
        jdbcTemplate.update(
            "INSERT INTO domains (id, domain_id, description, created_at) VALUES (?, ?, ?, NOW())",
            domainId, domainIdStr, "Test domain"
        );

        UUID solutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO solutions (id, domain_uuid, solution_id, description, created_at) VALUES (?, ?, ?, ?, NOW())",
            solutionId, domainId, "test-solution", "Test solution"
        );

        UUID environmentId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO environments (id, application_name, session_token, shared_secret, created_at, type, solution_uuid) " +
            "VALUES (?, ?, ?, ?, NOW(), 'DIRECT', ?)",
            environmentId, "test-app-" + environmentId, "token-" + environmentId, "secret", solutionId
        );

        return environmentId;
    }

    /**
     * Creates a context (with required domain → solution → zone hierarchy) and returns its UUID.
     * Used to provide a valid context_uuid FK for ACL entries.
     */
    private UUID createContextForAcl() {
        return createContextForAcl("dom-" + UUID.randomUUID(), "sol-" + UUID.randomUUID());
    }

    /**
     * Creates a context hierarchy using the specified domainId/solutionId strings.
     * Used when ACL resolution needs to match against specific path parameters.
     */
    private UUID createContextForAcl(String domainIdStr, String solutionIdStr) {
        UUID domainId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO domains (id, domain_id, description, created_at) VALUES (?, ?, ?, NOW())",
            domainId, domainIdStr, "Test domain"
        );

        UUID solutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO solutions (id, domain_uuid, solution_id, description, created_at) VALUES (?, ?, ?, ?, NOW())",
            solutionId, domainId, solutionIdStr, "Test solution"
        );

        UUID zoneId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO zones (id, solution_uuid, zone_id, description, created_at) VALUES (?, ?, ?, ?, NOW())",
            zoneId, solutionId, "zone-" + zoneId, "Test zone"
        );

        UUID contextId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO contexts (id, zone_uuid, context_id, description, created_at) VALUES (?, ?, ?, ?, NOW())",
            contextId, zoneId, "ctx-" + contextId, "Test context"
        );

        return contextId;
    }

    /**
     * Inserts a context_service and then an ACL entry via JDBC (AclEntryRef is @Immutable).
     * The acl_entries table now references context_services via context_service_uuid.
     */
    private UUID insertAclEntry(String serviceName, UUID contextUuid,
                                AccessType accessType, AccessLevel accessLevel) {
        // Insert context_service record (reuse if already exists for same contextUuid+serviceName)
        UUID contextServiceId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO context_services (id, context_uuid, service_name, service_type, created_at) " +
            "VALUES (?, ?, ?, ?, NOW()) ON CONFLICT (context_uuid, service_name) DO NOTHING",
            contextServiceId, contextUuid, serviceName, "OBJECT"
        );
        // Retrieve the actual context_service_uuid (may differ if it already existed)
        contextServiceId = jdbcTemplate.queryForObject(
            "SELECT id FROM context_services WHERE context_uuid = ? AND service_name = ?",
            UUID.class, contextUuid, serviceName
        );

        // Insert acl_entry referencing the context_service
        UUID id = UUID.randomUUID();
        UUID environmentId = createEnvironmentForAcl();
        jdbcTemplate.update(
            "INSERT INTO acl_entries (id, environment_id, context_service_uuid, access_type, access_level) " +
            "VALUES (?, ?, ?, ?, ?)",
            id, environmentId, contextServiceId, accessType.name(), accessLevel.name()
        );
        return id;
    }

    /**
     * Creates a subscription via the API using an aclEntryId.
     * Builds JSON manually because aclEntryId is WRITE_ONLY (excluded from serialization).
     * Uses SIF 3.7 envelope: POST to /subscription with {"subscription":{...}} wrapper.
     */
    private String createSubscriptionViaApi(UUID aclEntryId, UUID queueId,
                                            SubscriptionType type) throws Exception {
        String json = String.format(
            "{\"subscription\":{\"queueId\":\"%s\",\"aclEntryId\":\"%s\",\"messageType\":\"%s\"}}",
            queueId, aclEntryId, type);

        MvcResult result = mockMvc.perform(post(BASE_PATH + "/subscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("subscription").get("id").asText();
    }
}
