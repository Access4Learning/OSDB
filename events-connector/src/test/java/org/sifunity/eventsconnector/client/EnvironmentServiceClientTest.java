// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EnvironmentServiceClient.
 * Tests all branches in resolveContextUuid() using mocked RestClient.
 *
 * <p>The implementation calls GET /internal/contexts/resolve?domainId=X&solutionId=Y&zoneId=Z&contextId=C
 * which returns {"contextUuid":"uuid-string"} on success or 404 if not found.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EnvironmentServiceClient Tests")
class EnvironmentServiceClientTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private EnvironmentServiceClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        client = new EnvironmentServiceClient(restClient, "http://localhost:8080");
        objectMapper = new ObjectMapper();
    }

    @SuppressWarnings("unchecked")
    private void setupRestClientChain(JsonNode responseBody) {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(), any(), any(), any())).thenReturn(requestHeadersSpec);
        lenient().when(requestHeadersSpec.header(anyString(), any(String[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(responseBody);
    }

    @SuppressWarnings("unchecked")
    private void setupRestClientChainThrows(Exception exception) {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(), any(), any(), any())).thenReturn(requestHeadersSpec);
        lenient().when(requestHeadersSpec.header(anyString(), any(String[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenThrow(exception);
    }

    @Nested
    @DisplayName("resolveContextUuid - null/blank contextId defaults to DEFAULT")
    class NullBlankContextId {

        @Test
        @DisplayName("should default to DEFAULT and resolve when contextId is null")
        void shouldDefaultToDefaultWhenContextIdIsNull() {
            UUID expectedUuid = UUID.randomUUID();
            ObjectNode response = buildResolveResponse(expectedUuid.toString());
            setupRestClientChain(response);

            UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", null);
            assertThat(result).isEqualTo(expectedUuid);
        }

        @Test
        @DisplayName("should default to DEFAULT and resolve when contextId is empty")
        void shouldDefaultToDefaultWhenContextIdIsEmpty() {
            UUID expectedUuid = UUID.randomUUID();
            ObjectNode response = buildResolveResponse(expectedUuid.toString());
            setupRestClientChain(response);

            UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", "");
            assertThat(result).isEqualTo(expectedUuid);
        }

        @Test
        @DisplayName("should default to DEFAULT and resolve when contextId is blank")
        void shouldDefaultToDefaultWhenContextIdIsBlank() {
            UUID expectedUuid = UUID.randomUUID();
            ObjectNode response = buildResolveResponse(expectedUuid.toString());
            setupRestClientChain(response);

            UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", "   ");
            assertThat(result).isEqualTo(expectedUuid);
        }
    }

    @Nested
    @DisplayName("resolveContextUuid - response handling")
    class ResponseHandling {

        @Test
        @DisplayName("should return null when response is null")
        void shouldReturnNullWhenResponseIsNull() {
            setupRestClientChain(null);

            UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", "DEFAULT");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when contextUuid field is missing from response")
        void shouldReturnNullWhenContextUuidFieldMissing() {
            // Response without "contextUuid" key
            ObjectNode response = objectMapper.createObjectNode();
            response.put("other", "data");
            setupRestClientChain(response);

            UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", "DEFAULT");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return UUID when contextUuid field is present")
        void shouldReturnUuidWhenContextUuidPresent() {
            UUID expectedUuid = UUID.randomUUID();
            ObjectNode response = buildResolveResponse(expectedUuid.toString());
            setupRestClientChain(response);

            UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", "DEFAULT");

            assertThat(result).isEqualTo(expectedUuid);
        }

        @Test
        @DisplayName("should resolve with explicit contextId")
        void shouldResolveWithExplicitContextId() {
            UUID expectedUuid = UUID.randomUUID();
            ObjectNode response = buildResolveResponse(expectedUuid.toString());
            setupRestClientChain(response);

            UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", "myContext");

            assertThat(result).isEqualTo(expectedUuid);
        }
    }

    @Nested
    @DisplayName("resolveContextUuid - exception handling")
    class ExceptionHandling {

        @Test
        @DisplayName("should return null on HttpClientErrorException (404)")
        void shouldReturnNullOnHttpClientError404() {
            setupRestClientChainThrows(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found"));

            UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", "DEFAULT");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null on HttpServerErrorException (5xx)")
        void shouldReturnNullOnHttpServerError() {
            setupRestClientChainThrows(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error"));

            UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", "DEFAULT");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null on generic Exception")
        void shouldReturnNullOnGenericException() {
            setupRestClientChainThrows(new RuntimeException("Connection refused"));

            UUID result = client.resolveContextUuid("domain1", "solution1", "zone1", "DEFAULT");

            assertThat(result).isNull();
        }
    }

    // Helper to build the new resolve endpoint response shape: {"contextUuid":"<uuid>"}
    private ObjectNode buildResolveResponse(String uuid) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("contextUuid", uuid);
        return response;
    }
}
