// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.metrics.ConnectorMetricsService;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.util.NoOpTracingUtils;
import org.sifunity.requestsconnector.client.ProviderHttpClient;
import org.sifunity.requestsconnector.client.ResponsesConnectorClient;
import org.sifunity.requestsconnector.config.ProxyStreamConfig;
import org.sifunity.requestsconnector.dto.ProxyRequestMetadata;
import org.sifunity.requestsconnector.dto.ProviderRawResponse;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProxyWorkerService paging logic.
 * Tests isPagingRequired, buildNextPageHeaders, executePagedQuery,
 * getHeaderCaseInsensitive, and error routing failure paths.
 *
 * <p>Uses the Streams-based ProxyWorkerService constructor with StringRedisTemplate.</p>
 */
class ProxyWorkerServicePagingTest {

    private StringRedisTemplate stringRedisTemplate;
    private StreamOperations<String, Object, Object> streamOps;
    private ObjectMapper objectMapper;
    private ProviderHttpClient providerClient;
    private ResponsesConnectorClient responsesClient;
    private ConnectorMetricsService metricsService;
    private ProxyStreamConfig proxyStreamConfig;
    private ProxyWorkerService service;

    private static final int MAX_RETRIES = 3;
    private static final int MAX_PAGES = 5;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        streamOps = mock(StreamOperations.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        providerClient = mock(ProviderHttpClient.class);
        responsesClient = mock(ResponsesConnectorClient.class);
        metricsService = mock(ConnectorMetricsService.class);
        proxyStreamConfig = new ProxyStreamConfig(120000, MAX_RETRIES);

        when(stringRedisTemplate.opsForStream()).thenReturn(streamOps);

        service = new ProxyWorkerService(
            stringRedisTemplate, objectMapper, providerClient, responsesClient,
            metricsService, new NoOpTracingUtils(), List.of(),
            proxyStreamConfig, MAX_RETRIES, MAX_PAGES);
    }

    // ========== getHeaderCaseInsensitive ==========

    @Nested
    class GetHeaderCaseInsensitiveTests {

        @Test
        void nullHeaders_returnsNull() {
            assertThat(ProxyWorkerService.getHeaderCaseInsensitive(null, "key")).isNull();
        }

        @Test
        void nullName_returnsNull() {
            Map<String, String> headers = Map.of("key", "value");
            assertThat(ProxyWorkerService.getHeaderCaseInsensitive(headers, null)).isNull();
        }

        @Test
        void exactMatch_returnsValue() {
            Map<String, String> headers = Map.of("navigationPage", "3");
            assertThat(ProxyWorkerService.getHeaderCaseInsensitive(headers, "navigationPage")).isEqualTo("3");
        }

        @Test
        void caseInsensitiveMatch_returnsValue() {
            Map<String, String> headers = new HashMap<>();
            headers.put("NAVIGATIONPAGE", "5");
            assertThat(ProxyWorkerService.getHeaderCaseInsensitive(headers, "navigationPage")).isEqualTo("5");
        }

        @Test
        void noMatch_returnsNull() {
            Map<String, String> headers = Map.of("otherHeader", "value");
            assertThat(ProxyWorkerService.getHeaderCaseInsensitive(headers, "navigationPage")).isNull();
        }
    }

    // ========== isPagingRequired ==========

    @Nested
    class IsPagingRequiredTests {

        @Test
        void httpMethodNotGet_returnsFalse() {
            ProxyRequestMetadata metadata = buildMetadata("POST", Map.of("queryIntention", "ALL"));
            ProviderRawResponse response = buildResponse(200, Map.of(
                "navigationPage", "1", "navigationLastPage", "3"));

            assertThat(service.isPagingRequired(metadata, response)).isFalse();
        }

