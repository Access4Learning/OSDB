// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.unit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.common.metrics.ConnectorMetricsService;
import org.sifunity.broker.common.util.HeaderSanitizer;
import org.sifunity.broker.common.util.NoOpTracingUtils;
import org.sifunity.requestsconnector.cache.ProviderRegistryCache;
import org.sifunity.requestsconnector.controller.RequestController;
import org.sifunity.requestsconnector.dto.RequestResponseDTO;
import org.sifunity.requestsconnector.exception.NoProviderException;
import org.sifunity.requestsconnector.exception.UnsupportedPatternException;
import org.sifunity.requestsconnector.service.RoutingService;
import org.sifunity.requestsconnector.util.HeaderExtractor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * Unit tests for RequestController.
 *
 * <p>Tests the controller methods directly (not via MockMvc) with mocked dependencies.
 * Uses WireMock to stub the provider endpoint for IMMEDIATE proxy tests.</p>
 */
@ExtendWith(MockitoExtension.class)
class RequestControllerTest {

    @Mock
    private RoutingService routingService;

    @Mock
    private HeaderExtractor headerExtractor;

    @Mock
    private HeaderSanitizer headerSanitizer;

    @Mock
    private ProviderRegistryCache providerCache;

    @Mock
    private ConnectorMetricsService connectorMetricsService;

    @Mock
    private HttpServletRequest httpServletRequest;

    private WireMockServer wireMockServer;
    private RequestController controller;

    private final String domainId = "test-domain";
    private final String solutionId = "test-solution";
    private final String objectName = "StudentPersonal";
    private final String objectId = "abc-123";
    private final UUID environmentId = UUID.randomUUID();
    private final String environmentName = "TestEnv";
    private final String zoneId = "production";
    private final String contextId = "default";
    private final UUID queueId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        WebClient.Builder webClientBuilder = WebClient.builder()
            .baseUrl(wireMockServer.baseUrl());

        controller = new RequestController(
            routingService,
            headerExtractor,
            headerSanitizer,
            providerCache,
            webClientBuilder,
            connectorMetricsService,
            new NoOpTracingUtils(),
            java.util.List.of()
        );
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // ========== GET IMMEDIATE Tests ==========

