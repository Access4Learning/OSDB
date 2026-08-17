// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * Integration test for the IMMEDIATE request flow header passthrough.
 *
 * <p>Uses real HeaderSanitizer, HeaderBlacklistProperties (default config),
 * MessageBuilder, ProviderHttpClient, and ImmediateRequestHandler wired together.
 * WireMock simulates the provider endpoint so we can capture outbound headers
 * and return custom response headers.</p>
 *
 * <p>Validates: Requirements 7.1, 7.2, 7.3, 9.2</p>
 */
class ImmediateRequestHeaderPassthroughIntegrationTest {

    private WireMockServer wireMockServer;
    private ImmediateRequestHandler handler;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        // Real components — no mocks
        HeaderBlacklistProperties blacklistProps = new HeaderBlacklistProperties();
        HeaderSanitizer headerSanitizer = new HeaderSanitizer(blacklistProps);
        MessageBuilder messageBuilder = new MessageBuilder();
        ProviderHttpClient providerClient =
                new ProviderHttpClient(WebClient.builder(), 2000, 5000);

        handler = new ImmediateRequestHandler(providerClient, messageBuilder, headerSanitizer, new NoOpTracingUtils(), java.util.List.of());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // ========== Tests ==========

    @Test
    void shouldForwardPassthroughHeadersToProviderAndExcludeBlacklisted() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnv";

        ImmediateRequestDTO request = createRequest("StudentPersonal");
        ProviderRegistrationDTO provider = createProvider(
                wireMockServer.baseUrl() + "/api/students");

        UUID responseMessageId = UUID.randomUUID();
        stubProviderSuccess("/api/students", responseMessageId);

        // Custom passthrough headers + blacklisted headers
        Map<String, String> passthroughHeaders = new LinkedHashMap<>();
        passthroughHeaders.put("x-vendor-name", "TestVendor");
        passthroughHeaders.put("debugToken", "abc123");
        // Blacklisted headers should NOT appear even if passed in
        passthroughHeaders.put("Authorization", "Bearer secret-token");
        passthroughHeaders.put("fingerprint", "spoofed-value");

        // Act — sanitize first (as the controller would), then call handler
        HeaderBlacklistProperties props = new HeaderBlacklistProperties();
        HeaderSanitizer sanitizer = new HeaderSanitizer(props);
        Map<String, String> sanitized = sanitizer.sanitize(passthroughHeaders);

        RequestResponseDTO response = handler.handleRequest(
                environmentId, environmentName, "test-domain", "test-solution", provider, request, sanitized);

        // Assert — capture the outbound request WireMock received
        var requests = wireMockServer.findAll(postRequestedFor(urlPathEqualTo("/api/students")));
        assertThat(requests).hasSize(1);
        LoggedRequest captured = requests.get(0);

        // Build case-insensitive set of outbound header names
        Set<String> outboundKeys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        captured.getHeaders().all().forEach(h -> outboundKeys.add(h.key()));

        // Custom passthrough headers SHOULD be present on the outbound request
        assertThat(outboundKeys).contains("x-vendor-name");
        assertThat(captured.getHeader("x-vendor-name")).isEqualTo("TestVendor");
        assertThat(outboundKeys).contains("debugToken");
        assertThat(captured.getHeader("debugToken")).isEqualTo("abc123");

        // Blacklisted headers SHOULD NOT be present on the outbound request
        assertThat(outboundKeys).doesNotContain("Authorization");
        assertThat(outboundKeys).doesNotContain("fingerprint");

        // Response should be valid
        assertThat(response).isNotNull();
        assertThat(response.getMessageId()).isEqualTo(responseMessageId);
    }

    @Test
    void shouldCaptureProviderResponseHeadersAndSanitizeThem() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnv";

        ImmediateRequestDTO request = createRequest("SchoolInfo");
        ProviderRegistrationDTO provider = createProvider(
                wireMockServer.baseUrl() + "/api/schools");

        UUID responseMessageId = UUID.randomUUID();

        // Provider returns custom + blacklisted response headers
        wireMockServer.stubFor(post(urlPathEqualTo("/api/schools"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("x-provider-trace", "trace-42")
                        .withHeader("x-debug-info", "debug-value")
                        .withBody(String.format("""
                            {
                                "messageId": "%s",
                                "timestamp": "2024-01-15T10:00:00Z",
                                "messageType": "RESPONSE",
                                "serviceName": "SchoolInfo",
                                "serviceType": "OBJECT",
                                "headers": {
                                    "x-provider-trace": "trace-42",
                                    "x-debug-info": "debug-value",
                                    "Authorization": "Bearer leaked-secret",
                                    "fingerprint": "provider-fp"
                                },
                                "payload": {"id": "school-1"}
                            }
                            """, responseMessageId))));

        Map<String, String> passthroughHeaders = Map.of("x-vendor-name", "Vendor1");

        // Act
        RequestResponseDTO response = handler.handleRequest(
                environmentId, environmentName, "test-domain", "test-solution", provider, request, passthroughHeaders);

        // Assert — response headers should be sanitized
        assertThat(response).isNotNull();
        assertThat(response.getHeaders()).isNotNull();

        Set<String> responseKeys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        responseKeys.addAll(response.getHeaders().keySet());

        // Non-blacklisted provider response headers SHOULD be present
        assertThat(responseKeys).contains("x-provider-trace");
        assertThat(response.getHeaders().entrySet()).anyMatch(
                e -> e.getKey().equalsIgnoreCase("x-provider-trace")
                        && "trace-42".equals(e.getValue()));
        assertThat(responseKeys).contains("x-debug-info");

        // Blacklisted headers from provider response SHOULD NOT be present
        assertThat(responseKeys).doesNotContain("Authorization");
        assertThat(responseKeys).doesNotContain("fingerprint");
    }

    @Test
    void shouldHandleEmptyPassthroughHeaders() {
        // Arrange
        UUID environmentId = UUID.randomUUID();
        String environmentName = "TestEnv";

        ImmediateRequestDTO request = createRequest("StudentPersonal");
        ProviderRegistrationDTO provider = createProvider(
                wireMockServer.baseUrl() + "/api/students");

        UUID responseMessageId = UUID.randomUUID();
        stubProviderSuccess("/api/students", responseMessageId);

        // Act — empty passthrough headers
        RequestResponseDTO response = handler.handleRequest(
                environmentId, environmentName, "test-domain", "test-solution", provider, request, Map.of());

        // Assert — request should succeed without custom headers
        assertThat(response).isNotNull();
        assertThat(response.getMessageId()).isEqualTo(responseMessageId);

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/api/students")));
    }

    // ========== Helpers ==========

    private ImmediateRequestDTO createRequest(String serviceName) {
        ImmediateRequestDTO dto = new ImmediateRequestDTO();
        dto.setServiceName(serviceName);
        dto.setServiceType(ServiceType.OBJECT.name());
        dto.setMessageType(MessageType.REQUEST.name());
        dto.setPayload(Map.of("id", "123"));
        return dto;
    }

    private ProviderRegistrationDTO createProvider(String endpoint) {
        return new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "TestService",
                "default",
                "default",
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of(endpoint),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY, Operation.UPDATE, Operation.DELETE))))),
                Instant.now()
        );
    }

    private void stubProviderSuccess(String path, UUID responseMessageId) {
        wireMockServer.stubFor(post(urlPathEqualTo(path))
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
                                "payload": {"id": "123", "name": "John Doe"}
                            }
                            """, responseMessageId))));
    }
}