        @Test
        void queryIntentionHeaderMissing_returnsFalse() {
            ProxyRequestMetadata metadata = buildMetadata("GET", Collections.emptyMap());
            ProviderRawResponse response = buildResponse(200, Map.of(
                "navigationPage", "1", "navigationLastPage", "3"));

            assertThat(service.isPagingRequired(metadata, response)).isFalse();
        }

        @Test
        void queryIntentionNotAll_returnsFalse() {
            ProxyRequestMetadata metadata = buildMetadata("GET", Map.of("queryIntention", "ONE"));
            ProviderRawResponse response = buildResponse(200, Map.of(
                "navigationPage", "1", "navigationLastPage", "3"));

            assertThat(service.isPagingRequired(metadata, response)).isFalse();
        }

        @Test
        void responseStatusNot200_returnsFalse() {
            ProxyRequestMetadata metadata = buildMetadata("GET", Map.of("queryIntention", "ALL"));
            ProviderRawResponse response = buildResponse(404, Map.of(
                "navigationPage", "1", "navigationLastPage", "3"));

            assertThat(service.isPagingRequired(metadata, response)).isFalse();
        }

        @Test
        void responseHeadersNull_returnsFalse() {
            ProxyRequestMetadata metadata = buildMetadata("GET", Map.of("queryIntention", "ALL"));
            ProviderRawResponse response = ProviderRawResponse.builder()
                .body(Map.of())
                .headers(null)
                .statusCode(200)
                .build();

            assertThat(service.isPagingRequired(metadata, response)).isFalse();
        }

        @Test
        void navigationPageMissing_returnsFalse() {
            ProxyRequestMetadata metadata = buildMetadata("GET", Map.of("queryIntention", "ALL"));
            ProviderRawResponse response = buildResponse(200, Map.of("navigationLastPage", "3"));

            assertThat(service.isPagingRequired(metadata, response)).isFalse();
        }

        @Test
        void navigationLastPageMissing_returnsFalse() {
            ProxyRequestMetadata metadata = buildMetadata("GET", Map.of("queryIntention", "ALL"));
            ProviderRawResponse response = buildResponse(200, Map.of("navigationPage", "1"));

            assertThat(service.isPagingRequired(metadata, response)).isFalse();
        }

        @Test
        void navigationPageEqualsLastPage_returnsFalse() {
            ProxyRequestMetadata metadata = buildMetadata("GET", Map.of("queryIntention", "ALL"));
            ProviderRawResponse response = buildResponse(200, Map.of(
                "navigationPage", "3", "navigationLastPage", "3"));

            assertThat(service.isPagingRequired(metadata, response)).isFalse();
        }

        @Test
        void allConditionsMet_returnsTrue() {
            ProxyRequestMetadata metadata = buildMetadata("GET", Map.of("queryIntention", "ALL"));
            ProviderRawResponse response = buildResponse(200, Map.of(
                "navigationPage", "1", "navigationLastPage", "3"));

            assertThat(service.isPagingRequired(metadata, response)).isTrue();
        }

        @Test
        void requestEnvelopeHeadersNull_returnsFalse() {
            SifMessageEnvelope<Object> envelope = SifMessageEnvelope.<Object>builder()
                .messageId(UUID.randomUUID())
                .timestamp(Instant.now())
                .messageType(MessageType.REQUEST)
                .serviceName("StudentPersonal")
                .serviceType(ServiceType.OBJECT)
                .headers(null)
                .payload(Map.of())
                .build();

            ProxyRequestMetadata metadata = ProxyRequestMetadata.builder()
                .messageId(UUID.randomUUID())
                .consumerQueueId(UUID.randomUUID())
                .environmentId(UUID.randomUUID())
                .environmentName("Test")
                .domainId("domain")
                .solutionId("solution")
                .httpMethod("GET")
                .providerEnvironmentId(UUID.randomUUID())
                .providerEndpoint("http://provider/api")
                .requestEnvelope(envelope)
                .createdAt(Instant.now())
                .retryCount(0)
                .build();

            ProviderRawResponse response = buildResponse(200, Map.of(
                "navigationPage", "1", "navigationLastPage", "3"));

            assertThat(service.isPagingRequired(metadata, response)).isFalse();
        }
    }

