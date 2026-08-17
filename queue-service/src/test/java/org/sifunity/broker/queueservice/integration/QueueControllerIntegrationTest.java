// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.queueservice.config.EmbeddedRedisTestConfiguration;
import org.sifunity.broker.queueservice.dto.CreateQueueRequest;
import org.sifunity.broker.queueservice.dto.QueueCreateRequest;
import org.sifunity.broker.queueservice.redis.StreamMessageRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for QueueController.
 * Tests all queue management endpoints with HTTP status codes, response formats, and error handling.
 * 
 * **Validates: Requirements 1.1, 1.3, 1.4, 1.5, 12.1, 12.2, 12.3, 12.4, 14.1, 14.2, 14.3, 14.4**
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EmbeddedRedisTestConfiguration.class)
@TestPropertySource(properties = {
    "message.storage.type=filesystem",
    "message.storage.filesystem.path=./target/test-messages-queue-controller",
    "broker.internal-service-token=test-internal-token"
})
class QueueControllerIntegrationTest {
    
    private static final String TEST_DOMAIN_ID = "d47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String TEST_SOLUTION_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    private static final String QUEUE_BASE_PATH = "/" + TEST_DOMAIN_ID + "/" + TEST_SOLUTION_ID + "/queues";
    private static final UUID TEST_OWNER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID TEST_OWNER_ID_2 = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private QueueRepository queueRepository;
    
    @Autowired
    private StreamMessageRepository streamMessageRepository;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @BeforeEach
    void setUp() {
        // Clean up Redis before each test
        Set<String> keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        
        // Clean up database before each test (use deleteAllInBatch to avoid
        // loading entities which can cause OptimisticLockingFailure on CI)
        queueRepository.deleteAllInBatch();
    }
    