    @Test
    @DisplayName("GET IMMEDIATE - provider found and supports IMMEDIATE, returns proxied response")
    void handleGetRequest_immediate_proxiesToProvider() {
        // Given
        ProviderRegistrationDTO provider = createProvider(
            wireMockServer.baseUrl() + "/" + objectName,
            RequestType.IMMEDIATE);

        when(providerCache.getProvider(domainId, solutionId,objectName, zoneId, contextId, "OBJECT"))
            .thenReturn(Optional.of(provider));
        when(headerSanitizer.sanitize(httpServletRequest))
            .thenReturn(Map.of("Accept", "application/json"));

        wireMockServer.stubFor(get(urlPathEqualTo("/" + objectName + "/" + objectId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"abc-123\",\"name\":\"Jane\"}")));

        // When
        ResponseEntity<Object> response = controller.handleGetRequest(
            domainId, solutionId, objectName, objectId,
            environmentId, environmentName, zoneId, contextId,
            "IMMEDIATE", null, null, "OBJECT", null, httpServletRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/" + objectName + "/" + objectId)));
    }

    // ========== GET DELAYED Tests ==========

    @Test
    @DisplayName("GET DELAYED - valid queueId delegates to routingService, returns 202")
    void handleGetRequest_delayed_delegatesToRoutingService() {
        // Given
        RequestResponseDTO expectedResponse = RequestResponseDTO.builder()
            .messageId(UUID.randomUUID())
            .queueId(queueId)
            .status("ACCEPTED")
            .timestamp(Instant.now())
            .build();

        when(headerSanitizer.sanitize(httpServletRequest))
            .thenReturn(Collections.emptyMap());
        when(httpServletRequest.getQueryString()).thenReturn(null);
        when(routingService.routeDelayedRequest(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(expectedResponse);

        // When
        ResponseEntity<Object> response = controller.handleGetRequest(
            domainId, solutionId, objectName, objectId,
            environmentId, environmentName, zoneId, contextId,
            "DELAYED", queueId, null, "OBJECT", null, httpServletRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        verify(routingService).routeDelayedRequest(
            eq(environmentId), eq(environmentName), eq(domainId), eq(solutionId),
            eq(zoneId), eq(contextId), eq(queueId), any(), any(), eq("GET"), any(), any());
    }

    @Test
    @DisplayName("GET DELAYED missing queueId - throws IllegalArgumentException")
    void handleGetRequest_delayed_missingQueueId_throws() {
        // When/Then
        assertThatThrownBy(() -> controller.handleGetRequest(
            domainId, solutionId, objectName, objectId,
            environmentId, environmentName, zoneId, contextId,
            "DELAYED", null, null, "OBJECT", null, httpServletRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("queueId header is required for DELAYED requests");
    }

    // ========== POST IMMEDIATE Tests ==========

    @Test
    @DisplayName("POST IMMEDIATE - proxies to provider")
    void handlePostRequest_immediate_proxiesToProvider() {
        // Given
        ProviderRegistrationDTO provider = createProvider(
            wireMockServer.baseUrl() + "/" + objectName,
            RequestType.IMMEDIATE);

        when(providerCache.getProvider(domainId, solutionId,objectName, zoneId, contextId, "OBJECT"))
            .thenReturn(Optional.of(provider));
        when(headerSanitizer.sanitize(httpServletRequest))
            .thenReturn(Map.of("Accept", "application/json"));

        wireMockServer.stubFor(post(urlPathEqualTo("/" + objectName))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"new-id\",\"status\":\"created\"}")));

        Object requestBody = Map.of("name", "New Student");

        // When
        ResponseEntity<?> response = controller.handleRequest(
            domainId, solutionId, objectName,
            environmentId, environmentName, zoneId, contextId,
            "IMMEDIATE", null, null, "OBJECT", null, requestBody, httpServletRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/" + objectName)));
    }

    // ========== POST DELAYED Tests ==========

    @Test
    @DisplayName("POST DELAYED - delegates to routingService, returns 202")
    void handlePostRequest_delayed_delegatesToRoutingService() {
        // Given
        RequestResponseDTO expectedResponse = RequestResponseDTO.builder()
            .messageId(UUID.randomUUID())
            .queueId(queueId)
            .status("ACCEPTED")
            .timestamp(Instant.now())
            .build();

        when(headerSanitizer.sanitize(httpServletRequest))
            .thenReturn(Collections.emptyMap());
        when(httpServletRequest.getQueryString()).thenReturn(null);
        when(routingService.routeDelayedRequest(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(expectedResponse);

        Object requestBody = Map.of("name", "New Student");

        // When
        ResponseEntity<?> response = controller.handleRequest(
            domainId, solutionId, objectName,
            environmentId, environmentName, zoneId, contextId,
            "DELAYED", queueId, null, "OBJECT", null, requestBody, httpServletRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        verify(routingService).routeDelayedRequest(
            eq(environmentId), eq(environmentName), eq(domainId), eq(solutionId),
            eq(zoneId), eq(contextId), eq(queueId), any(), any(), eq("POST"), any(), any());
    }

    // ========== POST Invalid requestType Tests ==========

    @Test
    @DisplayName("POST invalid requestType - throws IllegalArgumentException")
    void handlePostRequest_invalidRequestType_throws() {
        // When/Then
        assertThatThrownBy(() -> controller.handleRequest(
            domainId, solutionId, objectName,
            environmentId, environmentName, zoneId, contextId,
            "INVALID", null, null, "OBJECT", null, Map.of("data", "value"), httpServletRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid requestType")
            .hasMessageContaining("INVALID");
    }

    // ========== PUT IMMEDIATE Tests ==========

    @Test
    @DisplayName("PUT IMMEDIATE - proxies to provider")
    void handlePutRequest_immediate_proxiesToProvider() {
        // Given
        ProviderRegistrationDTO provider = createProvider(
            wireMockServer.baseUrl() + "/" + objectName,
            RequestType.IMMEDIATE);

        when(providerCache.getProvider(domainId, solutionId,objectName, zoneId, contextId, "OBJECT"))
            .thenReturn(Optional.of(provider));
        when(headerSanitizer.sanitize(httpServletRequest))
            .thenReturn(Map.of("Accept", "application/json"));

        wireMockServer.stubFor(put(urlPathEqualTo("/" + objectName + "/" + objectId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"abc-123\",\"name\":\"Updated\"}")));

        Object body = Map.of("name", "Updated");

        // When
        ResponseEntity<Object> response = controller.handlePutRequest(
            domainId, solutionId, objectName, objectId,
            environmentId, environmentName, zoneId, contextId,
            "IMMEDIATE", null, null, "OBJECT", null, body, httpServletRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        wireMockServer.verify(putRequestedFor(urlPathEqualTo("/" + objectName + "/" + objectId)));
    }

    // ========== PUT DELAYED Tests ==========

    @Test
    @DisplayName("PUT DELAYED - delegates to routingService, returns 202")
    void handlePutRequest_delayed_delegatesToRoutingService() {
        // Given
        RequestResponseDTO expectedResponse = RequestResponseDTO.builder()
            .messageId(UUID.randomUUID())
            .queueId(queueId)
            .status("ACCEPTED")
            .timestamp(Instant.now())
            .build();

        when(headerSanitizer.sanitize(httpServletRequest))
            .thenReturn(Collections.emptyMap());
        when(httpServletRequest.getQueryString()).thenReturn(null);
        when(routingService.routeDelayedRequest(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(expectedResponse);

        Object body = Map.of("name", "Updated");

        // When
        ResponseEntity<Object> response = controller.handlePutRequest(
            domainId, solutionId, objectName, objectId,
            environmentId, environmentName, zoneId, contextId,
            "DELAYED", queueId, null, "OBJECT", null, body, httpServletRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        verify(routingService).routeDelayedRequest(
            eq(environmentId), eq(environmentName), eq(domainId), eq(solutionId),
            eq(zoneId), eq(contextId), eq(queueId), any(), any(), eq("PUT"), any(), any());
    }

    // ========== DELETE IMMEDIATE Tests ==========

    @Test
    @DisplayName("DELETE IMMEDIATE - proxies to provider")
    void handleDeleteRequest_immediate_proxiesToProvider() {
        // Given
        ProviderRegistrationDTO provider = createProvider(
            wireMockServer.baseUrl() + "/" + objectName,
            RequestType.IMMEDIATE);

        when(providerCache.getProvider(domainId, solutionId,objectName, zoneId, contextId, "OBJECT"))
            .thenReturn(Optional.of(provider));
        when(headerSanitizer.sanitize(httpServletRequest))
            .thenReturn(Map.of("Accept", "application/json"));

        wireMockServer.stubFor(delete(urlPathEqualTo("/" + objectName + "/" + objectId))
            .willReturn(aResponse()
                .withStatus(204)));

        // When
        ResponseEntity<Object> response = controller.handleDeleteRequest(
            domainId, solutionId, objectName, objectId,
            environmentId, environmentName, zoneId, contextId,
            "IMMEDIATE", null, null, "OBJECT", null, httpServletRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        wireMockServer.verify(deleteRequestedFor(urlPathEqualTo("/" + objectName + "/" + objectId)));
    }

    // ========== DELETE DELAYED Tests ==========

    @Test
    @DisplayName("DELETE DELAYED - delegates to routingService, returns 202")
    void handleDeleteRequest_delayed_delegatesToRoutingService() {
        // Given
        RequestResponseDTO expectedResponse = RequestResponseDTO.builder()
            .messageId(UUID.randomUUID())
            .queueId(queueId)
            .status("ACCEPTED")
            .timestamp(Instant.now())
            .build();

        when(headerSanitizer.sanitize(httpServletRequest))
            .thenReturn(Collections.emptyMap());
        when(httpServletRequest.getQueryString()).thenReturn(null);
        when(routingService.routeDelayedRequest(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(expectedResponse);

        // When
        ResponseEntity<Object> response = controller.handleDeleteRequest(
            domainId, solutionId, objectName, objectId,
            environmentId, environmentName, zoneId, contextId,
            "DELAYED", queueId, null, "OBJECT", null, httpServletRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        verify(routingService).routeDelayedRequest(
            eq(environmentId), eq(environmentName), eq(domainId), eq(solutionId),
            eq(zoneId), eq(contextId), eq(queueId), any(), any(), eq("DELETE"), any(), any());
    }

    // ========== No Provider Found Tests ==========

    @Test
    @DisplayName("No provider found - throws NoProviderException")
    void handleGetRequest_immediate_noProvider_throws() {
        // Given
        when(providerCache.getProvider(domainId, solutionId,objectName, zoneId, contextId, "OBJECT"))
            .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> controller.handleGetRequest(
            domainId, solutionId, objectName, objectId,
            environmentId, environmentName, zoneId, contextId,
            "IMMEDIATE", null, null, "OBJECT", null, httpServletRequest))
            .isInstanceOf(NoProviderException.class)
            .hasMessageContaining(objectName)
            .hasMessageContaining(zoneId);
    }

    // ========== Provider Doesn't Support IMMEDIATE Tests ==========

    @Test
    @DisplayName("Provider doesn't support IMMEDIATE - throws UnsupportedPatternException")
    void handleGetRequest_immediate_unsupportedPattern_throws() {
        // Given: provider only supports DELAYED
        ProviderRegistrationDTO provider = createProvider(
            wireMockServer.baseUrl() + "/" + objectName,
            RequestType.DELAYED);

        when(providerCache.getProvider(domainId, solutionId,objectName, zoneId, contextId, "OBJECT"))
            .thenReturn(Optional.of(provider));

        // When/Then
        assertThatThrownBy(() -> controller.handleGetRequest(
            domainId, solutionId, objectName, objectId,
            environmentId, environmentName, zoneId, contextId,
            "IMMEDIATE", null, null, "OBJECT", null, httpServletRequest))
            .isInstanceOf(UnsupportedPatternException.class)
            .hasMessageContaining("Provider does not support IMMEDIATE pattern");
    }

    // ========== serviceType Header Extraction Tests ==========

    @Test
    @DisplayName("GET IMMEDIATE with serviceType=XQUERYTEMPLATE - provider lookup uses XQUERYTEMPLATE")
    void handleGetRequest_immediate_xqueryTemplateServiceType_usesCorrectServiceType() {
        // Given
        String queryToken = "StudentSnapshot";
        ProviderRegistrationDTO provider = createProvider(
            wireMockServer.baseUrl() + "/" + queryToken,
            RequestType.IMMEDIATE);

        when(providerCache.getProvider(domainId, solutionId,queryToken, zoneId, contextId, "XQUERYTEMPLATE"))
            .thenReturn(Optional.of(provider));
        when(headerSanitizer.sanitize(httpServletRequest))
            .thenReturn(Map.of("Accept", "application/json"));

        wireMockServer.stubFor(get(urlPathEqualTo("/" + queryToken))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"results\":[]}")));

        // When
        ResponseEntity<Object> response = controller.handleGetRequest(
            domainId, solutionId, queryToken, null,
            environmentId, environmentName, zoneId, contextId,
            "IMMEDIATE", null, null, "XQUERYTEMPLATE", null, httpServletRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(providerCache).getProvider(domainId, solutionId,queryToken, zoneId, contextId, "XQUERYTEMPLATE");
        verify(providerCache, never()).getProvider(domainId, solutionId,queryToken, zoneId, contextId, "OBJECT");
    }

    @Test
    @DisplayName("GET IMMEDIATE with missing serviceType - defaults to OBJECT (backward compatible)")
    void handleGetRequest_immediate_missingServiceType_defaultsToObject() {
        // Given: serviceType defaults to "OBJECT" via @RequestHeader defaultValue
        ProviderRegistrationDTO provider = createProvider(
            wireMockServer.baseUrl() + "/" + objectName,
            RequestType.IMMEDIATE);

        when(providerCache.getProvider(domainId, solutionId,objectName, zoneId, contextId, "OBJECT"))
            .thenReturn(Optional.of(provider));
        when(headerSanitizer.sanitize(httpServletRequest))
            .thenReturn(Map.of("Accept", "application/json"));

        wireMockServer.stubFor(get(urlPathEqualTo("/" + objectName + "/" + objectId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"abc-123\"}")));

        // When: pass "OBJECT" which is what Spring would inject as the default
        ResponseEntity<Object> response = controller.handleGetRequest(
            domainId, solutionId, objectName, objectId,
            environmentId, environmentName, zoneId, contextId,
            "IMMEDIATE", null, null, "OBJECT", null, httpServletRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(providerCache).getProvider(domainId, solutionId,objectName, zoneId, contextId, "OBJECT");
    }

    @Test
    @DisplayName("GET IMMEDIATE with explicit serviceType=OBJECT - same as default")
    void handleGetRequest_immediate_explicitObjectServiceType_sameAsDefault() {
        // Given
        ProviderRegistrationDTO provider = createProvider(
            wireMockServer.baseUrl() + "/" + objectName,
            RequestType.IMMEDIATE);

        when(providerCache.getProvider(domainId, solutionId,objectName, zoneId, contextId, "OBJECT"))
            .thenReturn(Optional.of(provider));
        when(headerSanitizer.sanitize(httpServletRequest))
            .thenReturn(Map.of("Accept", "application/json"));

        wireMockServer.stubFor(get(urlPathEqualTo("/" + objectName + "/" + objectId))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"abc-123\",\"name\":\"Jane\"}")));

        // When: explicitly pass "OBJECT"
        ResponseEntity<Object> response = controller.handleGetRequest(
            domainId, solutionId, objectName, objectId,
            environmentId, environmentName, zoneId, contextId,
            "IMMEDIATE", null, null, "OBJECT", null, httpServletRequest);

        // Then: same behavior as when serviceType is omitted (defaults to OBJECT)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(providerCache).getProvider(domainId, solutionId,objectName, zoneId, contextId, "OBJECT");
        verify(providerCache, never()).getProvider(domainId, solutionId,objectName, zoneId, contextId, "XQUERYTEMPLATE");
    }

    // ========== Helper Methods ==========

    private ProviderRegistrationDTO createProvider(String endpoint, RequestType requestType) {
        return new ProviderRegistrationDTO(
                UUID.randomUUID(),
                environmentId,
                objectName,
                zoneId,
                contextId,
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of(endpoint),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(requestType, OperationsDTO.of(Set.of(
                Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))))),
                Instant.now()
        );
    }
}
