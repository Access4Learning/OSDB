// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.unit;

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
import org.sifunity.servicesconnector.client.EnvironmentServiceClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;
import java.util.Set;
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
 * - 7.1: Query provider registry with serviceName, zoneId, and contextId
 * - 7.3: Handle no provider found scenario
 * - 7.4: Handle Environment Service unavailability
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

    // ========== Provider Lookup Tests ==========
    
    @Test
    void lookupProvider_withAllParameters_shouldReturnProvider() {
        // Given
        UUID providerId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        String serviceName = "DataExport";
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
                        "serviceType": "OBJECT",
                        "providerName": "TestSIS",
                        "supportedRequestTypes": {"supportedRequestType": [
                            {"requestType": "IMMEDIATE", "operations": {"operation": ["CREATE", "QUERY", "UPDATE", "DELETE"]}},
                            {"requestType": "DELAYED", "operations": {"operation": ["QUERY"]}}
                        ]},
                        "registeredAt": "2024-01-15T10:00:00Z"
                    }
                    """, providerId, environmentId, serviceName, zoneId, contextId, endpoint))));
        
        // When
        Optional<ProviderRegistrationDTO> result = client.lookupProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then
        assertThat(result).isPresent();
        ProviderRegistrationDTO provider = result.get();
        assertThat(provider.id()).isEqualTo(providerId);
        assertThat(provider.environmentId()).isEqualTo(environmentId);
        assertThat(provider.serviceName()).isEqualTo(serviceName);
        assertThat(provider.zoneId()).isEqualTo(zoneId);
        assertThat(provider.contextId()).isEqualTo(contextId);
        assertThat(ProtocolDTO.locationOf(provider.endPoint())).isEqualTo(endpoint);
        assertThat(provider.serviceType()).isEqualTo(ServiceType.OBJECT);
        assertThat(provider.providerName()).isEqualTo("TestSIS");
        assertThat(provider.supportedRequestTypes().supportedRequestType()).hasSize(2);
        assertThat(provider.supportedRequestTypes().supportedRequestType())
            .anyMatch(srt -> srt.requestType() == RequestType.IMMEDIATE)
            .anyMatch(srt -> srt.requestType() == RequestType.DELAYED);
        
        // Verify request includes tenant isolation params
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/internal/provider-lookup"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("zoneId", equalTo(zoneId))
            .withQueryParam("contextId", equalTo(contextId))
            .withQueryParam("domainId", equalTo("d1"))
            .withQueryParam("solutionId", equalTo("s1")));
    }
    
    @Test
    void lookupProvider_withNullContext_shouldNotIncludeContextParam() {
        // Given
        UUID providerId = UUID.randomUUID();
        UUID environmentId = UUID.randomUUID();
        String serviceName = "DataExport";
        String zoneId = "production";
        String endpoint = "http://provider.example.com";
        
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
                        "providerName": "TestSIS",
                        "supportedRequestTypes": {"supportedRequestType": [
                            {"requestType": "IMMEDIATE", "operations": {"operation": ["QUERY"]}}
                        ]},
                        "registeredAt": "2024-01-15T10:00:00Z"
                    }
                    """, providerId, environmentId, serviceName, zoneId, endpoint))));
        
        // When
        Optional<ProviderRegistrationDTO> result = client.lookupProvider("d1", "s1", serviceName, zoneId, null);
        
        // Then
        assertThat(result).isPresent();
        ProviderRegistrationDTO provider = result.get();
        assertThat(provider.id()).isEqualTo(providerId);
        assertThat(provider.serviceName()).isEqualTo(serviceName);
        assertThat(provider.zoneId()).isEqualTo(zoneId);
        assertThat(provider.contextId()).isNull();
        assertThat(ProtocolDTO.locationOf(provider.endPoint())).isEqualTo(endpoint);
        
        // Verify contextId query param is not included, but tenant params always are
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/internal/provider-lookup"))
            .withQueryParam("serviceName", equalTo(serviceName))
            .withQueryParam("zoneId", equalTo(zoneId))
            .withQueryParam("domainId", equalTo("d1"))
            .withQueryParam("solutionId", equalTo("s1"))
            .withoutQueryParam("contextId"));
    }
    
    @Test
    void lookupProvider_whenNotFound_shouldReturnEmpty() {
        // Given
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
        
        // When
        Optional<ProviderRegistrationDTO> result = client.lookupProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then: 404 is treated as "no provider" → Optional.empty()
        assertThat(result).isEmpty();
    }
    
    @Test
    void lookupProvider_whenServiceUnavailable_shouldThrow503() {
        // Given
        String serviceName = "DataExport";
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
        
        // When/Then
        assertThatThrownBy(() -> client.lookupProvider("d1", "s1", serviceName, zoneId, contextId))
            .isInstanceOf(WebClientResponseException.ServiceUnavailable.class);
    }
    
    @Test
    void lookupProvider_withEmptyResponse_shouldReturnEmpty() {
        // Given
        String serviceName = "DataExport";
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
        
        // When
        Optional<ProviderRegistrationDTO> result = client.lookupProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    void lookupProvider_withMultipleSupportedRequestTypes_shouldParseCorrectly() {
        // Given
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
        
        // When
        Optional<ProviderRegistrationDTO> result = client.lookupProvider("d1", "s1", serviceName, zoneId, contextId);
        
        // Then
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

    // ========== resolveContextUuid Tests ==========

    @Test
    void resolveContextUuid_withNullContextId_defaultsToDefault() {
        // null contextId → defaults to DEFAULT → resolves via env-service
        UUID expectedUuid = UUID.randomUUID();
        wireMockServer.stubFor(get(urlPathEqualTo("/internal/contexts/resolve"))
            .withQueryParam("domainId", equalTo("domain1"))
            .withQueryParam("solutionId", equalTo("solution1"))
            .withQueryParam("zoneId", equalTo("zone1"))
            .withQueryParam("contextId", equalTo("DEFAULT"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("{\"contextUuid\":\"%s\"}", expectedUuid))));

        UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", null);
        assertThat(result).isEqualTo(expectedUuid);
    }

    @Test
    void resolveContextUuid_withBlankContextId_defaultsToDefault() {
        UUID expectedUuid = UUID.randomUUID();
        wireMockServer.stubFor(get(urlPathEqualTo("/internal/contexts/resolve"))
            .withQueryParam("domainId", equalTo("domain1"))
            .withQueryParam("solutionId", equalTo("solution1"))
            .withQueryParam("zoneId", equalTo("zone1"))
            .withQueryParam("contextId", equalTo("DEFAULT"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("{\"contextUuid\":\"%s\"}", expectedUuid))));

        UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", "   ");
        assertThat(result).isEqualTo(expectedUuid);
    }

    @Test
    void resolveContextUuid_withEmptyContextId_defaultsToDefault() {
        UUID expectedUuid = UUID.randomUUID();
        wireMockServer.stubFor(get(urlPathEqualTo("/internal/contexts/resolve"))
            .withQueryParam("domainId", equalTo("domain1"))
            .withQueryParam("solutionId", equalTo("solution1"))
            .withQueryParam("zoneId", equalTo("zone1"))
            .withQueryParam("contextId", equalTo("DEFAULT"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("{\"contextUuid\":\"%s\"}", expectedUuid))));

        UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", "");
        assertThat(result).isEqualTo(expectedUuid);
    }

    @Test
    void resolveContextUuid_successfulResolution_shouldReturnUuid() {
        // Given
        String domainId = "domain1";
        String solutionId = "solution1";
        String zoneId = "zone1";
        String contextId = "myContext";
        UUID expectedUuid = UUID.randomUUID();

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/contexts/resolve"))
            .withQueryParam("domainId", equalTo(domainId))
            .withQueryParam("solutionId", equalTo(solutionId))
            .withQueryParam("zoneId", equalTo(zoneId))
            .withQueryParam("contextId", equalTo(contextId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(String.format("{\"contextUuid\":\"%s\"}", expectedUuid))));

        // When
        UUID result = client.resolveContextUuid(domainId, solutionId, zoneId, contextId);

        // Then
        assertThat(result).isEqualTo(expectedUuid);
    }

    @Test
    void resolveContextUuid_notFound_shouldReturnNull() {
        // Given — environment-service returns 404 when context cannot be resolved
        String domainId = "domain1";
        String solutionId = "solution1";
        String zoneId = "zone1";
        String contextId = "nonExistent";

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/contexts/resolve"))
            .withQueryParam("domainId", equalTo(domainId))
            .withQueryParam("solutionId", equalTo(solutionId))
            .withQueryParam("zoneId", equalTo(zoneId))
            .withQueryParam("contextId", equalTo(contextId))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Context not found\"}")));

        // When
        UUID result = client.resolveContextUuid(domainId, solutionId, zoneId, contextId);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void resolveContextUuid_responseWithoutContextUuidField_shouldReturnNull() {
        // Given — response is 200 but missing the expected "contextUuid" field
        String domainId = "domain1";
        String solutionId = "solution1";
        String zoneId = "zone1";
        String contextId = "myContext";

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/contexts/resolve"))
            .withQueryParam("domainId", equalTo(domainId))
            .withQueryParam("solutionId", equalTo(solutionId))
            .withQueryParam("zoneId", equalTo(zoneId))
            .withQueryParam("contextId", equalTo(contextId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"unexpected\":\"shape\"}")));

        // When
        UUID result = client.resolveContextUuid(domainId, solutionId, zoneId, contextId);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void resolveContextUuid_serverError_shouldReturnNull() {
        // Given
        String domainId = "domain1";
        String solutionId = "solution1";
        String zoneId = "zone1";
        String contextId = "myContext";

        wireMockServer.stubFor(get(urlPathEqualTo("/internal/contexts/resolve"))
            .withQueryParam("domainId", equalTo(domainId))
            .withQueryParam("solutionId", equalTo(solutionId))
            .withQueryParam("zoneId", equalTo(zoneId))
            .withQueryParam("contextId", equalTo(contextId))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"Internal error\"}")));

        // When
        UUID result = client.resolveContextUuid(domainId, solutionId, zoneId, contextId);

        // Then
        assertThat(result).isNull();
    }
}
