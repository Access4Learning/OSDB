// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for paging error handling scenarios in ProxyWorkerService.
 *
 * <p>Uses the Streams-based ProxyWorkerService with StringRedisTemplate and XREADGROUP.</p>
 *
 * <p>Validates Requirements 5.1, 5.2, 5.3, 5.4, 6.1, 6.2</p>
 */
class PagingErrorHandlingTest {

    private StringRedisTemplate stringRedisTemplate;
    private StreamOperations<String, Object, Object> streamOps;
    private ObjectMapper objectMapper;
    private ProviderHttpClient providerClient;
    private ResponsesConnectorClient responsesClient;
    private ConnectorMetricsService metricsService;
    private ProxyStreamConfig proxyStreamConfig;
    private ProxyWorkerService service;

    private static final int MAX_RETRIES = 3;
    private static final int MAX_PAGES = 1000;

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

    // ========== Scenario 1: Provider non-200 on page N ==========

    /**
     * Provider returns 200 for page 1, then 500 for page 2.
     * Verify: page 1 delivered, error delivered with "page 2 of 5", pages 3-5 not requested.
     */
    @Test
    @SuppressWarnings("unchecked")
    void providerNon200OnPageN_deliversPage1ThenError() throws Exception {
        ProxyRequestMetadata metadata = createPagingMetadata();
        MapRecord<String, Object, Object> record = createStreamRecord(metadata);

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
            .thenReturn(List.of(record));

        // Page 1: 200 with nav headers indicating 5 pages
        ProviderRawResponse page1 = buildPageResponse(1, 5, 200);
        // Page 2: 500 error
        ProviderRawResponse page2 = buildPageResponse(2, 5, 500);

        when(providerClient.sendRequestRawWithHeaders(any(), any(), any(), any()))
            .thenReturn(page1)   // first call (page 1)
            .thenReturn(page2);  // second call (page 2)

        service.processProxyRequests();

        // Page 1 delivered via routeResponse
        verify(responsesClient, times(1)).routeResponse(
            any(), any(), any(), any(), any(), any(), any(), any());

        // Error delivered for page 2
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(responsesClient, times(1)).routeErrorResponse(
            any(), any(), any(), any(), any(), any(), any(), errorCaptor.capture());
        assertThat(errorCaptor.getValue()).contains("page 2 of 5");
        assertThat(errorCaptor.getValue()).contains("500");

        // Only 2 calls to provider (page 1 + page 2), pages 3-5 never requested
        verify(providerClient, times(2)).sendRequestRawWithHeaders(any(), any(), any(), any());
    }

    // ========== Scenario 2: Exception on page N ==========

    /**
     * Provider returns 200 for page 1, then throws RuntimeException for page 2.
     * Verify: error delivered with page context.
     */
    @Test
    @SuppressWarnings("unchecked")
    void exceptionOnPageN_deliversErrorWithPageContext() throws Exception {
        ProxyRequestMetadata metadata = createPagingMetadata();
        MapRecord<String, Object, Object> record = createStreamRecord(metadata);

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
            .thenReturn(List.of(record));

        ProviderRawResponse page1 = buildPageResponse(1, 5, 200);

        when(providerClient.sendRequestRawWithHeaders(any(), any(), any(), any()))
            .thenReturn(page1)
            .thenThrow(new RuntimeException("Connection reset"));

        service.processProxyRequests();

        // Page 1 delivered
        verify(responsesClient, times(1)).routeResponse(
            any(), any(), any(), any(), any(), any(), any(), any());

        // Error delivered with page context
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(responsesClient, times(1)).routeErrorResponse(
            any(), any(), any(), any(), any(), any(), any(), errorCaptor.capture());
        assertThat(errorCaptor.getValue()).contains("page 2 of 5");
        assertThat(errorCaptor.getValue()).contains("Connection reset");
    }

    // ========== Scenario 3: Unexpected exception on page 3 ==========

    /**
     * Provider returns 200 for pages 1-2, then throws on page 3.
     * Verify: error message includes "page 3 of 5: {message}".
     */
    @Test
    @SuppressWarnings("unchecked")
    void unexpectedException_errorIncludesPageNOfL() throws Exception {
        ProxyRequestMetadata metadata = createPagingMetadata();
        MapRecord<String, Object, Object> record = createStreamRecord(metadata);

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
            .thenReturn(List.of(record));

        ProviderRawResponse page1 = buildPageResponse(1, 5, 200);
        ProviderRawResponse page2 = buildPageResponse(2, 5, 200);

        when(providerClient.sendRequestRawWithHeaders(any(), any(), any(), any()))
            .thenReturn(page1)
            .thenReturn(page2)
            .thenThrow(new RuntimeException("Unexpected NPE"));

        service.processProxyRequests();

        // Pages 1 and 2 delivered
        verify(responsesClient, times(2)).routeResponse(
            any(), any(), any(), any(), any(), any(), any(), any());

        // Error for page 3
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(responsesClient).routeErrorResponse(
            any(), any(), any(), any(), any(), any(), any(), errorCaptor.capture());
        assertThat(errorCaptor.getValue()).contains("page 3 of 5");
        assertThat(errorCaptor.getValue()).contains("Unexpected NPE");
    }

