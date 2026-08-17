// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;
import org.sifunity.broker.common.exception.QueueNotFoundException;
import org.sifunity.broker.common.exception.SubscriptionNotFoundException;
import org.sifunity.broker.subscriptionservice.client.QueueServiceClient;
import org.sifunity.broker.subscriptionservice.config.TestConfig;
import org.sifunity.broker.subscriptionservice.dto.SubscriptionCreateRequest;
import org.sifunity.broker.subscriptionservice.exception.AccessTypeMismatchException;
import org.sifunity.broker.subscriptionservice.exception.AclEntryNotFoundException;
import org.sifunity.broker.subscriptionservice.exception.DuplicateSubscriptionException;
import org.sifunity.broker.subscriptionservice.health.QueueServiceHealthIndicator;
import org.sifunity.broker.subscriptionservice.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for SubscriptionController using MockMvc.
 * 
 * <p>Tests cover:</p>
 * <ul>
 *   <li>POST /subscriptions - Create subscription</li>
 *   <li>GET /subscriptions/events - Query EVENT subscriptions</li>
 *   <li>GET /subscriptions/delayed-requests - Query DELAYED_REQUEST subscription</li>
 *   <li>GET /subscriptions/{id} - Get subscription by ID</li>
 *   <li>GET /subscriptions?queueId={id} - List subscriptions by queue</li>
 *   <li>DELETE /subscriptions/{id} - Delete subscription</li>
 *   <li>Error handling for various failure scenarios</li>
 * </ul>
 */
@WebMvcTest(SubscriptionController.class)
@Import(TestConfig.class)
@DisplayName("SubscriptionController Unit Tests")
class SubscriptionControllerTest {
    
    private static final String TEST_DOMAIN_ID = "d47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String TEST_SOLUTION_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    private static final String BASE_PATH = "/" + TEST_DOMAIN_ID + "/" + TEST_SOLUTION_ID + "/subscriptions";
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private SubscriptionService subscriptionService;
    
    @MockBean
    private QueueServiceClient queueServiceClient;
    
    @MockBean
    private QueueServiceHealthIndicator queueServiceHealthIndicator;
    
    private UUID queueId;
    private UUID subscriptionId;
    private UUID aclEntryId;
    private UUID environmentId;
    private String serviceName;
    private UUID contextUuid;
    private Instant createdAt;
    
    @BeforeEach
    void setUp() {
        queueId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        aclEntryId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        serviceName = "StudentPersonal";
        contextUuid = UUID.randomUUID();
        createdAt = Instant.now();
    }
    
    @Nested
    @DisplayName("POST /subscriptions/subscription - Create Subscription Tests")
    class CreateSubscriptionTests {
        