    /**
     * Test: Create queue with valid parameters
     * 
     * **Validates: Requirements 1.1, 14.1, 14.2, 14.3, 14.4**
     */
    @Test
    void testCreateQueue_WithValidParameters_ReturnsCreated() throws Exception {
        // Given: Valid queue creation request
        UUID ownerId = TEST_OWNER_ID;
        QueueCreateRequest request = new QueueCreateRequest(new CreateQueueRequest(
            "test-queue",
            ownerId,
            3,
            5000L,
            null
        ));
        
        // When: POST to queue endpoint
        MvcResult result = mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                .header("X-Environment-Id", ownerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: Returns 201 Created
            .andExpect(status().isCreated())
            // And: Response contains queue data
            .andExpect(jsonPath("$.queue.id").exists())
            .andExpect(jsonPath("$.queue.name").value("test-queue"))
            .andExpect(jsonPath("$.queue.ownerId").value(ownerId.toString()))
            .andExpect(jsonPath("$.queue.maxRetries").doesNotExist())
            .andExpect(jsonPath("$.queue.retryDelayMs").doesNotExist())
            .andExpect(jsonPath("$.queue.created").exists())
            .andExpect(jsonPath("$.queue.messageCount").value(0))
            .andReturn();
        
        // Verify queue was persisted
        String responseBody = result.getResponse().getContentAsString();
        String queueId = objectMapper.readTree(responseBody).get("queue").get("id").asText();
        assertThat(queueRepository.findById(UUID.fromString(queueId))).isPresent();
    }
    
    /**
     * Test: Create queue with default values (null optional parameters)
     * 
     * **Validates: Requirements 1.1, 14.1, 14.2, 14.3, 14.4**
     */
    @Test
    void testCreateQueue_WithDefaultValues_ReturnsCreated() throws Exception {
        // Given: Queue creation request with null optional parameters
        UUID ownerId = TEST_OWNER_ID;
        QueueCreateRequest request = new QueueCreateRequest(new CreateQueueRequest(
            "test-queue",
            ownerId,
            null,
            null,
            null
        ));
        
        // When: POST to queue endpoint
        mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                .header("X-Environment-Id", ownerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: Returns 201 Created with default values
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.queue.maxRetries").doesNotExist())
            .andExpect(jsonPath("$.queue.retryDelayMs").doesNotExist());
    }
    
    /**
     * Test: Create queue with missing required fields
     * 
     * **Validates: Requirements 14.1, 14.2, 14.3, 14.4**
     */
    @Test
    void testCreateQueue_WithMissingName_ReturnsBadRequest() throws Exception {
        // Given: Request with missing name (wrapped in queue envelope)
        String requestJson = """
            {
                "queue": {
                    "ownerId": "%s",
                    "maxRetries": 3,
                    "retryDelayMs": 5000
                }
            }
            """.formatted(TEST_OWNER_ID);
        
        // When: POST to queue endpoint with missing name
        mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                .header("X-Environment-Id", TEST_OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            // Then: Returns 400 Bad Request
            .andExpect(status().isBadRequest());
    }
    
    /**
     * Test: Create queue with missing owner ID
     * 
     * **Validates: Requirements 14.1, 14.2, 14.3, 14.4**
     */
    @Test
    void testCreateQueue_WithMissingOwnerId_ReturnsBadRequest() throws Exception {
        // Given: Request with missing ownerId (wrapped in queue envelope)
        String requestJson = """
            {
                "queue": {
                    "name": "test-queue",
                    "maxRetries": 3,
                    "retryDelayMs": 5000
                }
            }
            """;
        
        // When: POST to queue endpoint with missing ownerId
        mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                .header("X-Environment-Id", TEST_OWNER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            // Then: Returns 400 Bad Request
            .andExpect(status().isBadRequest());
    }
    
    /**
     * Test: Create queue with invalid maxRetries (negative)
     * 
     * **Validates: Requirements 14.1, 14.2, 14.3, 14.4**
     */
    @Test
    void testCreateQueue_WithNegativeMaxRetries_ReturnsBadRequest() throws Exception {
        // Given: Request with negative maxRetries
        UUID ownerId = TEST_OWNER_ID;
        QueueCreateRequest request = new QueueCreateRequest(new CreateQueueRequest(
            "test-queue",
            ownerId,
            -1,
            5000L,
            null
        ));
        
        // When: POST to queue endpoint with negative maxRetries
        mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                .header("X-Environment-Id", ownerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: Returns 400 Bad Request
            .andExpect(status().isBadRequest());
    }
    
    /**
     * Test: Get queue by ID
     * 
     * **Validates: Requirements 1.3, 14.1, 14.2, 14.3, 14.4**
     */
    @Test
    void testGetQueue_WithExistingId_ReturnsOk() throws Exception {
        // Given: An existing queue
        UUID ownerId = TEST_OWNER_ID;
        QueueCreateRequest createRequest = new QueueCreateRequest(new CreateQueueRequest(
            "test-queue",
            ownerId,
            3,
            5000L,
            null
        ));
        
        MvcResult createResult = mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                .header("X-Environment-Id", ownerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andReturn();
        
        String queueId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .get("queue").get("id").asText();
        
        // When: GET queue by ID
        mockMvc.perform(get(QUEUE_BASE_PATH + "/{queueId}", queueId)
                .header("X-Environment-Id", ownerId.toString()))
            // Then: Returns 200 OK
            .andExpect(status().isOk())
            // And: Response contains queue data wrapped in "queue" key
            .andExpect(jsonPath("$.queue.id").value(queueId))
            .andExpect(jsonPath("$.queue.name").value("test-queue"))
            .andExpect(jsonPath("$.queue.ownerId").value(ownerId.toString()))
            .andExpect(jsonPath("$.queue.maxRetries").doesNotExist())
            .andExpect(jsonPath("$.queue.retryDelayMs").doesNotExist())
            .andExpect(jsonPath("$.queue.created").exists())
            .andExpect(jsonPath("$.queue.messageCount").exists());
    }
    
    /**
     * Test: Get queue with non-existent ID
     * 
     * **Validates: Requirements 1.3, 14.1, 14.2, 14.3, 14.4**
     */
    @Test
    void testGetQueue_WithNonExistentId_ReturnsNotFound() throws Exception {
        // Given: A non-existent queue ID
        UUID nonExistentId = UUID.randomUUID();
        
        // When: GET queue by ID
        mockMvc.perform(get(QUEUE_BASE_PATH + "/{queueId}", nonExistentId)
                .header("X-Environment-Id", TEST_OWNER_ID.toString()))
            // Then: Returns 404 Not Found
            .andExpect(status().isNotFound());
    }
    
    /**
     * Test: Get queues by owner ID
     * 
     * **Validates: Requirements 1.4, 14.1, 14.2, 14.3, 14.4**
     */
    @Test
    void testGetQueuesByOwner_WithExistingQueues_ReturnsOk() throws Exception {
        // Given: Multiple queues for the same owner
        UUID ownerId = TEST_OWNER_ID;
        
        // Create first queue
        QueueCreateRequest request1 = new QueueCreateRequest(new CreateQueueRequest("queue-1", ownerId, 3, 5000L, null));
        mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                .header("X-Environment-Id", ownerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isCreated());
        
        // Create second queue
        QueueCreateRequest request2 = new QueueCreateRequest(new CreateQueueRequest("queue-2", ownerId, 5, 10000L, null));
        mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                .header("X-Environment-Id", ownerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isCreated());
        
        // When: GET queues by owner (controller filters via X-Environment-Id header)
        mockMvc.perform(get(QUEUE_BASE_PATH)
                .header("X-Environment-Id", ownerId.toString()))
            // Then: Returns 200 OK
            .andExpect(status().isOk())
            // And: Response contains both queues (DLQ is now a Redis Stream, not a PostgreSQL queue)
            .andExpect(jsonPath("$.queues.queue", hasSize(2)))
            .andExpect(jsonPath("$.queues.queue[*].name", hasItems("queue-1", "queue-2")))
            .andExpect(jsonPath("$.queues.queue[*].ownerId", everyItem(is(ownerId.toString()))));
    }
    
    /**
     * Test: Get queues by owner with no queues
     * 
     * **Validates: Requirements 1.4, 14.1, 14.2, 14.3, 14.4**
     */
    @Test
    void testGetQueuesByOwner_WithNoQueues_ReturnsEmptyList() throws Exception {
        // Given: An owner with no queues
        UUID ownerId = TEST_OWNER_ID;
        
        // When: GET queues by owner (no queues, controller filters via X-Environment-Id header)
        mockMvc.perform(get(QUEUE_BASE_PATH)
                .header("X-Environment-Id", ownerId.toString()))
            // Then: Returns 200 OK with empty list
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queues.queue", hasSize(0)));
    }
    
    /**
     * Test: Get queues by owner filters correctly
     * 
     * **Validates: Requirements 1.4, 14.1, 14.2, 14.3, 14.4**
     */
    @Test
    void testGetQueuesByOwner_FiltersCorrectly_ReturnsOnlyOwnerQueues() throws Exception {
        // Given: Queues for different owners
        UUID owner1 = TEST_OWNER_ID;
        UUID owner2 = TEST_OWNER_ID_2;
        
        // Create queue for owner1
        QueueCreateRequest request1 = new QueueCreateRequest(new CreateQueueRequest("owner1-queue", owner1, 3, 5000L, null));
        mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                .header("X-Environment-Id", owner1.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isCreated());
        
        // Create queue for owner2
        QueueCreateRequest request2 = new QueueCreateRequest(new CreateQueueRequest("owner2-queue", owner2, 3, 5000L, null));
        mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                .header("X-Environment-Id", owner2.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isCreated());
        
        // When: GET queues filtered by owner1 (controller filters via X-Environment-Id header)
        mockMvc.perform(get(QUEUE_BASE_PATH)
                .header("X-Environment-Id", owner1.toString()))
            // Then: Returns only owner1's queue (DLQ is now a Redis Stream, not a PostgreSQL queue)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.queues.queue", hasSize(1)))
            .andExpect(jsonPath("$.queues.queue[*].name", hasItems("owner1-queue")))
            .andExpect(jsonPath("$.queues.queue[*].ownerId", everyItem(is(owner1.toString()))));
    }
    
    /**
     * Test: Delete queue
     * 
     * **Validates: Requirements 1.5, 14.1, 14.2, 14.3, 14.4**
     */
    @Test
    void testDeleteQueue_WithExistingId_ReturnsNoContent() throws Exception {
        // Given: An existing queue
        UUID ownerId = TEST_OWNER_ID;
        QueueCreateRequest createRequest = new QueueCreateRequest(new CreateQueueRequest(
            "test-queue",
            ownerId,
            3,
            5000L,
            null
        ));
        
        MvcResult createResult = mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                .header("X-Environment-Id", ownerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andReturn();
        
        String queueId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .get("queue").get("id").asText();
        
        // When: DELETE queue by ID
        mockMvc.perform(delete(QUEUE_BASE_PATH + "/{queueId}", queueId)
                .header("X-Environment-Id", ownerId.toString()))
            // Then: Returns 202 Accepted (async cleanup)
            .andExpect(status().isAccepted());
    }
    
    /**
     * Test: Delete non-existent queue
     * 
     * **Validates: Requirements 1.5, 14.1, 14.2, 14.3, 14.4**
     */
    @Test
    void testDeleteQueue_WithNonExistentId_ReturnsNotFound() throws Exception {
        // Given: A non-existent queue ID
        UUID nonExistentId = UUID.randomUUID();
        
        // When: DELETE non-existent queue
        mockMvc.perform(delete(QUEUE_BASE_PATH + "/{queueId}", nonExistentId)
                .header("X-Environment-Id", TEST_OWNER_ID.toString()))
            // Then: Returns 404 Not Found
            .andExpect(status().isNotFound());
    }
    
    /**
     * Test: Get queue statistics
     * 
     * **Validates: Requirements 12.1, 12.2, 12.3, 12.4, 14.1, 14.2, 14.3, 14.4**
     */
    @Test
    void testGetQueueStatistics_WithExistingQueue_ReturnsOk() throws Exception {
        // Given: An existing queue
        UUID ownerId = TEST_OWNER_ID;
        QueueCreateRequest createRequest = new QueueCreateRequest(new CreateQueueRequest(
            "test-queue",
            ownerId,
            3,
            5000L,
            null
        ));
        
        MvcResult createResult = mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                .header("X-Environment-Id", ownerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andReturn();
        
        String queueId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .get("queue").get("id").asText();
        
        // When: GET queue stats
        mockMvc.perform(get(QUEUE_BASE_PATH + "/{queueId}/stats", queueId)
                .header("X-Environment-Id", ownerId.toString()))
            // Then: Returns 200 OK
            .andExpect(status().isOk())
            // And: Response contains statistics
            .andExpect(jsonPath("$.queueId").value(queueId))
            .andExpect(jsonPath("$.pendingCount").exists())
            .andExpect(jsonPath("$.deliveredCount").exists())
            .andExpect(jsonPath("$.deadLetterCount").exists())
            .andExpect(jsonPath("$.totalProcessed").exists());
    }
    
    /**
     * Test: Get statistics for non-existent queue
     * 
     * **Validates: Requirements 12.1, 12.2, 12.3, 12.4, 14.1, 14.2, 14.3, 14.4**
     */
    @Test
    void testGetQueueStatistics_WithNonExistentId_ReturnsNotFound() throws Exception {
        // Given: A non-existent queue ID
        UUID nonExistentId = UUID.randomUUID();
        
        // When: GET stats for non-existent queue
        mockMvc.perform(get(QUEUE_BASE_PATH + "/{queueId}/stats", nonExistentId)
                .header("X-Environment-Id", TEST_OWNER_ID.toString()))
            // Then: Returns 404 Not Found
            .andExpect(status().isNotFound());
    }
    
    /**
     * Test: Create queue returns correct content type
     * 
     * **Validates: Requirements 14.1, 14.2, 14.3, 14.4**
     */
    @Test
    void testCreateQueue_ReturnsJsonContentType() throws Exception {
        // Given: Valid queue creation request
        UUID ownerId = TEST_OWNER_ID;
        QueueCreateRequest request = new QueueCreateRequest(new CreateQueueRequest(
            "test-queue",
            ownerId,
            3,
            5000L,
            null
        ));
        
        // When: POST to queue endpoint (content type check)
        mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                .header("X-Environment-Id", ownerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            // Then: Returns JSON content type
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
    
    /**
     * Test: Get queue returns correct content type
     * 
     * **Validates: Requirements 14.1, 14.2, 14.3, 14.4**
     */
    @Test
    void testGetQueue_ReturnsJsonContentType() throws Exception {
        // Given: An existing queue
        UUID ownerId = TEST_OWNER_ID;
        QueueCreateRequest createRequest = new QueueCreateRequest(new CreateQueueRequest(
            "test-queue",
            ownerId,
            3,
            5000L,
            null
        ));
        
        MvcResult createResult = mockMvc.perform(post(QUEUE_BASE_PATH + "/queue")
                .header("X-Environment-Id", ownerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andReturn();
        
        String queueId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .get("queue").get("id").asText();
        
        // When: GET queue by ID (content type check)
        mockMvc.perform(get(QUEUE_BASE_PATH + "/{queueId}", queueId)
                .header("X-Environment-Id", ownerId.toString()))
            // Then: Returns JSON content type
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}