    // ========== buildNextPageHeaders ==========

    @Nested
    class BuildNextPageHeadersTests {

        @Test
        void setsNavigationPageToNextPage() {
            Map<String, String> original = new HashMap<>(Map.of("queryIntention", "ALL"));
            ProviderRawResponse prev = buildResponse(200, Map.of(
                "navigationPage", "1", "navigationLastPage", "3", "navigationId", "nav-123"));

            Map<String, String> result = service.buildNextPageHeaders(original, prev, 2, 3);

            assertThat(result.get("navigationPage")).isEqualTo("2");
        }

        @Test
        void carriesNavigationIdFromPreviousResponse() {
            Map<String, String> original = new HashMap<>(Map.of("queryIntention", "ALL"));
            ProviderRawResponse prev = buildResponse(200, Map.of(
                "navigationPage", "1", "navigationLastPage", "3", "navigationId", "nav-456"));

            Map<String, String> result = service.buildNextPageHeaders(original, prev, 2, 3);

            assertThat(result.get("navigationId")).isEqualTo("nav-456");
        }

        @Test
        void navigationIdNullInPreviousResponse_notAdded() {
            Map<String, String> original = new HashMap<>(Map.of("queryIntention", "ALL"));
            ProviderRawResponse prev = buildResponse(200, Map.of(
                "navigationPage", "1", "navigationLastPage", "3"));

            Map<String, String> result = service.buildNextPageHeaders(original, prev, 2, 3);

            assertThat(result).doesNotContainKey("navigationId");
        }

        @Test
        void nextPageLessThanLastPage_includesQueryIntentionAll() {
            Map<String, String> original = new HashMap<>(Map.of("queryIntention", "ALL"));
            ProviderRawResponse prev = buildResponse(200, Map.of(
                "navigationPage", "1", "navigationLastPage", "5"));

            Map<String, String> result = service.buildNextPageHeaders(original, prev, 2, 5);

            assertThat(result.get("queryIntention")).isEqualTo("ALL");
        }

        @Test
        void nextPageEqualsLastPage_removesQueryIntention() {
            Map<String, String> original = new HashMap<>(Map.of("queryIntention", "ALL"));
            ProviderRawResponse prev = buildResponse(200, Map.of(
                "navigationPage", "2", "navigationLastPage", "3"));

            Map<String, String> result = service.buildNextPageHeaders(original, prev, 3, 3);

            assertThat(result).doesNotContainKey("queryIntention");
        }

        @Test
        void previousResponseHeadersNull_navigationIdNotAdded() {
            Map<String, String> original = new HashMap<>(Map.of("queryIntention", "ALL"));
            ProviderRawResponse prev = ProviderRawResponse.builder()
                .body(Map.of())
                .headers(null)
                .statusCode(200)
                .build();

            Map<String, String> result = service.buildNextPageHeaders(original, prev, 2, 3);

            assertThat(result).doesNotContainKey("navigationId");
            assertThat(result.get("navigationPage")).isEqualTo("2");
        }
    }

    // ========== executePagedQuery ==========

    @Nested
    class ExecutePagedQueryTests {