        @Test
        @DisplayName("Should create subscription and return 201 Created")
        void shouldCreateSubscriptionAndReturn201() throws Exception {
            // Given
            SubscriptionDTO inner = new SubscriptionDTO(
                null, queueId, aclEntryId, serviceName, "test-zone", "test-ctx", SubscriptionType.EVENT, null
            );
            SubscriptionCreateRequest request = new SubscriptionCreateRequest(inner);
            
            SubscriptionDTO response = new SubscriptionDTO(
                subscriptionId, queueId, aclEntryId, serviceName, "test-zone", "test-ctx", SubscriptionType.EVENT, null
            );
            
            when(subscriptionService.createSubscription(anyString(), anyString(), any(SubscriptionDTO.class)))
                .thenReturn(response);
            
            // When/Then
            mockMvc.perform(post(BASE_PATH + "/subscription")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscription.id").value(subscriptionId.toString()))
                .andExpect(jsonPath("$.subscription.queueId").value(queueId.toString()))
                .andExpect(jsonPath("$.subscription.aclEntryId").doesNotExist())
                .andExpect(jsonPath("$.subscription.serviceName").value(serviceName))
                .andExpect(jsonPath("$.subscription.zoneId").value("test-zone"))
                .andExpect(jsonPath("$.subscription.messageType").value("EVENT"))
                .andExpect(jsonPath("$.subscription.createdAt").doesNotExist());
            
            verify(subscriptionService).createSubscription(anyString(), anyString(), any(SubscriptionDTO.class));
        }
        
        @Test
        @DisplayName("Should create subscription with null contextUuid and return 201")
        void shouldCreateSubscriptionWithNullContextUuid() throws Exception {
            // Given
            SubscriptionDTO inner = new SubscriptionDTO(
                null, queueId, aclEntryId, serviceName, "test-zone", (String) null, SubscriptionType.EVENT, null
            );
            SubscriptionCreateRequest request = new SubscriptionCreateRequest(inner);
            
            SubscriptionDTO response = new SubscriptionDTO(
                subscriptionId, queueId, aclEntryId, serviceName, "test-zone", (String) null, SubscriptionType.EVENT, null
            );
            
            when(subscriptionService.createSubscription(anyString(), anyString(), any(SubscriptionDTO.class)))
                .thenReturn(response);
            
            // When/Then
            mockMvc.perform(post(BASE_PATH + "/subscription")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscription.id").value(subscriptionId.toString()))
                // D4 (2.10): zoneId is required now, so absence is asserted on contextId — the field
                // this test is actually named for, and the one that remains optional.
                .andExpect(jsonPath("$.subscription.contextId").doesNotExist())
                .andExpect(jsonPath("$.subscription.zoneId").value("test-zone"));
            
            verify(subscriptionService).createSubscription(anyString(), anyString(), any(SubscriptionDTO.class));
        }
        
        @Test
        @DisplayName("Should return 404 Not Found when queue does not exist")
        void shouldReturn404WhenQueueDoesNotExist() throws Exception {
            // Given
            SubscriptionDTO inner = new SubscriptionDTO(
                null, queueId, aclEntryId, serviceName, "test-zone", "test-ctx", SubscriptionType.EVENT, null
            );
            SubscriptionCreateRequest request = new SubscriptionCreateRequest(inner);
            
            when(subscriptionService.createSubscription(anyString(), anyString(), any(SubscriptionDTO.class)))
                .thenThrow(new QueueNotFoundException("Queue not found: " + queueId));
            
            // When/Then
            mockMvc.perform(post(BASE_PATH + "/subscription")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
            
            verify(subscriptionService).createSubscription(anyString(), anyString(), any(SubscriptionDTO.class));
        }
        
        @Test
        @DisplayName("Should return 409 Conflict when duplicate subscription exists")
        void shouldReturn409WhenDuplicateSubscriptionExists() throws Exception {
            // Given
            SubscriptionDTO inner = new SubscriptionDTO(
                null, queueId, aclEntryId, serviceName, "test-zone", "test-ctx", SubscriptionType.EVENT, null
            );
            SubscriptionCreateRequest request = new SubscriptionCreateRequest(inner);
            
            when(subscriptionService.createSubscription(anyString(), anyString(), any(SubscriptionDTO.class)))
                .thenThrow(new DuplicateSubscriptionException("Subscription already exists"));
            
            // When/Then
            mockMvc.perform(post(BASE_PATH + "/subscription")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
            
            verify(subscriptionService).createSubscription(anyString(), anyString(), any(SubscriptionDTO.class));
        }
        
        @Test
        @DisplayName("Should return 400 Bad Request when a caller-supplied ACL entry does not exist")
        void shouldReturn400WhenAclEntryNotFound() throws Exception {
            // Given
            SubscriptionDTO inner = new SubscriptionDTO(
                null, queueId, aclEntryId, serviceName, "test-zone", "test-ctx", SubscriptionType.EVENT, null
            );
            SubscriptionCreateRequest request = new SubscriptionCreateRequest(inner);
            
            when(subscriptionService.createSubscription(anyString(), anyString(), any(SubscriptionDTO.class)))
                .thenThrow(new AclEntryNotFoundException("ACL entry not found: " + aclEntryId));

            // When/Then — D7 (2.16): POST /subscriptions/subscription declares no 404. A
            // caller-supplied aclEntryId that does not resolve is a malformed reference, so 400.
            // "Caller holds no matching grant" is the separate AclEntryNotHeldException -> 403.
            // The 404 on QueueNotFoundException above IS declared and stays unchanged.
            mockMvc.perform(post(BASE_PATH + "/subscription")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                // The body's code must agree with the HTTP status. This test previously asserted only
                // the status, which let a real defect through: the handler returned 400 while the
                // exception still carried NOT_FOUND, and SifErrorResponse.fromException builds
                // error.code from the exception's status -- so the body advertised the undeclared 404
                // that D7 exists to remove.
                .andExpect(jsonPath("$.error.code").value(400));
            
            verify(subscriptionService).createSubscription(anyString(), anyString(), any(SubscriptionDTO.class));
        }
        
        @Test
        @DisplayName("Should return 403 Forbidden when access type does not match subscription type")
        void shouldReturn400WhenAccessTypeMismatch() throws Exception {
            // Given
            SubscriptionDTO inner = new SubscriptionDTO(
                null, queueId, aclEntryId, serviceName, "test-zone", "test-ctx", SubscriptionType.EVENT, null
            );
            SubscriptionCreateRequest request = new SubscriptionCreateRequest(inner);
            
            when(subscriptionService.createSubscription(anyString(), anyString(), any(SubscriptionDTO.class)))
                .thenThrow(new AccessTypeMismatchException(
                    "Cannot create EVENT subscription: ACL entry has access_type=PROVIDE, expected=SUBSCRIBE"));
            
            // When/Then
            mockMvc.perform(post(BASE_PATH + "/subscription")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
            
            verify(subscriptionService).createSubscription(anyString(), anyString(), any(SubscriptionDTO.class));
        }
    }
    
    @Nested
    @DisplayName("GET /subscriptions/events - Query EVENT Subscriptions Tests")
    class QueryEventSubscriptionsTests {
        
        @Test
        @DisplayName("Should return matching EVENT subscriptions with 200 OK")
        void shouldReturnMatchingEventSubscriptions() throws Exception {
            // Given
            SubscriptionDTO subscription1 = new SubscriptionDTO(
                UUID.randomUUID(), queueId, aclEntryId, serviceName, "test-zone", "test-ctx", SubscriptionType.EVENT, createdAt
            );
            
            SubscriptionDTO subscription2 = new SubscriptionDTO(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), serviceName, "test-zone", (String) null, SubscriptionType.EVENT, createdAt
            );
            
            List<SubscriptionDTO> subscriptions = Arrays.asList(subscription1, subscription2);
            
            when(subscriptionService.findEventSubscriptions(serviceName, contextUuid))
                .thenReturn(subscriptions);
            
            // When/Then
            mockMvc.perform(get(BASE_PATH + "/events")
                    .param("serviceName", serviceName)
                    .param("contextUuid", contextUuid.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions.subscription", hasSize(2)))
                .andExpect(jsonPath("$.subscriptions.subscription[0].serviceName").value(serviceName))
                .andExpect(jsonPath("$.subscriptions.subscription[0].zoneId").value("test-zone"))
                .andExpect(jsonPath("$.subscriptions.subscription[0].messageType").value("EVENT"));
            
            verify(subscriptionService).findEventSubscriptions(serviceName, contextUuid);
        }
        
        @Test
        @DisplayName("Should return empty list when no EVENT subscriptions match")
        void shouldReturnEmptyListWhenNoEventSubscriptionsMatch() throws Exception {
            // Given
            when(subscriptionService.findEventSubscriptions(serviceName, contextUuid))
                .thenReturn(Collections.emptyList());
            
            // When/Then
            mockMvc.perform(get(BASE_PATH + "/events")
                    .param("serviceName", serviceName)
                    .param("contextUuid", contextUuid.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions.subscription", hasSize(0)));
            
            verify(subscriptionService).findEventSubscriptions(serviceName, contextUuid);
        }
        
        @Test
        @DisplayName("Should return 400 Bad Request when required parameters are missing")
        void shouldReturn400WhenRequiredParametersAreMissing() throws Exception {
            // When/Then - Missing serviceName parameter
            mockMvc.perform(get(BASE_PATH + "/events")
                    .param("contextUuid", contextUuid.toString()))
                .andExpect(status().isBadRequest());
            
            verify(subscriptionService, never()).findEventSubscriptions(any(), any());
        }
    }
    
    @Nested
    @DisplayName("GET /subscriptions/delayed-requests - Query DELAYED_REQUEST Subscription Tests")
    class QueryDelayedRequestSubscriptionTests {
        
        @Test
        @DisplayName("Should return DELAYED_REQUEST subscription with 200 OK")
        void shouldReturnDelayedRequestSubscription() throws Exception {
            // Given
            SubscriptionDTO subscription = new SubscriptionDTO(
                subscriptionId, queueId, aclEntryId, serviceName, "test-zone", "test-ctx", SubscriptionType.DELAYED_REQUEST, createdAt
            );
            
            when(subscriptionService.findDelayedRequestSubscription(serviceName, contextUuid))
                .thenReturn(subscription);
            
            // When/Then
            mockMvc.perform(get(BASE_PATH + "/delayed-requests")
                    .param("serviceName", serviceName)
                    .param("contextUuid", contextUuid.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(subscriptionId.toString()))
                .andExpect(jsonPath("$.serviceName").value(serviceName))
                .andExpect(jsonPath("$.zoneId").value("test-zone"))
                .andExpect(jsonPath("$.messageType").value("DELAYED_REQUEST"));
            
            verify(subscriptionService).findDelayedRequestSubscription(serviceName, contextUuid);
        }
        
        @Test
        @DisplayName("Should return 404 Not Found when no DELAYED_REQUEST subscription exists")
        void shouldReturn404WhenNoDelayedRequestSubscriptionExists() throws Exception {
            // Given
            when(subscriptionService.findDelayedRequestSubscription(serviceName, contextUuid))
                .thenThrow(new SubscriptionNotFoundException("No DELAYED_REQUEST subscription found"));
            
            // When/Then
            mockMvc.perform(get(BASE_PATH + "/delayed-requests")
                    .param("serviceName", serviceName)
                    .param("contextUuid", contextUuid.toString()))
                .andExpect(status().isNotFound());
            
            verify(subscriptionService).findDelayedRequestSubscription(serviceName, contextUuid);
        }
    }
    
    @Nested
    @DisplayName("GET /subscriptions/{id} - Get Subscription By ID Tests")
    class GetSubscriptionByIdTests {
        
        @Test
        @DisplayName("Should return subscription with 200 OK when ID exists")
        void shouldReturnSubscriptionWhenIdExists() throws Exception {
            // Given
            SubscriptionDTO subscription = new SubscriptionDTO(
                subscriptionId, queueId, aclEntryId, serviceName, "test-zone", "test-ctx", SubscriptionType.EVENT, createdAt
            );
            
            when(subscriptionService.getSubscription(subscriptionId))
                .thenReturn(subscription);
            
            // When/Then
            mockMvc.perform(get(BASE_PATH + "/{id}", subscriptionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscription.id").value(subscriptionId.toString()))
                .andExpect(jsonPath("$.subscription.queueId").value(queueId.toString()))
                .andExpect(jsonPath("$.subscription.aclEntryId").doesNotExist())
                .andExpect(jsonPath("$.subscription.serviceName").value(serviceName))
                .andExpect(jsonPath("$.subscription.zoneId").value("test-zone"))
                .andExpect(jsonPath("$.subscription.messageType").value("EVENT"));
            
            verify(subscriptionService).getSubscription(subscriptionId);
        }
        
        @Test
        @DisplayName("Should return 404 Not Found when subscription ID does not exist")
        void shouldReturn404WhenSubscriptionIdDoesNotExist() throws Exception {
            // Given
            when(subscriptionService.getSubscription(subscriptionId))
                .thenThrow(new SubscriptionNotFoundException("Subscription not found: " + subscriptionId));
            
            // When/Then
            mockMvc.perform(get(BASE_PATH + "/{id}", subscriptionId))
                .andExpect(status().isNotFound());
            
            verify(subscriptionService).getSubscription(subscriptionId);
        }
    }
    
    @Nested
    @DisplayName("GET /subscriptions - List Subscriptions Tests")
    class ListSubscriptionsByQueueTests {
        
        @Test
        @DisplayName("Should return all subscriptions for a queue with 200 OK when queueId is provided")
        void shouldReturnAllSubscriptionsForQueue() throws Exception {
            // Given
            SubscriptionDTO subscription1 = new SubscriptionDTO(
                UUID.randomUUID(), queueId, aclEntryId, serviceName, "test-zone", "test-ctx", SubscriptionType.EVENT, createdAt
            );
            
            SubscriptionDTO subscription2 = new SubscriptionDTO(
                UUID.randomUUID(), queueId, UUID.randomUUID(), "SchoolInfo", "test-zone", "test-ctx", SubscriptionType.DELAYED_REQUEST, createdAt
            );
            
            List<SubscriptionDTO> subscriptions = Arrays.asList(subscription1, subscription2);
            
            when(subscriptionService.findSubscriptionsByQueue(queueId))
                .thenReturn(subscriptions);
            
            // When/Then
            mockMvc.perform(get(BASE_PATH)
                    .param("queueId", queueId.toString())
                    .header("X-Environment-Id", environmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions.subscription", hasSize(2)))
                .andExpect(jsonPath("$.subscriptions.subscription[0].queueId").value(queueId.toString()))
                .andExpect(jsonPath("$.subscriptions.subscription[1].queueId").value(queueId.toString()))
                .andExpect(jsonPath("$.subscriptions.subscription[*].messageType", containsInAnyOrder("EVENT", "DELAYED_REQUEST")));
            
            verify(subscriptionService).findSubscriptionsByQueue(queueId);
            verify(subscriptionService, never()).findSubscriptionsByEnvironment(any());
        }
        
        @Test
        @DisplayName("Should return empty list when queue has no subscriptions")
        void shouldReturnEmptyListWhenQueueHasNoSubscriptions() throws Exception {
            // Given
            when(subscriptionService.findSubscriptionsByQueue(queueId))
                .thenReturn(Collections.emptyList());
            
            // When/Then
            mockMvc.perform(get(BASE_PATH)
                    .param("queueId", queueId.toString())
                    .header("X-Environment-Id", environmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions.subscription", hasSize(0)));
            
            verify(subscriptionService).findSubscriptionsByQueue(queueId);
            verify(subscriptionService, never()).findSubscriptionsByEnvironment(any());
        }
        
        @Test
        @DisplayName("Should return all subscriptions for environment when queueId is omitted")
        void shouldReturnAllSubscriptionsForEnvironmentWhenQueueIdOmitted() throws Exception {
            // Given
            SubscriptionDTO subscription1 = new SubscriptionDTO(
                UUID.randomUUID(), queueId, aclEntryId, serviceName, "test-zone", "test-ctx", SubscriptionType.EVENT, createdAt
            );
            
            SubscriptionDTO subscription2 = new SubscriptionDTO(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SchoolInfo", "zone-2", "ctx-2", SubscriptionType.DELAYED_REQUEST, createdAt
            );
            
            List<SubscriptionDTO> subscriptions = Arrays.asList(subscription1, subscription2);
            
            when(subscriptionService.findSubscriptionsByEnvironment(environmentId))
                .thenReturn(subscriptions);
            
            // When/Then
            mockMvc.perform(get(BASE_PATH)
                    .header("X-Environment-Id", environmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions.subscription", hasSize(2)))
                .andExpect(jsonPath("$.subscriptions.subscription[0].serviceName").value(serviceName))
                .andExpect(jsonPath("$.subscriptions.subscription[1].serviceName").value("SchoolInfo"));
            
            verify(subscriptionService).findSubscriptionsByEnvironment(environmentId);
            verify(subscriptionService, never()).findSubscriptionsByQueue(any());
        }
        
        @Test
        @DisplayName("Should return empty list when environment has no subscriptions")
        void shouldReturnEmptyListWhenEnvironmentHasNoSubscriptions() throws Exception {
            // Given
            when(subscriptionService.findSubscriptionsByEnvironment(environmentId))
                .thenReturn(Collections.emptyList());
            
            // When/Then
            mockMvc.perform(get(BASE_PATH)
                    .header("X-Environment-Id", environmentId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions.subscription", hasSize(0)));
            
            verify(subscriptionService).findSubscriptionsByEnvironment(environmentId);
            verify(subscriptionService, never()).findSubscriptionsByQueue(any());
        }
        
        @Test
        @DisplayName("Should return 400 Bad Request when X-Environment-Id header is missing")
        void shouldReturn400WhenEnvironmentIdHeaderIsMissing() throws Exception {
            // When/Then
            mockMvc.perform(get(BASE_PATH))
                .andExpect(status().isBadRequest());
            
            verify(subscriptionService, never()).findSubscriptionsByQueue(any());
            verify(subscriptionService, never()).findSubscriptionsByEnvironment(any());
        }
    }
    
    @Nested
    @DisplayName("DELETE /subscriptions/{id} - Delete Subscription Tests")
    class DeleteSubscriptionTests {
        
        @Test
        @DisplayName("Should delete subscription and return 204 No Content")
        void shouldDeleteSubscriptionAndReturn204() throws Exception {
            // Given
            doNothing().when(subscriptionService).deleteSubscription(subscriptionId);
            
            // When/Then
            mockMvc.perform(delete(BASE_PATH + "/{id}", subscriptionId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
            
            verify(subscriptionService).deleteSubscription(subscriptionId);
        }
        
        @Test
        @DisplayName("Should return 404 Not Found when subscription ID does not exist")
        void shouldReturn404WhenDeletingNonExistentSubscription() throws Exception {
            // Given
            doThrow(new SubscriptionNotFoundException("Subscription not found: " + subscriptionId))
                .when(subscriptionService).deleteSubscription(subscriptionId);
            
            // When/Then
            mockMvc.perform(delete(BASE_PATH + "/{id}", subscriptionId))
                .andExpect(status().isNotFound());
            
            verify(subscriptionService).deleteSubscription(subscriptionId);
        }
    }
}