    // ========== Scenario 4: Max page limit hit ==========

    /**
     * maxPages=3, provider indicates lastPage=10.
     * Verify: only 3 pages delivered + "reached maximum page limit of 3" error.
     */
    @Test
    @SuppressWarnings("unchecked")
    void maxPageLimitHit_delivers3PagesPlusLimitError() throws Exception {
        // Create service with maxPages=3
        ProxyWorkerService limitedService = new ProxyWorkerService(
            stringRedisTemplate, objectMapper, providerClient, responsesClient,
            metricsService, new NoOpTracingUtils(), List.of(),
            proxyStreamConfig, MAX_RETRIES, 3);

        ProxyRequestMetadata metadata = createPagingMetadata();
        MapRecord<String, Object, Object> record = createStreamRecord(metadata);

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
            .thenReturn(List.of(record));

        // Provider indicates 10 pages
        ProviderRawResponse page1 = buildPageResponse(1, 10, 200);
        ProviderRawResponse page2 = buildPageResponse(2, 10, 200);
        ProviderRawResponse page3 = buildPageResponse(3, 10, 200);

        when(providerClient.sendRequestRawWithHeaders(any(), any(), any(), any()))
            .thenReturn(page1)
            .thenReturn(page2)
            .thenReturn(page3);

        limitedService.processProxyRequests();

        // 3 pages delivered
        verify(responsesClient, times(3)).routeResponse(
            any(), any(), any(), any(), any(), any(), any(), any());

        // Error about limit reached
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(responsesClient).routeErrorResponse(
            any(), any(), any(), any(), any(), any(), any(), errorCaptor.capture());
        assertThat(errorCaptor.getValue()).contains("maximum page limit of 3");
        assertThat(errorCaptor.getValue()).contains("10 total pages");

        // Only 3 provider calls (page 1 initial + pages 2,3 in loop)
        verify(providerClient, times(3)).sendRequestRawWithHeaders(any(), any(), any(), any());
    }

    // ========== Scenario 5: Provider drops nav headers mid-loop ==========

    /**
     * Provider returns page 2 without navigationPage/navigationLastPage headers.
     * Verify: error "dropped navigation headers".
     */
    @Test
    @SuppressWarnings("unchecked")
    void providerDropsNavHeaders_deliversErrorAboutMissingHeaders() throws Exception {
        ProxyRequestMetadata metadata = createPagingMetadata();
        MapRecord<String, Object, Object> record = createStreamRecord(metadata);

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
            .thenReturn(List.of(record));

        ProviderRawResponse page1 = buildPageResponse(1, 5, 200);
        // Page 2: 200 but no navigation headers
        ProviderRawResponse page2NoNav = ProviderRawResponse.builder()
            .body(Map.of("data", "page2"))
            .headers(Map.of("Content-Type", "application/json"))
            .statusCode(200)
            .build();

        when(providerClient.sendRequestRawWithHeaders(any(), any(), any(), any()))
            .thenReturn(page1)
            .thenReturn(page2NoNav);

        service.processProxyRequests();

        // Page 1 delivered
        verify(responsesClient, times(1)).routeResponse(
            any(), any(), any(), any(), any(), any(), any(), any());

        // Error about dropped headers
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(responsesClient).routeErrorResponse(
            any(), any(), any(), any(), any(), any(), any(), errorCaptor.capture());
        assertThat(errorCaptor.getValue()).contains("dropped navigation headers");
    }

    // ========== Scenario 6: Single-page response (no paging) ==========

    /**
     * Response with navigationPage=1, navigationLastPage=1.
     * Verify: isPagingRequired returns false, single response delivered.
     */
    @Test
    @SuppressWarnings("unchecked")
    void singlePageResponse_noPagingInitiated() throws Exception {
        ProxyRequestMetadata metadata = createPagingMetadata();
        MapRecord<String, Object, Object> record = createStreamRecord(metadata);

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
            .thenReturn(List.of(record));

        // Single page: navigationPage == navigationLastPage
        ProviderRawResponse singlePage = buildPageResponse(1, 1, 200);

        when(providerClient.sendRequestRawWithHeaders(any(), any(), any(), any()))
            .thenReturn(singlePage);

        // Verify isPagingRequired directly
        assertThat(service.isPagingRequired(metadata, singlePage)).isFalse();

        // Process through full flow
        service.processProxyRequests();

        // Single response delivered (not via paging loop)
        verify(responsesClient, times(1)).routeResponse(
            any(), any(), any(), any(), any(), any(), any(), any());

        // No error
        verify(responsesClient, never()).routeErrorResponse(
            any(), any(), any(), any(), any(), any(), any(), any());

        // Only 1 provider call
        verify(providerClient, times(1)).sendRequestRawWithHeaders(any(), any(), any(), any());
    }