        @Test
        void providerReturnsNon200OnPage2_deliversErrorAndStops() {
            ProxyRequestMetadata metadata = buildMetadata("GET", Map.of("queryIntention", "ALL"));
            SifMessageEnvelope<?> envelope = (SifMessageEnvelope<?>) metadata.getRequestEnvelope();
            Map<String, String> headers = new HashMap<>(Map.of("queryIntention", "ALL"));

            ProviderRawResponse firstPage = buildResponse(200, Map.of(
                "navigationPage", "1", "navigationLastPage", "3"));

            ProviderRawResponse page2 = buildResponse(500, Map.of(
                "navigationPage", "2", "navigationLastPage", "3"));
            when(providerClient.sendRequestRawWithHeaders(any(), any(), any(), eq("GET")))
                .thenReturn(page2);

            service.executePagedQuery(metadata, envelope, headers, firstPage);

            verify(responsesClient).routeErrorResponse(
                eq(metadata.getEnvironmentId()),
                eq(metadata.getEnvironmentName()),
                eq(metadata.getDomainId()),
                eq(metadata.getSolutionId()),
                eq(metadata.getConsumerQueueId()),
                anyString(),
                eq("StudentPersonal"),
                argThat((String msg) -> msg.contains("status 500")));

            verify(providerClient, times(1)).sendRequestRawWithHeaders(any(), any(), any(), any());
        }

        @Test
        void providerDropsNavigationHeadersOnPage2_deliversErrorAndStops() {
            ProxyRequestMetadata metadata = buildMetadata("GET", Map.of("queryIntention", "ALL"));
            SifMessageEnvelope<?> envelope = (SifMessageEnvelope<?>) metadata.getRequestEnvelope();
            Map<String, String> headers = new HashMap<>(Map.of("queryIntention", "ALL"));

            ProviderRawResponse firstPage = buildResponse(200, Map.of(
                "navigationPage", "1", "navigationLastPage", "3"));

            ProviderRawResponse page2 = buildResponse(200, Collections.emptyMap());
            when(providerClient.sendRequestRawWithHeaders(any(), any(), any(), eq("GET")))
                .thenReturn(page2);

            service.executePagedQuery(metadata, envelope, headers, firstPage);

            verify(responsesClient).routeErrorResponse(
                any(), any(), any(), any(), any(), any(), any(),
                argThat(msg -> msg.contains("dropped navigation headers")));
        }

        @Test
        void exceptionOnPage2_deliversErrorAndStops() {
            ProxyRequestMetadata metadata = buildMetadata("GET", Map.of("queryIntention", "ALL"));
            SifMessageEnvelope<?> envelope = (SifMessageEnvelope<?>) metadata.getRequestEnvelope();
            Map<String, String> headers = new HashMap<>(Map.of("queryIntention", "ALL"));

            ProviderRawResponse firstPage = buildResponse(200, Map.of(
                "navigationPage", "1", "navigationLastPage", "3"));

            when(providerClient.sendRequestRawWithHeaders(any(), any(), any(), eq("GET")))
                .thenThrow(new RuntimeException("Connection reset"));

            service.executePagedQuery(metadata, envelope, headers, firstPage);

            verify(responsesClient).routeErrorResponse(
                any(), any(), any(), any(), any(), any(), any(),
                argThat(msg -> msg.contains("Connection reset")));
        }

        @Test
        void maxPagesLimitReached_deliversErrorAfterLastAllowedPage() {
            ProxyRequestMetadata metadata = buildMetadata("GET", Map.of("queryIntention", "ALL"));
            SifMessageEnvelope<?> envelope = (SifMessageEnvelope<?>) metadata.getRequestEnvelope();
            Map<String, String> headers = new HashMap<>(Map.of("queryIntention", "ALL"));

            // lastPage=10 exceeds maxPages=5
            ProviderRawResponse firstPage = buildResponse(200, Map.of(
                "navigationPage", "1", "navigationLastPage", "10"));

            ProviderRawResponse successPage = buildResponse(200, Map.of(
                "navigationPage", "2", "navigationLastPage", "10"));
            when(providerClient.sendRequestRawWithHeaders(any(), any(), any(), eq("GET")))
                .thenReturn(successPage);

            service.executePagedQuery(metadata, envelope, headers, firstPage);

            verify(responsesClient).routeErrorResponse(
                any(), any(), any(), any(), any(), any(), any(),
                argThat(msg -> msg.contains("maximum page limit")));

            // Pages 2..5 fetched (4 calls)
            verify(providerClient, times(4)).sendRequestRawWithHeaders(any(), any(), any(), any());

            verify(metricsService).recordPagingTimeout(any(), any());
        }

