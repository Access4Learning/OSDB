// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.unit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.config.HeaderBlacklistProperties;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.common.util.HeaderSanitizer;
import org.sifunity.broker.common.util.NoOpTracingUtils;
import org.sifunity.requestsconnector.client.ProviderHttpClient;
import org.sifunity.requestsconnector.dto.ImmediateRequestDTO;
import org.sifunity.requestsconnector.dto.RequestResponseDTO;
import org.sifunity.requestsconnector.service.ImmediateRequestHandler;
import org.sifunity.requestsconnector.util.MessageBuilder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * Unit tests for ImmediateRequestHandler.
 * Uses WireMock to mock provider HTTP responses.
 * 
 * Tests Requirements:
 * - 1.2: Forward request to provider's endpoint using HTTP
 * - 1.3: Return provider's response to consumer in same HTTP connection
 * - 1.4: Return HTTP 503 Service Unavailable when provider endpoint is unavailable
 * - 1.6: Preserve all SIF headers from original request
 * - 1.7: Add routing metadata headers
 * - 10.1: Return HTTP 503 Service Unavailable with descriptive message when provider unreachable
 * - 10.2: Forward provider error response to consumer
 */
class ImmediateRequestHandlerTest {
    
    private WireMockServer wireMockServer;
    private ImmediateRequestHandler handler;
    private MessageBuilder messageBuilder;
    private ProviderHttpClient providerClient;
    
    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        
        messageBuilder = new MessageBuilder();
        WebClient.Builder webClientBuilder = WebClient.builder();
        providerClient = new ProviderHttpClient(webClientBuilder, 1000, 2000);
        HeaderBlacklistProperties blacklistProps = new HeaderBlacklistProperties();
        HeaderSanitizer headerSanitizer = new HeaderSanitizer(blacklistProps);
        handler = new ImmediateRequestHandler(providerClient, messageBuilder, headerSanitizer, new NoOpTracingUtils(), List.of());
    }
    
    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }
    
    // ========== Successful Request/Response Flow Tests ==========
    
    @Test
    void handleRequest_withValidRequest_shouldForwardAndReturnResponse() {
        // Given: A valid IMMEDIATE request and provider
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType(ServiceType.OBJECT.name());
        request.setMessageType(MessageType.REQUEST.name());
        request.setPayload(Map.of("id", "123"));
        
        ProviderRegistrationDTO provider = createProviderWithEndpoint(
            "StudentPersonal", "default", "default",
            wireMockServer.baseUrl() + "/api/students");
        
        // Mock provider response
        UUID responseMessageId = UUID.randomUUID();
        wireMockServer.stubFor(post(urlPathEqualTo("/api/students"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {
                        "messageId": "%s",
                        "timestamp": "2024-01-15T10:00:00Z",
                        "messageType": "RESPONSE",
                        "serviceName": "StudentPersonal",
                        "serviceType": "OBJECT",
                        "payload": {
                            "id": "123",
                            "name": "John Doe",
                            "grade": 10
                        }
                    }
                    """, responseMessageId))));
        
        // When: Handling the request
        RequestResponseDTO response = handler.handleRequest(
            environmentId, environmentName, "test-domain", "test-solution", provider, request, Collections.emptyMap());
        
        // Then: Response is returned with provider's data
        assertThat(response).isNotNull();
        assertThat(response.getMessageId()).isEqualTo(responseMessageId);
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getPayload()).isNotNull();
        assertThat(response.getQueueId()).isNull(); // IMMEDIATE requests don't have queueId
        assertThat(response.getStatus()).isNull(); // IMMEDIATE requests don't have status
        
        // Verify provider was called
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/students")));
    }
    
    @Test
    void handleRequest_shouldPreserveSifHeaders() {
        // Given: A valid IMMEDIATE request
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType(ServiceType.OBJECT.name());
        request.setMessageType(MessageType.REQUEST.name());
        request.setPayload(Map.of("id", "123"));
        
        ProviderRegistrationDTO provider = createProviderWithEndpoint(
            "StudentPersonal", "default", "default",
            wireMockServer.baseUrl() + "/api/students");
        
        // Mock provider response
        wireMockServer.stubFor(post(urlPathEqualTo("/api/students"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "messageId": "00000000-0000-0000-0000-000000000000",
                        "timestamp": "2024-01-15T10:00:00Z",
                        "messageType": "RESPONSE",
                        "serviceName": "StudentPersonal",
                        "serviceType": "OBJECT",
                        "payload": {}
                    }
                    """)));
        
        // When: Handling the request
        handler.handleRequest(environmentId, environmentName, "test-domain", "test-solution", provider, request, Collections.emptyMap());
        
        // Then: Request to provider should contain all SIF headers
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/students"))
            .withRequestBody(matchingJsonPath("$.messageId"))
            .withRequestBody(matchingJsonPath("$.timestamp"))
            .withRequestBody(matchingJsonPath("$.messageType", equalTo("REQUEST")))
            .withRequestBody(matchingJsonPath("$.serviceName", equalTo("StudentPersonal")))
            .withRequestBody(matchingJsonPath("$.serviceType", equalTo("OBJECT")))
            .withRequestBody(matchingJsonPath("$.sourceId", equalTo(environmentId.toString()))));
    }
    
    @Test
    void handleRequest_shouldPreservePayload() {
        // Given: A request with a complex payload
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType(ServiceType.OBJECT.name());
        request.setMessageType(MessageType.REQUEST.name());
        request.setPayload(Map.of("id", "123", "name", "John Doe"));
        
        ProviderRegistrationDTO provider = createProviderWithEndpoint(
            "StudentPersonal", "default", "default",
            wireMockServer.baseUrl() + "/api/students");
        
        // Mock provider response
        wireMockServer.stubFor(post(urlPathEqualTo("/api/students"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "messageId": "00000000-0000-0000-0000-000000000000",
                        "timestamp": "2024-01-15T10:00:00Z",
                        "messageType": "RESPONSE",
                        "serviceName": "StudentPersonal",
                        "serviceType": "OBJECT",
                        "payload": {}
                    }
                    """)));
        
        // When: Handling the request
        handler.handleRequest(environmentId, environmentName, "test-domain", "test-solution", provider, request, Collections.emptyMap());
        
        // Then: Payload should be preserved in the forwarded request
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/students"))
            .withRequestBody(matchingJsonPath("$.payload.id", equalTo("123")))
            .withRequestBody(matchingJsonPath("$.payload.name", equalTo("John Doe"))));
    }
    
    // ========== Provider Unavailable Scenario Tests ==========
    
    @Test
    void handleRequest_withUnreachableProvider_shouldThrowException() {
        // Given: A provider with an unreachable endpoint
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType(ServiceType.OBJECT.name());
        request.setMessageType(MessageType.REQUEST.name());
        request.setPayload(Map.of("id", "123"));
        
        ProviderRegistrationDTO provider = createProviderWithEndpoint(
            "StudentPersonal", "default", "default",
            "http://localhost:99999/unreachable");
        
        // When/Then: Handling the request should throw an exception
        assertThatThrownBy(() -> 
            handler.handleRequest(environmentId, environmentName, "test-domain", "test-solution", provider, request, Collections.emptyMap()))
            .as("Should throw exception when provider is unreachable")
            .isInstanceOf(Exception.class);
    }
    
    // ========== Provider Timeout Scenario Tests ==========
    
    @Test
    void handleRequest_withSlowProvider_shouldTimeout() {
        // Given: A provider that responds slowly
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType(ServiceType.OBJECT.name());
        request.setMessageType(MessageType.REQUEST.name());
        request.setPayload(Map.of("id", "123"));
        
        ProviderRegistrationDTO provider = createProviderWithEndpoint(
            "StudentPersonal", "default", "default",
            wireMockServer.baseUrl() + "/api/students");
        
        // Mock provider with slow response (3 seconds, exceeds 2 second read timeout)
        wireMockServer.stubFor(post(urlPathEqualTo("/api/students"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")
                .withFixedDelay(3000)));
        
        // When/Then: Handling the request should timeout
        assertThatThrownBy(() -> 
            handler.handleRequest(environmentId, environmentName, "test-domain", "test-solution", provider, request, Collections.emptyMap()))
            .as("Should timeout when provider is slow")
            .isInstanceOf(Exception.class);
    }
    
    // ========== Provider Error Response Forwarding Tests ==========
    
    @Test
    void handleRequest_withProviderError_shouldForwardErrorResponse() {
        // Given: A provider that returns an error response
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType(ServiceType.OBJECT.name());
        request.setMessageType(MessageType.REQUEST.name());
        request.setPayload(Map.of("id", "999"));
        
        ProviderRegistrationDTO provider = createProviderWithEndpoint(
            "StudentPersonal", "default", "default",
            wireMockServer.baseUrl() + "/api/students");
        
        // Mock provider error response (404 Not Found)
        wireMockServer.stubFor(post(urlPathEqualTo("/api/students"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "code": "NOT_FOUND",
                        "message": "Student not found",
                        "timestamp": "2024-01-15T10:00:00Z"
                    }
                    """)));
        
        // When/Then: Handling the request should throw WebClientResponseException
        assertThatThrownBy(() -> 
            handler.handleRequest(environmentId, environmentName, "test-domain", "test-solution", provider, request, Collections.emptyMap()))
            .as("Should throw exception for provider error response")
            .isInstanceOf(WebClientResponseException.class)
            .satisfies(ex -> {
                WebClientResponseException webEx = (WebClientResponseException) ex;
                assertThat(webEx.getStatusCode().value()).isEqualTo(404);
            });
    }
    
    @Test
    void handleRequest_withProvider500Error_shouldForwardErrorResponse() {
        // Given: A provider that returns a 500 Internal Server Error
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnvironment";
        
        ImmediateRequestDTO request = new ImmediateRequestDTO();
        request.setServiceName("StudentPersonal");
        request.setServiceType(ServiceType.OBJECT.name());
        request.setMessageType(MessageType.REQUEST.name());
        request.setPayload(Map.of("id", "123"));
        
        ProviderRegistrationDTO provider = createProviderWithEndpoint(
            "StudentPersonal", "default", "default",
            wireMockServer.baseUrl() + "/api/students");
        
        // Mock provider error response (500 Internal Server Error)
        wireMockServer.stubFor(post(urlPathEqualTo("/api/students"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "code": "INTERNAL_ERROR",
                        "message": "Database connection failed",
                        "timestamp": "2024-01-15T10:00:00Z"
                    }
                    """)));
        
        // When/Then: Handling the request should throw WebClientResponseException
        assertThatThrownBy(() -> 
            handler.handleRequest(environmentId, environmentName, "test-domain", "test-solution", provider, request, Collections.emptyMap()))
            .as("Should throw exception for provider 500 error")
            .isInstanceOf(WebClientResponseException.class)
            .satisfies(ex -> {
                WebClientResponseException webEx = (WebClientResponseException) ex;
                assertThat(webEx.getStatusCode().value()).isEqualTo(500);
            });
    }
    
    // ========== Helper Methods ==========
    
    private ProviderRegistrationDTO createProviderWithEndpoint(
            String serviceName, String zoneId, String contextId, String endpoint) {
        return new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                serviceName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of(endpoint),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))))),
                Instant.now()
        );
    }
}