    // ========== Scenario 7: Non-GET with nav headers (no paging) ==========

    /**
     * POST request with navigation headers in response.
     * Verify: isPagingRequired returns false, single response delivered.
     */
    @Test
    @SuppressWarnings("unchecked")
    void nonGetWithNavHeaders_noPagingInitiated() throws Exception {
        // POST metadata (not GET)
        ProxyRequestMetadata metadata = createPagingMetadata();
        metadata.setHttpMethod("POST");

        MapRecord<String, Object, Object> record = createStreamRecord(metadata);

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
            .thenReturn(List.of(record));

        // Response has nav headers but method is POST
        ProviderRawResponse response = buildPageResponse(1, 5, 200);

        when(providerClient.sendRequestRawWithHeaders(any(), any(), any(), any()))
            .thenReturn(response);

        // Verify isPagingRequired directly
        assertThat(service.isPagingRequired(metadata, response)).isFalse();

        // Process through full flow
        service.processProxyRequests();

        // Single response delivered
        verify(responsesClient, times(1)).routeResponse(
            any(), any(), any(), any(), any(), any(), any(), any());

        // No error, no paging
        verify(responsesClient, never()).routeErrorResponse(
            any(), any(), any(), any(), any(), any(), any(), any());

        // Only 1 provider call
        verify(providerClient, times(1)).sendRequestRawWithHeaders(any(), any(), any(), any());
    }

    // ========== Helper Methods ==========

    /**
     * Creates test metadata configured for paging: GET method, queryIntention=ALL.
     */
    private ProxyRequestMetadata createPagingMetadata() {
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("queryIntention", "ALL");
        requestHeaders.put("navigationPageSize", "10");

        SifMessageEnvelope<Object> requestEnvelope = SifMessageEnvelope.<Object>builder()
            .messageId(UUID.randomUUID())
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonals")
            .serviceType(ServiceType.OBJECT)
            .headers(requestHeaders)
            .payload(null)
            .build();

        return ProxyRequestMetadata.builder()
            .messageId(UUID.randomUUID())
            .consumerQueueId(UUID.randomUUID())
            .environmentId(UUID.randomUUID())
            .environmentName("TestEnvironment")
            .domainId("test-domain")
            .solutionId("test-solution")
            .httpMethod("GET")
            .providerEnvironmentId(UUID.randomUUID())
            .relativeServicePath("StudentPersonals;zoneId=DEFAULT")
            .providerEndpoint("https://provider.example.com/api/StudentPersonals")
            .requestEnvelope(requestEnvelope)
            .createdAt(Instant.now())
            .retryCount(0)
            .build();
    }

    /**
     * Builds a ProviderRawResponse with navigation headers.
     */
    private ProviderRawResponse buildPageResponse(int page, int lastPage, int statusCode) {
        Map<String, String> headers = new HashMap<>();
        headers.put("navigationPage", String.valueOf(page));
        headers.put("navigationLastPage", String.valueOf(lastPage));
        headers.put("navigationPageSize", "10");
        headers.put("navigationId", "nav-cursor-abc");
        headers.put("navigationCount", String.valueOf(lastPage * 10));

        return ProviderRawResponse.builder()
            .body(Map.of("data", "page" + page))
            .headers(headers)
            .statusCode(statusCode)
            .build();
    }

    @SuppressWarnings("unchecked")
    private MapRecord<String, Object, Object> createStreamRecord(ProxyRequestMetadata metadata) throws Exception {
        SifMessageEnvelope<?> envelope = (SifMessageEnvelope<?>) metadata.getRequestEnvelope();
        String envelopeJson = objectMapper.writeValueAsString(envelope);

        Map<Object, Object> fields = new HashMap<>();
        fields.put("messageId", metadata.getMessageId().toString());
        fields.put("consumerQueueId", metadata.getConsumerQueueId().toString());
        fields.put("environmentId", metadata.getEnvironmentId().toString());
        fields.put("environmentName", metadata.getEnvironmentName());
        fields.put("domainId", metadata.getDomainId());
        fields.put("solutionId", metadata.getSolutionId());
        fields.put("httpMethod", metadata.getHttpMethod());
        fields.put("providerEnvironmentId", metadata.getProviderEnvironmentId().toString());
        fields.put("relativeServicePath", metadata.getRelativeServicePath());
        fields.put("providerEndpoint", metadata.getProviderEndpoint());
        fields.put("createdAt", metadata.getCreatedAt().toString());
        fields.put("requestEnvelope", envelopeJson);

        return MapRecord.create(ProxyStreamConfig.STREAM_KEY, fields)
            .withId(RecordId.of("1234567890-0"));
    }
}