        @Test
        void normalMultiPageCompletion_deliversAllPages() {
            ProxyRequestMetadata metadata = buildMetadata("GET", Map.of("queryIntention", "ALL"));
            SifMessageEnvelope<?> envelope = (SifMessageEnvelope<?>) metadata.getRequestEnvelope();
            Map<String, String> headers = new HashMap<>(Map.of("queryIntention", "ALL"));

            ProviderRawResponse firstPage = buildResponse(200, Map.of(
                "navigationPage", "1", "navigationLastPage", "2"));

            ProviderRawResponse page2 = buildResponse(200, Map.of(
                "navigationPage", "2", "navigationLastPage", "2"));
            when(providerClient.sendRequestRawWithHeaders(any(), any(), any(), eq("GET")))
                .thenReturn(page2);

            service.executePagedQuery(metadata, envelope, headers, firstPage);

            // First page + page 2 = 2 routeResponse calls
            verify(responsesClient, times(2)).routeResponse(
                any(), any(), any(), any(), any(), any(), any(), any());

            verify(responsesClient, never()).routeErrorResponse(
                any(), any(), any(), any(), any(), any(), any(), any());

            verify(metricsService).recordPagingPagesPerLoop(any(), any(), eq(2));
        }

        @Test
        void pageResponseHeadersNull_deliversError() {
            ProxyRequestMetadata metadata = buildMetadata("GET", Map.of("queryIntention", "ALL"));
            SifMessageEnvelope<?> envelope = (SifMessageEnvelope<?>) metadata.getRequestEnvelope();
            Map<String, String> headers = new HashMap<>(Map.of("queryIntention", "ALL"));

            ProviderRawResponse firstPage = buildResponse(200, Map.of(
                "navigationPage", "1", "navigationLastPage", "3"));

            ProviderRawResponse page2 = ProviderRawResponse.builder()
                .body(Map.of())
                .headers(null)
                .statusCode(200)
                .build();
            when(providerClient.sendRequestRawWithHeaders(any(), any(), any(), eq("GET")))
                .thenReturn(page2);

            service.executePagedQuery(metadata, envelope, headers, firstPage);

            verify(responsesClient).routeErrorResponse(
                any(), any(), any(), any(), any(), any(), any(),
                argThat(msg -> msg.contains("dropped navigation headers")));
        }
    }

    // ========== Helper Methods ==========

    private ProxyRequestMetadata buildMetadata(String httpMethod, Map<String, String> requestHeaders) {
        UUID msgId = UUID.randomUUID();
        SifMessageEnvelope<Object> envelope = SifMessageEnvelope.<Object>builder()
            .messageId(msgId)
            .requestId(msgId.toString())
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .headers(requestHeaders.isEmpty() ? null : new HashMap<>(requestHeaders))
            .payload(Map.of("refId", "12345"))
            .build();

        return ProxyRequestMetadata.builder()
            .messageId(UUID.randomUUID())
            .consumerQueueId(UUID.randomUUID())
            .environmentId(UUID.randomUUID())
            .environmentName("TestEnv")
            .domainId("test-domain")
            .solutionId("test-solution")
            .httpMethod(httpMethod)
            .providerEnvironmentId(UUID.randomUUID())
            .relativeServicePath("StudentPersonals;zoneId=DEFAULT")
            .providerEndpoint("http://provider.example.com/api/students")
            .requestEnvelope(envelope)
            .createdAt(Instant.now())
            .retryCount(0)
            .build();
    }

    private ProviderRawResponse buildResponse(int statusCode, Map<String, String> headers) {
        return ProviderRawResponse.builder()
            .body(Map.of("students", "data"))
            .headers(headers.isEmpty() ? new HashMap<>() : new HashMap<>(headers))
            .statusCode(statusCode)
            .build();
    }
}
