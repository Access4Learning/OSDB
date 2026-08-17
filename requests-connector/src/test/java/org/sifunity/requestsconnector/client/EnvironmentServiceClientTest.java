// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EnvironmentServiceClient.resolveContextUuid().
 * Covers all branches in the context resolution logic.
 *
 * <p>The implementation calls GET /internal/contexts/resolve?domainId=X&solutionId=Y&zoneId=Z&contextId=C
 * which returns {"contextUuid":"uuid-string"} on success or 404 if not found.</p>
 */
@ExtendWith(MockitoExtension.class)
class EnvironmentServiceClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private EnvironmentServiceClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // Use reflection-free approach: build client with a mock WebClient.Builder
        WebClient.Builder builder = mock(WebClient.Builder.class);
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(webClient);

        client = new EnvironmentServiceClient(builder, "http://localhost:8080");
    }

    @Nested
    class ResolveContextUuidTests {

        @Test
        @SuppressWarnings("unchecked")
        void contextIdNull_defaultsToDefaultContext() {
            // null contextId → defaults to DEFAULT → resolves via env-service
            UUID expectedUuid = UUID.randomUUID();
            JsonNode response = buildResolveResponse(expectedUuid.toString());
            setupWebClientChain(Mono.just(response));

            UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", null);
            assertThat(result).isEqualTo(expectedUuid);
        }

        @Test
        @SuppressWarnings("unchecked")
        void contextIdBlank_defaultsToDefaultContext() {
            // blank contextId → defaults to DEFAULT → resolves via env-service
            UUID expectedUuid = UUID.randomUUID();
            JsonNode response = buildResolveResponse(expectedUuid.toString());
            setupWebClientChain(Mono.just(response));

            UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", "   ");
            assertThat(result).isEqualTo(expectedUuid);
        }

        @Test
        @SuppressWarnings("unchecked")
        void responseNull_returnsNull() {
            setupWebClientChain(Mono.justOrEmpty(null));

            UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", "DEFAULT");
            assertThat(result).isNull();
        }

        @Test
        @SuppressWarnings("unchecked")
        void responseWithoutContextUuidField_returnsNull() {
            // Response without "contextUuid" key
            ObjectNode response = objectMapper.createObjectNode();
            response.put("other", "data");
            setupWebClientChain(Mono.just(response));

            UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", "DEFAULT");
            assertThat(result).isNull();
        }

        @Test
        @SuppressWarnings("unchecked")
        void matchFoundWithValidUuid_returnsUuid() {
            UUID expectedUuid = UUID.randomUUID();
            JsonNode response = buildResolveResponse(expectedUuid.toString());
            setupWebClientChain(Mono.just(response));

            UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", "DEFAULT");
            assertThat(result).isEqualTo(expectedUuid);
        }

        @Test
        @SuppressWarnings("unchecked")
        void resolveWithExplicitContextId_returnsUuid() {
            UUID expectedUuid = UUID.randomUUID();
            JsonNode response = buildResolveResponse(expectedUuid.toString());
            setupWebClientChain(Mono.just(response));

            UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", "myContext");
            assertThat(result).isEqualTo(expectedUuid);
        }

        @Test
        @SuppressWarnings("unchecked")
        void webClientResponseException_returnsNull() {
            setupWebClientChainThrows(
                WebClientResponseException.create(404, "Not Found", null, null, null));

            UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", "DEFAULT");
            assertThat(result).isNull();
        }

        @Test
        @SuppressWarnings("unchecked")
        void genericException_returnsNull() {
            setupWebClientChainThrows(new RuntimeException("Connection refused"));

            UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", "DEFAULT");
            assertThat(result).isNull();
        }
    }

    // ========== Helper Methods ==========

    @SuppressWarnings("unchecked")
    private void setupWebClientChain(Mono<JsonNode> monoResult) {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(), any(), any(), any()))
            .thenReturn(requestHeadersSpec);
        lenient().when(requestHeadersSpec.header(anyString(), any(String[].class)))
            .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(monoResult);
    }

    @SuppressWarnings("unchecked")
    private void setupWebClientChainThrows(Exception exception) {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(), any(), any(), any()))
            .thenReturn(requestHeadersSpec);
        lenient().when(requestHeadersSpec.header(anyString(), any(String[].class)))
            .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.error(exception));
    }

    // Helper to build the new resolve endpoint response shape: {"contextUuid":"<uuid>"}
    private JsonNode buildResolveResponse(String uuid) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("contextUuid", uuid);
        return response;
    }
}
