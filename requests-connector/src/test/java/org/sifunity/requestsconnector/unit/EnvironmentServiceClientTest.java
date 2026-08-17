// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.unit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.requestsconnector.client.EnvironmentServiceClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.sifunity.broker.common.dto.ProtocolDTO;

/**
 * Unit tests for EnvironmentServiceClient.
 * Uses WireMock to mock Environment Service HTTP responses.
 * 
 * Tests Requirements:
 * - 2.1: Query provider registry with serviceName, zoneId, and contextId
 * - 2.2: Extract provider endpoint and supported request types from registry response
 * - 2.3: Handle no provider found scenario
 * - 2.4: Handle Environment Service unavailability
 */
class EnvironmentServiceClientTest {
    
    private WireMockServer wireMockServer;
    private EnvironmentServiceClient client;
    
    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        
        WebClient.Builder webClientBuilder = WebClient.builder();
        client = new EnvironmentServiceClient(webClientBuilder, wireMockServer.baseUrl());
    }
    
    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // ========== Successful Response Tests ==========
    
    @Test
    void lookupProvider_withAllParameters_shouldReturnProvider() {
        // Given: A provider registration exists in Environment Service
        UUID providerId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        String serviceName = "StudentPersonal";
        String zoneId = "production";
        String contextId = "context1";
        String endpoint = "http://provider.example.com/api/students";
        
        wireMockServer.stubFor(get(urlPathEqualTo("/internal/provider-lookup"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("zoneId", equalTo(zoneId))
            .withQueryParam("contextId", equalTo(contextId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {
                        "id": "%s",
                        "environmentId": "%s",
                        "serviceName": "%s",
                        "zoneId": "%s",
                        "contextId": "%s",
                        "endPoint": {"location": "%s"},
                        "serviceType": "OBJECT",
                        "providerName": null,
                        "supportedRequestTypes": {"supportedRequestType": [
                            {"requestType": "IMMEDIATE", "operations": {"operation": ["CREATE", "QUERY", "UPDATE", "DELETE"]}},
                            {"requestType": "DELAYED", "operations": {"operation": ["QUERY"]}}
                        ]},
                        "registeredAt": "2024-01-15T10:00:00Z"
                    }
                    """, providerId, environmentId, serviceName, zoneId, contextId, endpoint))));
        
        // When: Looking up the provider
        Optional<ProviderRegistrationDTO> result = client.lookupProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then: Provider is returned with correct data
        assertThat(result).isPresent();
        ProviderRegistrationDTO provider = result.get();
        assertThat(provider.id()).isEqualTo(providerId);
        assertThat(provider.environmentId()).isEqualTo(environmentId);
        assertThat(provider.serviceName()).isEqualTo(serviceName);
        assertThat(provider.zoneId()).isEqualTo(zoneId);
        assertThat(provider.contextId()).isEqualTo(contextId);
        assertThat(ProtocolDTO.locationOf(provider.endPoint())).isEqualTo(endpoint);
        assertThat(provider.serviceType()).isEqualTo(ServiceType.OBJECT);
        assertThat(provider.providerName()).isEqualTo(null);
        assertThat(provider.supportedRequestTypes().supportedRequestType()).hasSize(2);
        assertThat(provider.supportedRequestTypes().supportedRequestType())
            .anyMatch(srt -> srt.requestType() == RequestType.IMMEDIATE)
            .anyMatch(srt -> srt.requestType() == RequestType.DELAYED);
        
        // Verify correct request was made
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/internal/provider-lookup"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("zoneId", equalTo(zoneId))
            .withQueryParam("contextId", equalTo(contextId)));
    }
    
    @Test
    void lookupProvider_withNullContext_shouldNotIncludeContextParam() {
        // Given: A provider registration without context
        UUID providerId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        String serviceName = "StudentPersonal";
        String zoneId = "production";
        String endpoint = "http://provider.example.com/api/students";
        
        wireMockServer.stubFor(get(urlPathEqualTo("/internal/provider-lookup"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("zoneId", equalTo(zoneId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {
                        "id": "%s",
                        "environmentId": "%s",
                        "serviceName": "%s",
                        "zoneId": "%s",
                        "contextId": null,
                        "endPoint": {"location": "%s"},
                        "serviceType": "OBJECT",
                        "providerName": null,
                        "supportedRequestTypes": {"supportedRequestType": [
                            {"requestType": "IMMEDIATE", "operations": {"operation": ["QUERY"]}}
                        ]},
                        "registeredAt": "2024-01-15T10:00:00Z"
                    }
                    """, providerId, environmentId, serviceName, zoneId, endpoint))));
        
        // When: Looking up provider with null contextId
        Optional<ProviderRegistrationDTO> result = client.lookupProvider("d1", "s1", serviceName, zoneId, null);
        
        // Then: Provider is returned without contextId
        assertThat(result).isPresent();
        ProviderRegistrationDTO provider = result.get();
        assertThat(provider.serviceName()).isEqualTo(serviceName);
        assertThat(provider.zoneId()).isEqualTo(zoneId);
        assertThat(provider.contextId()).isNull();
        assertThat(ProtocolDTO.locationOf(provider.endPoint())).isEqualTo(endpoint);
        
        // Verify contextId query param is not included
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/internal/provider-lookup"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("zoneId", equalTo(zoneId))
            .withoutQueryParam("contextId"));
    }
    
    @Test
    void lookupProvider_withEmptyContext_shouldNotIncludeContextParam() {
        // Given: A provider registration with empty context string
        UUID providerId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        String serviceName = "StudentPersonal";
        String zoneId = "production";
        String endpoint = "http://provider.example.com/api/students";
        
        wireMockServer.stubFor(get(urlPathEqualTo("/internal/provider-lookup"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("zoneId", equalTo(zoneId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {
                        "id": "%s",
                        "environmentId": "%s",
                        "serviceName": "%s",
                        "zoneId": "%s",
                        "contextId": null,
                        "endPoint": {"location": "%s"},
                        "serviceType": "OBJECT",
                        "providerName": null,
                        "supportedRequestTypes": {"supportedRequestType": [
                            {"requestType": "IMMEDIATE", "operations": {"operation": ["QUERY"]}}
                        ]},
                        "registeredAt": "2024-01-15T10:00:00Z"
                    }
                    """, providerId, environmentId, serviceName, zoneId, endpoint))));
        
        // When: Looking up provider with empty contextId
        Optional<ProviderRegistrationDTO> result = client.lookupProvider("d1", "s1", serviceName, zoneId, "");
        
        // Then: Provider is returned
        assertThat(result).isPresent();
        
        // Verify contextId query param is not included
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/internal/provider-lookup"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("zoneId", equalTo(zoneId))
            .withoutQueryParam("contextId"));
    }
    
    @Test
    void lookupProvider_withMultipleSupportedRequestTypes_shouldParseCorrectly() {
        // Given: A provider supporting multiple request types with various operations
        UUID providerId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        String serviceName = "ComprehensiveService";
        String zoneId = "production";
        String contextId = "context1";
        String endpoint = "http://provider.example.com";
        
        wireMockServer.stubFor(get(urlPathEqualTo("/internal/provider-lookup"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("zoneId", equalTo(zoneId))
            .withQueryParam("contextId", equalTo(contextId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("""
                    {
                        "id": "%s",
                        "environmentId": "%s",
                        "serviceName": "%s",
                        "zoneId": "%s",
                        "contextId": "%s",
                        "endPoint": {"location": "%s"},
                        "serviceType": "FUNCTIONAL",
                        "providerName": "ComprehensiveSIS",
                        "supportedRequestTypes": {"supportedRequestType": [
                            {"requestType": "IMMEDIATE", "operations": {"operation": ["CREATE", "QUERY", "UPDATE", "DELETE"]}},
                            {"requestType": "DELAYED", "operations": {"operation": ["CREATE", "QUERY"]}}
                        ]},
                        "registeredAt": "2024-01-15T10:00:00Z"
                    }
                    """, providerId, environmentId, serviceName, zoneId, contextId, endpoint))));
        
        // When: Looking up the provider
        Optional<ProviderRegistrationDTO> result = client.lookupProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then: All supported request types are parsed correctly
        assertThat(result).isPresent();
        ProviderRegistrationDTO provider = result.get();
        assertThat(provider.serviceType()).isEqualTo(ServiceType.FUNCTIONAL);
        assertThat(provider.providerName()).isEqualTo("ComprehensiveSIS");
        assertThat(provider.supportedRequestTypes().supportedRequestType()).hasSize(2);
        
        SupportedRequestType immediateEntry = provider.supportedRequestTypes().supportedRequestType().stream()
            .filter(srt -> srt.requestType() == RequestType.IMMEDIATE)
            .findFirst().orElseThrow();
        assertThat(immediateEntry.operations().operation()).containsExactlyInAnyOrder(
            Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE);
        
        SupportedRequestType delayedEntry = provider.supportedRequestTypes().supportedRequestType().stream()
            .filter(srt -> srt.requestType() == RequestType.DELAYED)
            .findFirst().orElseThrow();
        assertThat(delayedEntry.operations().operation()).containsExactlyInAnyOrder(
            Operation.CREATE, Operation.QUERY);
    }
    
    @Test
    void lookupProvider_withEmptyResponse_shouldReturnEmpty() {
        // Given: Environment Service returns empty response
        String serviceName = "StudentPersonal";
        String zoneId = "production";
        String contextId = "context1";
        
        wireMockServer.stubFor(get(urlPathEqualTo("/internal/provider-lookup"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("zoneId", equalTo(zoneId))
            .withQueryParam("contextId", equalTo(contextId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("")));
        
        // When: Looking up the provider
        Optional<ProviderRegistrationDTO> result = client.lookupProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then: Empty optional is returned
        assertThat(result).isEmpty();
    }

    // ========== Error Response Tests ==========
    
    @Test
    void lookupProvider_whenNotFound_shouldReturnEmpty() {
        // Given: No provider registered for the service (Environment Service returns 404)
        String serviceName = "NonExistentService";
        String zoneId = "production";
        String contextId = "context1";
        
        wireMockServer.stubFor(get(urlPathEqualTo("/internal/provider-lookup"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("zoneId", equalTo(zoneId))
            .withQueryParam("contextId", equalTo(contextId))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Provider not found\"}")));
        
        // When: Looking up a non-existent provider
        Optional<ProviderRegistrationDTO> result = client.lookupProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then: Empty optional returned (404 is handled gracefully)
        assertThat(result).isEmpty();
    }
    
    @Test
    void lookupProvider_whenServiceUnavailable_shouldThrow503() {
        // Given: Environment Service is unavailable
        String serviceName = "StudentPersonal";
        String zoneId = "production";
        String contextId = "context1";
        
        wireMockServer.stubFor(get(urlPathEqualTo("/internal/provider-lookup"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("zoneId", equalTo(zoneId))
            .withQueryParam("contextId", equalTo(contextId))
            .willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Service temporarily unavailable\"}")));
        
        // When/Then: Should throw 503 exception
        assertThatThrownBy(() -> client.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
            .isInstanceOf(WebClientResponseException.ServiceUnavailable.class)
            .hasMessageContaining("503");
    }
    
    @Test
    void lookupProvider_whenInternalServerError_shouldThrow500() {
        // Given: Environment Service has internal error
        String serviceName = "StudentPersonal";
        String zoneId = "production";
        String contextId = "context1";
        
        wireMockServer.stubFor(get(urlPathEqualTo("/internal/provider-lookup"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("zoneId", equalTo(zoneId))
            .withQueryParam("contextId", equalTo(contextId))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Internal server error\"}")));
        
        // When/Then: Should throw 500 exception
        assertThatThrownBy(() -> client.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
            .isInstanceOf(WebClientResponseException.InternalServerError.class)
            .hasMessageContaining("500");
    }
}
