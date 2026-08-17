// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.responsesconnector.dto.EnqueueResponseDTO;
import org.sifunity.responsesconnector.dto.ResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests for response routing.
 * Tests complete response routing flow with mocked Queue Service.
 * 
 * <p>Uses full Spring context with MockMvc for REST API testing and
 * WireMock for mocking Queue Service.</p>
 * 
 * <p><b>Test Coverage:</b></p>
 * <ul>
 *   <li>Complete response routing flow from controller to Queue Service</li>
 *   <li>Error response flow with SIF error format</li>
 *   <li>Internal response flow from Requests Connector</li>
 *   <li>Queue Service error handling (404, 503, timeout)</li>
 *   <li>Header validation and environment context</li>
 *   <li>Request body validation</li>
 * </ul>
 * 
 * <p><b>Requirements:</b> All</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ResponseRoutingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static WireMockServer wireMockServer;

    static {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        stubDestinationGateDependencies();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.resetAll();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + (wireMockServer != null ? wireMockServer.port() : 0);
        registry.add("queue.service.url", () -> base);
        // The destination gate calls environment-service for the entitlement check, so that client
        // must point at WireMock too. Left at its real default, every public-path test 404s: the
        // gate fails closed on an unreachable environment-service.
        registry.add("environment.service.url", () -> base);
    }

    /**
     * Default stubs for the two lookups the public response path performs before enqueuing.
     *
     * <p>{@code ResponseDestinationValidator} resolves the destination queue's owner and then asks
     * whether that owner holds any approved grant. <b>Both fail closed</b>, so without these stubs
     * WireMock returns 404 for the owner lookup, the gate denies, and every positive public-path
     * assertion here fails with 404 instead of 202 — a hard CI failure that does not reproduce in a
     * sandbox where WireMock cannot bind a socket and these tests error out before asserting.
     *
     * <p>Registered per-test in {@link #setUp()} because {@code resetAll()} clears them. Individual
     * tests may override either stub to exercise a denial.
     */
    private void stubDestinationGateDependencies() {
        // Queue-owner lookup: GET /{domainId}/{solutionId}/queues/{queueId} -> { "queue": { ... } }
        wireMockServer.stubFor(WireMock.get(urlPathMatching("/[^/]+/[^/]+/queues/[^/]+"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"queue\":{\"id\":\"" + UUID.randomUUID()
                    + "\",\"ownerId\":\"" + DESTINATION_OWNER_ID + "\"}}")));

        // Entitlement check: POST /internal/check-any-permission -> { "granted": true }
        wireMockServer.stubFor(WireMock.post(urlPathEqualTo("/internal/check-any-permission"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"granted\":true}")));
    }

    /** Owner returned for every destination queue by the default stub above. */
    private static final UUID DESTINATION_OWNER_ID = UUID.randomUUID();

    // ========== Provider Response Flow Tests ==========

    @Test
    void shouldRouteProviderResponseToConsumerQueue() throws Exception {
        // Arrange
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID environmentId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        
        // Mock Queue Service response
        EnqueueResponseDTO enqueueResponse = EnqueueResponseDTO.builder()
            .messageId(messageId)
            .status("ENQUEUED")
            .build();
        
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/test-domain/test-solution/queues/.*/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(objectMapper.writeValueAsString(enqueueResponse))));
        
        // Create response payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("refId", "abc123");
        payload.put("name", Map.of("givenName", "John", "familyName", "Doe"));
        
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(payload)
            .build();
        
        // Act & Assert
        mockMvc.perform(post("/test-domain/test-solution/responses")
                .header("X-Environment-Id", environmentId.toString())
                .header("X-Environment-Name", "test-env")
                .header("queueId", queueId.toString())
                .header("requestId", requestId.toString())
                .header("relativeServicePath", "StudentPersonals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(response)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.messageId").exists())
            .andExpect(jsonPath("$.queueId").value(queueId.toString()))
            .andExpect(jsonPath("$.status").value("DELIVERED"))
            .andExpect(jsonPath("$.timestamp").exists());
        
        // Verify Queue Service was called
        wireMockServer.verify(postRequestedFor(urlPathMatching("/test-domain/test-solution/queues/.*/messages")));
    }

    @Test
    void shouldPreserveCompleteResponsePayload() throws Exception {
        // Arrange
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID environmentId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        
        // Mock Queue Service response
        EnqueueResponseDTO enqueueResponse = EnqueueResponseDTO.builder()
            .messageId(messageId)
            .status("ENQUEUED")
            .build();
        
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/test-domain/test-solution/queues/.*/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(objectMapper.writeValueAsString(enqueueResponse))));
        
        // Create complex response payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("refId", "abc123");
        payload.put("name", Map.of("givenName", "John", "familyName", "Doe"));
        payload.put("demographics", Map.of("birthDate", "2005-01-15", "gender", "M"));
        payload.put("addresses", java.util.List.of(
            Map.of("type", "HOME", "street", "123 Main St", "city", "Springfield")
        ));
        
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(payload)
            .build();
        
        // Act
        mockMvc.perform(post("/test-domain/test-solution/responses")
                .header("relativeServicePath", "StudentPersonals")
                .header("X-Environment-Id", environmentId.toString())
                .header("X-Environment-Name", "test-env")
                .header("queueId", queueId.toString())
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(response)))
            .andExpect(status().isAccepted());
        
        // Verify payload was sent to Queue Service
        wireMockServer.verify(postRequestedFor(urlPathMatching("/test-domain/test-solution/queues/.*/messages"))
            .withRequestBody(containing("abc123"))
            .withRequestBody(containing("John"))
            .withRequestBody(containing("Doe"))
            .withRequestBody(containing("demographics"))
            .withRequestBody(containing("addresses")));
    }

    // ========== Internal Response Flow Tests ==========

    @Test
    void shouldRouteInternalResponseToConsumerQueue() throws Exception {
        // Arrange
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID messageId = UUID.randomUUID();
        
        // Mock Queue Service response
        EnqueueResponseDTO enqueueResponse = EnqueueResponseDTO.builder()
            .messageId(messageId)
            .status("ENQUEUED")
            .build();
        
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/.+/.+/queues/.*/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(objectMapper.writeValueAsString(enqueueResponse))));
        
        // Create response payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("refId", "xyz789");
        payload.put("data", "test data");
        
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(payload)
            .build();
        
        // Act & Assert
        mockMvc.perform(post("/internal/responses")
                .header("X-Internal-Service-Token", "test-internal-token")
                .header("queueId", queueId.toString())
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(response)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messageId").exists())
            .andExpect(jsonPath("$.status").value("DELIVERED"))
            .andExpect(jsonPath("$.timestamp").exists());
        
        // Verify Queue Service was called
        wireMockServer.verify(postRequestedFor(urlPathMatching("/.+/.+/queues/.*/messages")));
    }

    @Test
    void shouldRouteInternalErrorResponseToConsumerQueue() throws Exception {
        // Arrange
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID messageId = UUID.randomUUID();
        
        // Mock Queue Service response
        EnqueueResponseDTO enqueueResponse = EnqueueResponseDTO.builder()
            .messageId(messageId)
            .status("ENQUEUED")
            .build();
        
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/.+/.+/queues/.*/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(objectMapper.writeValueAsString(enqueueResponse))));
        
        String errorMessage = "Provider timeout: no response received within 30 seconds";

        // The shape requests-connector actually sends: an application/json Error Object. This test
        // previously posted text/plain, which is why it stayed green while a JSON body was being
        // flattened into the message text — see InternalErrorFidelityTest.
        String body = objectMapper.writeValueAsString(Map.of("error", Map.of(
            "code", 504,
            "subCode", "PROVIDER_TIMEOUT",
            "message", errorMessage,
            "scope", "PROVIDER")));

        // Act & Assert
        mockMvc.perform(post("/internal/responses/error")
                .header("X-Internal-Service-Token", "test-internal-token")
                .header("queueId", queueId.toString())
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messageId").exists())
            .andExpect(jsonPath("$.status").value("ERROR_DELIVERED"))
            .andExpect(jsonPath("$.timestamp").exists());

        // The sender's classification must reach the queue, not a hardcoded broker-internal 500
        wireMockServer.verify(postRequestedFor(urlPathMatching("/.+/.+/queues/.*/messages"))
            .withRequestBody(containing("PROVIDER_TIMEOUT"))
            .withRequestBody(containing("PROVIDER"))
            .withRequestBody(containing(errorMessage)));
    }

    // ========== Error Handling Tests ==========

    @Test
    void shouldHandleQueueNotFound() throws Exception {
        // Arrange
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID environmentId = UUID.randomUUID();
        
        // Mock Queue Service 404 response
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/test-domain/test-solution/queues/.*/messages"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"code\":\"QUEUE_NOT_FOUND\",\"message\":\"Queue not found\"}}")));
        
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(Map.of("data", "test"))
            .build();
        
        // Act & Assert
        mockMvc.perform(post("/test-domain/test-solution/responses")
                .header("relativeServicePath", "StudentPersonals")
                .header("X-Environment-Id", environmentId.toString())
                .header("X-Environment-Name", "test-env")
                .header("queueId", queueId.toString())
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(response)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.subCode").value("QUEUE_NOT_FOUND"))
            .andExpect(jsonPath("$.error.message").exists())
            .andExpect(jsonPath("$.error.scope").value("BROKER"));
    }

    @Test
    void shouldHandleQueueServiceUnavailable() throws Exception {
        // Arrange
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID environmentId = UUID.randomUUID();
        
        // Mock Queue Service 503 response
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/test-domain/test-solution/queues/.*/messages"))
            .willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"Service unavailable\"}}")));
        
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(Map.of("data", "test"))
            .build();
        
        // Act & Assert
        mockMvc.perform(post("/test-domain/test-solution/responses")
                .header("relativeServicePath", "StudentPersonals")
                .header("X-Environment-Id", environmentId.toString())
                .header("X-Environment-Name", "test-env")
                .header("queueId", queueId.toString())
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(response)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.subCode").value("SERVICE_UNAVAILABLE"))
            .andExpect(jsonPath("$.error.message").exists())
            .andExpect(jsonPath("$.error.scope").value("BROKER"));
    }

    @Test
    void shouldRetryOnTransientQueueServiceFailure() throws Exception {
        // Arrange
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID environmentId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        
        // Mock Queue Service: first call fails (503), second call succeeds
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/test-domain/test-solution/queues/.*/messages"))
            .inScenario("Retry Scenario")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("First Attempt Failed"));
        
        EnqueueResponseDTO enqueueResponse = EnqueueResponseDTO.builder()
            .messageId(messageId)
            .status("ENQUEUED")
            .build();
        
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/test-domain/test-solution/queues/.*/messages"))
            .inScenario("Retry Scenario")
            .whenScenarioStateIs("First Attempt Failed")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(objectMapper.writeValueAsString(enqueueResponse))));
        
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(Map.of("data", "test"))
            .build();
        
        // Act & Assert
        mockMvc.perform(post("/test-domain/test-solution/responses")
                .header("relativeServicePath", "StudentPersonals")
                .header("X-Environment-Id", environmentId.toString())
                .header("X-Environment-Name", "test-env")
                .header("queueId", queueId.toString())
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(response)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.messageId").exists())
            .andExpect(jsonPath("$.status").value("DELIVERED"));
        
        // Verify retry occurred (2 requests to Queue Service)
        wireMockServer.verify(2, postRequestedFor(urlPathMatching("/test-domain/test-solution/queues/.*/messages")));
    }

    // ========== Validation Tests ==========

    @Test
    void shouldRejectResponseWithMissingQueueId() throws Exception {
        // Arrange
        String requestId = UUID.randomUUID().toString();
        UUID environmentId = UUID.randomUUID();
        
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(Map.of("data", "test"))
            .build();
        
        // Act & Assert
        mockMvc.perform(post("/test-domain/test-solution/responses")
                .header("relativeServicePath", "StudentPersonals")
                .header("X-Environment-Id", environmentId.toString())
                .header("X-Environment-Name", "test-env")
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(response)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectResponseWithMissingRequestId() throws Exception {
        // Arrange
        UUID queueId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        
        // Stubbed deliberately, as a negative control: if this stub is ever hit, the rejection
        // happened too late. A missing requestId must be refused before anything is enqueued.
        EnqueueResponseDTO enqueueResponse = EnqueueResponseDTO.builder()
            .messageId(messageId)
            .status("ENQUEUED")
            .build();

        wireMockServer.stubFor(WireMock.post(urlPathMatching("/test-domain/test-solution/queues/.*/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(objectMapper.writeValueAsString(enqueueResponse))));

        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(Map.of("data", "test"))
            .build();

        // Act & Assert — requestId is a mandatory @RequestHeader on public response ingress, so
        // Spring rejects with 400 before the controller body runs. This test previously asserted 202
        // and described requestId as optional, which contradicted its own name; the header was made
        // mandatory to match InternalResponseController, which always required it.
        mockMvc.perform(post("/test-domain/test-solution/responses")
                .header("relativeServicePath", "StudentPersonals")
                .header("X-Environment-Id", environmentId.toString())
                .header("X-Environment-Name", "test-env")
                .header("queueId", queueId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(response)))
            .andExpect(status().isBadRequest());

        // Nothing may have been enqueued — without a requestId the consumer could never correlate it.
        wireMockServer.verify(0,
            postRequestedFor(urlPathMatching("/test-domain/test-solution/queues/.*/messages")));
    }

    @Test
    void shouldRejectResponseWithMissingEnvironmentContext() throws Exception {
        // Arrange
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(Map.of("data", "test"))
            .build();
        
        // Act & Assert
        mockMvc.perform(post("/test-domain/test-solution/responses")
                .header("relativeServicePath", "StudentPersonals")
                .header("queueId", queueId.toString())
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(response)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectResponseWithInvalidPayload() throws Exception {
        // Arrange
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID environmentId = UUID.randomUUID();
        
        // Malformed JSON — not parseable
        String invalidContent = "this is not json {{{";
        
        // Act & Assert — malformed JSON results in a server error
        // (HttpMessageNotReadableException is not explicitly handled as 400)
        mockMvc.perform(post("/test-domain/test-solution/responses")
                .header("relativeServicePath", "StudentPersonals")
                .header("X-Environment-Id", environmentId.toString())
                .header("X-Environment-Name", "test-env")
                .header("queueId", queueId.toString())
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidContent))
            .andExpect(status().is5xxServerError());
    }

    @Test
    void shouldRejectInternalResponseWithMissingQueueId() throws Exception {
        // Arrange
        String requestId = UUID.randomUUID().toString();
        
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(Map.of("data", "test"))
            .build();
        
        // Act & Assert
        mockMvc.perform(post("/internal/responses")
                .header("X-Internal-Service-Token", "test-internal-token")
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(response)))
            .andExpect(status().isBadRequest());
    }

    // ========== Correlation and Message Construction Tests ==========

    @Test
    void shouldIncludeRequestIdInEnqueuedMessage() throws Exception {
        // Arrange
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID environmentId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        
        // Mock Queue Service response
        EnqueueResponseDTO enqueueResponse = EnqueueResponseDTO.builder()
            .messageId(messageId)
            .status("ENQUEUED")
            .build();
        
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/test-domain/test-solution/queues/.*/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(objectMapper.writeValueAsString(enqueueResponse))));
        
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(Map.of("data", "test"))
            .build();
        
        // Act
        mockMvc.perform(post("/test-domain/test-solution/responses")
                .header("relativeServicePath", "StudentPersonals")
                .header("X-Environment-Id", environmentId.toString())
                .header("X-Environment-Name", "test-env")
                .header("queueId", queueId.toString())
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(response)))
            .andExpect(status().isAccepted());
        
        // Verify requestId was included in the message envelope
        wireMockServer.verify(postRequestedFor(urlPathMatching("/test-domain/test-solution/queues/.*/messages"))
            .withRequestBody(containing(requestId.toString())));
    }

    @Test
    void shouldGenerateUniqueMessageIdForEachResponse() throws Exception {
        // Arrange
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID environmentId = UUID.randomUUID();
        UUID messageId1 = UUID.randomUUID();
        UUID messageId2 = UUID.randomUUID();
        
        // Mock Queue Service responses
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/test-domain/test-solution/queues/.*/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(objectMapper.writeValueAsString(
                    EnqueueResponseDTO.builder().messageId(messageId1).status("ENQUEUED").build()))));
        
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(Map.of("data", "test"))
            .build();
        
        // Act - Send two responses
        String result1 = mockMvc.perform(post("/test-domain/test-solution/responses")
                .header("relativeServicePath", "StudentPersonals")
                .header("X-Environment-Id", environmentId.toString())
                .header("X-Environment-Name", "test-env")
                .header("queueId", queueId.toString())
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(response)))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        
        String result2 = mockMvc.perform(post("/test-domain/test-solution/responses")
                .header("relativeServicePath", "StudentPersonals")
                .header("X-Environment-Id", environmentId.toString())
                .header("X-Environment-Name", "test-env")
                .header("queueId", queueId.toString())
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(response)))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        
        // Verify different messageIds were generated
        assertThat(result1).isNotEqualTo(result2);
    }

    @Test
    void shouldIncludeTimestampInEnqueuedMessage() throws Exception {
        // Arrange
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID environmentId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        
        // Mock Queue Service response
        EnqueueResponseDTO enqueueResponse = EnqueueResponseDTO.builder()
            .messageId(messageId)
            .status("ENQUEUED")
            .build();
        
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/test-domain/test-solution/queues/.*/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(objectMapper.writeValueAsString(enqueueResponse))));
        
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(Map.of("data", "test"))
            .build();
        
        // Act
        mockMvc.perform(post("/test-domain/test-solution/responses")
                .header("relativeServicePath", "StudentPersonals")
                .header("X-Environment-Id", environmentId.toString())
                .header("X-Environment-Name", "test-env")
                .header("queueId", queueId.toString())
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(response)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.timestamp").exists());
        
        // Verify timestamp was included in the message envelope
        wireMockServer.verify(postRequestedFor(urlPathMatching("/test-domain/test-solution/queues/.*/messages"))
            .withRequestBody(containing("timestamp")));
    }

    @Test
    void shouldIncludeEnvironmentContextInMessage() throws Exception {
        // Arrange
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID environmentId = UUID.randomUUID();
        String environmentName = "Production-Environment";
        UUID messageId = UUID.randomUUID();
        
        // Mock Queue Service response
        EnqueueResponseDTO enqueueResponse = EnqueueResponseDTO.builder()
            .messageId(messageId)
            .status("ENQUEUED")
            .build();
        
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/test-domain/test-solution/queues/.*/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(objectMapper.writeValueAsString(enqueueResponse))));
        
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(Map.of("data", "test"))
            .build();
        
        // Act
        mockMvc.perform(post("/test-domain/test-solution/responses")
                .header("relativeServicePath", "StudentPersonals")
                .header("X-Environment-Id", environmentId.toString())
                .header("X-Environment-Name", environmentName)
                .header("queueId", queueId.toString())
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(response)))
            .andExpect(status().isAccepted());
        
        // Verify environment context was included in the message envelope
        wireMockServer.verify(postRequestedFor(urlPathMatching("/test-domain/test-solution/queues/.*/messages"))
            .withRequestBody(containing(environmentId.toString())));
    }

    @Test
    void shouldConstructSifCompliantMessageEnvelope() throws Exception {
        // Arrange
        UUID queueId = UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();
        UUID environmentId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        
        // Mock Queue Service response
        EnqueueResponseDTO enqueueResponse = EnqueueResponseDTO.builder()
            .messageId(messageId)
            .status("ENQUEUED")
            .build();
        
        wireMockServer.stubFor(WireMock.post(urlPathMatching("/test-domain/test-solution/queues/.*/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(objectMapper.writeValueAsString(enqueueResponse))));
        
        ResponseDTO response = ResponseDTO.builder()
            .serviceName("StudentPersonal")
            .serviceType("OBJECT")
            .payload(Map.of("data", "test"))
            .build();
        
        // Act
        mockMvc.perform(post("/test-domain/test-solution/responses")
                .header("relativeServicePath", "StudentPersonals")
                .header("X-Environment-Id", environmentId.toString())
                .header("X-Environment-Name", "test-env")
                .header("queueId", queueId.toString())
                .header("requestId", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(response)))
            .andExpect(status().isAccepted());
        
        // Verify SIF message envelope structure
        wireMockServer.verify(postRequestedFor(urlPathMatching("/test-domain/test-solution/queues/.*/messages"))
            .withRequestBody(containing("messageId"))
            .withRequestBody(containing("timestamp"))
            .withRequestBody(containing("messageType"))
            .withRequestBody(containing("serviceName"))
            .withRequestBody(containing("serviceType"))
            .withRequestBody(containing("requestId"))
            .withRequestBody(containing("payload")));
    }

}
