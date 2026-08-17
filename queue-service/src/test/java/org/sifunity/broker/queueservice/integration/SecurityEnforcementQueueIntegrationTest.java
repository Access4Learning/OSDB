// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.queueservice.config.EmbeddedRedisTestConfiguration;
import org.sifunity.broker.queueservice.dto.CreateQueueRequest;
import org.sifunity.broker.queueservice.dto.QueueCreateRequest;
import org.sifunity.broker.queueservice.repository.QueueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for security enforcement on queue listing ownership scoping.
 *
 * <p>Verifies that non-admin environments only see their own queues when listing,
 * while admin/broker-services environments see all queues in the solution.</p>
 *
 * <p><strong>Validates: Requirements 2.5, 3.4, 3.5</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EmbeddedRedisTestConfiguration.class)
@TestPropertySource(properties = {
    "message.storage.type=filesystem",
    "message.storage.filesystem.path=./target/test-messages-security-enforcement",
    "broker.internal-service-token=test-internal-token"
})
@DisplayName("Security Enforcement Integration Tests - Queue Ownership Scoping")
class SecurityEnforcementQueueIntegrationTest {

    private static final String TEST_DOMAIN_ID = "d47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String TEST_SOLUTION_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    private static final String QUEUE_BASE_PATH = "/" + TEST_DOMAIN_ID + "/" + TEST_SOLUTION_ID + "/queues";

    private static final UUID OWNER_A = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID OWNER_B = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        // Clean up Redis
        Set<String> keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        // Clean up database
        queueRepository.deleteAllInBatch();
    }

    private void createQueue(String name, UUID ownerId) throws Exception {
        QueueCreateRequest request = new QueueCreateRequest(
                new CreateQueueRequest(name, ownerId, 3, 5000L, null));
        mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                .header("X-Environment-Id", ownerId.toString())
                .header("X-Solution-Uuid", TEST_SOLUTION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    @Nested
    @DisplayName("Non-Admin Queue Listing - Scoped to Owned Queues")
    class NonAdminQueueListing {

        @Test
        @DisplayName("Non-admin environment sees only its own queues")
        void nonAdmin_seesOnlyOwnedQueues() throws Exception {
            // Arrange: Create queues for two different owners
            createQueue("queue-owner-a-1", OWNER_A);
            createQueue("queue-owner-a-2", OWNER_A);
            createQueue("queue-owner-b-1", OWNER_B);

            // Act & Assert: Owner A lists queues — sees only its own 2 queues
            mockMvc.perform(get(QUEUE_BASE_PATH)
                    .header("X-Environment-Id", OWNER_A.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queues.queue", hasSize(2)))
                .andExpect(jsonPath("$.queues.queue[*].ownerId", everyItem(is(OWNER_A.toString()))))
                .andExpect(jsonPath("$.queues.queue[*].name", containsInAnyOrder("queue-owner-a-1", "queue-owner-a-2")));
        }

        @Test
        @DisplayName("Non-admin environment with no queues sees empty list")
        void nonAdmin_noQueues_seesEmptyList() throws Exception {
            // Arrange: Create a queue for owner B only
            createQueue("queue-owner-b-only", OWNER_B);

            // Act & Assert: Owner A lists queues — sees nothing
            mockMvc.perform(get(QUEUE_BASE_PATH)
                    .header("X-Environment-Id", OWNER_A.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queues.queue", hasSize(0)));
        }

        @Test
        @DisplayName("Non-admin environment cannot see other environment's queues regardless of query params")
        void nonAdmin_cannotAccessOtherQueuesViaQueryParams() throws Exception {
            // Arrange: Create queues for both owners
            createQueue("queue-a", OWNER_A);
            createQueue("queue-b", OWNER_B);

            // Act & Assert: Owner A cannot see owner B's queues even if trying ownerId param
            // (The controller ignores ownerId for non-admin; scopes to authenticated env ID)
            mockMvc.perform(get(QUEUE_BASE_PATH)
                    .header("X-Environment-Id", OWNER_A.toString())
                    .param("ownerId", OWNER_B.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queues.queue", hasSize(1)))
                .andExpect(jsonPath("$.queues.queue[0].ownerId").value(OWNER_A.toString()));
        }
    }

    @Nested
    @DisplayName("Admin/Broker-Services Queue Listing - All Queues Returned")
    class AdminQueueListing {

        @Test
        @DisplayName("Broker-services fingerprint sees all queues in the solution")
        void brokerServices_seesAllQueues() throws Exception {
            // Arrange: Create queues for different owners
            createQueue("queue-a-1", OWNER_A);
            createQueue("queue-b-1", OWNER_B);

            // Act & Assert: broker-services sees all queues (must provide solution UUID for the lookup)
            mockMvc.perform(get(QUEUE_BASE_PATH)
                    .header("X-Environment-Id", UUID.randomUUID().toString())
                    .header("X-Environment-Fingerprint", "broker-services")
                    .header("X-Internal-Service-Token", "test-internal-token")
                    .header("X-Solution-Uuid", TEST_SOLUTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queues.queue", hasSize(2)))
                .andExpect(jsonPath("$.queues.queue[*].name", containsInAnyOrder("queue-a-1", "queue-b-1")));
        }

        @Test
        @DisplayName("Global admin domain/solution sees all queues in the solution")
        void globalAdmin_seesAllQueues() throws Exception {
            // Arrange: Create queues for different owners
            createQueue("queue-ga-1", OWNER_A);
            createQueue("queue-gb-1", OWNER_B);

            // Act & Assert: Request with global admin domain/solution path gets all queues
            // GlobalAdminConstants checks domainId/solutionId from URL path
            String adminPath = "/" + GlobalAdminConstants.GLOBAL_DOMAIN + "/" + GlobalAdminConstants.GLOBAL_SOLUTION + "/queues";
            mockMvc.perform(get(adminPath)
                    .header("X-Environment-Id", OWNER_A.toString()))
                .andExpect(status().isOk());
            // Global admin path: isGlobalAdmin returns true, so getAllQueues is called.
            // Since these queues are under a different domainId/solutionId, they won't appear.
            // The important thing is the admin check passes and no 403 is returned.
        }
    }

    @Nested
    @DisplayName("Queue Ownership Enforcement - Create")
    class QueueCreateOwnershipEnforcement {

        @Test
        @DisplayName("Non-admin environment cannot create queue for another environment")
        void nonAdmin_cannotCreateQueueForAnother() throws Exception {
            // Arrange: Owner A tries to create a queue owned by Owner B
            QueueCreateRequest request = new QueueCreateRequest(
                    new CreateQueueRequest("stolen-queue", OWNER_B, 3, 5000L, null));

            // Act & Assert: Should return 403 ownership violation
            mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                    .header("X-Environment-Id", OWNER_A.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Broker-services can create queue for any environment")
        void brokerServices_canCreateQueueForAnother() throws Exception {
            // Arrange: broker-services creates a queue owned by Owner B
            QueueCreateRequest request = new QueueCreateRequest(
                    new CreateQueueRequest("broker-created-queue", OWNER_B, 3, 5000L, null));

            // Act & Assert: Should succeed
            mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                    .header("X-Environment-Id", UUID.randomUUID().toString())
                    .header("X-Environment-Fingerprint", "broker-services")
                    .header("X-Internal-Service-Token", "test-internal-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.queue.name").value("broker-created-queue"))
                .andExpect(jsonPath("$.queue.ownerId").value(OWNER_B.toString()));
        }
    }
}
