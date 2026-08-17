// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.unit;

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
import org.sifunity.requestsconnector.service.ProxyWorkerService;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
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
 * Unit tests for ProxyWorkerService (Streams-based).
 *
 * <p>These tests validate the background worker's behavior for processing DELAYED_to_IMMEDIATE
 * proxy requests using Redis Streams consumer groups.</p>
 */
class ProxyWorkerServiceTest {

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

    /**
     * Test successful request processing via XREADGROUP.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testSuccessfulRequestProcessing() throws Exception {
        // Given: A proxy request in the stream
        ProxyRequestMetadata metadata = createTestMetadata();
        MapRecord<String, Object, Object> record = createStreamRecord(metadata);

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
            .thenReturn(List.of(record));

        // Given: Provider returns successful response
        ProviderRawResponse rawResponse = ProviderRawResponse.builder()
            .body(Map.of("refId", "12345", "name", "John Doe"))
            .headers(Collections.emptyMap())
            .statusCode(200)
            .build();
        when(providerClient.sendRequestRawWithHeaders(any(), any(), any(), any()))
            .thenReturn(rawResponse);

        // When: Worker processes requests
        service.processProxyRequests();

        // Then: Provider was called with correct endpoint
        verify(providerClient).sendRequestRawWithHeaders(
            eq(metadata.getProviderEndpoint()),
            any(),
            any(),
            eq(metadata.getHttpMethod()));

        // Then: Response was routed to Responses Connector
        verify(responsesClient).routeResponse(
            eq(metadata.getEnvironmentId()),
            eq(metadata.getEnvironmentName()),
            eq(metadata.getDomainId()),
            eq(metadata.getSolutionId()),
            eq(metadata.getConsumerQueueId()),
            anyString(),
            any(),
            any());

        // Then: XACK + XDEL were called (success path)
        verify(streamOps).acknowledge(eq(ProxyStreamConfig.STREAM_KEY),
            eq(ProxyStreamConfig.CONSUMER_GROUP), anyString());
        verify(streamOps).delete(eq(ProxyStreamConfig.STREAM_KEY), anyString());
    }

    /**
     * Test failure leaves message in PEL (no ACK).
     */
    @Test
    @SuppressWarnings("unchecked")
    void testFailureDoesNotAck() throws Exception {
        // Given: A proxy request in the stream
        ProxyRequestMetadata metadata = createTestMetadata();
        MapRecord<String, Object, Object> record = createStreamRecord(metadata);

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
            .thenReturn(List.of(record));

        // Given: Provider call fails
        when(providerClient.sendRequestRawWithHeaders(any(), any(), any(), any()))
            .thenThrow(new RuntimeException("Provider unavailable"));

        // When: Worker processes requests
        service.processProxyRequests();

        // Then: Message was NOT acknowledged (stays in PEL for XAUTOCLAIM)
        verify(streamOps, never()).acknowledge(any(), any(), anyString());
        verify(streamOps, never()).delete(any(), anyString());
    }

    /**
     * Test empty stream (no records) — no processing occurs.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testEmptyStreamHandling() {
        // Given: Stream is empty
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
            .thenReturn(Collections.emptyList());

        // When: Worker processes requests
        service.processProxyRequests();

        // Then: No provider calls were made
        verify(providerClient, never()).sendRequestRawWithHeaders(any(), any(), any(), any());
        verify(responsesClient, never()).routeResponse(any(), any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * Test null result from XREADGROUP — no processing occurs.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testNullReadResult() {
        // Given: XREADGROUP returns null
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
            .thenReturn(null);

        // When: Worker processes requests
        service.processProxyRequests();

        // Then: No provider calls were made
        verify(providerClient, never()).sendRequestRawWithHeaders(any(), any(), any(), any());
    }

    /**
     * Test that XREADGROUP exception is handled gracefully.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testReadExceptionHandledGracefully() {
        // Given: XREADGROUP throws
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
            .thenThrow(new RuntimeException("Redis connection lost"));

        // When/Then: processProxyRequests doesn't throw
        service.processProxyRequests();

        // No provider calls
        verify(providerClient, never()).sendRequestRawWithHeaders(any(), any(), any(), any());
    }

    /**
     * Test invalid/corrupt stream entry is ACKed to prevent redelivery loop.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testCorruptStreamEntryAckedToPreventLoop() {
        // Given: A record with invalid field data
        Map<Object, Object> badFields = new HashMap<>();
        badFields.put("messageId", "not-a-uuid");
        badFields.put("requestEnvelope", "invalid json{{{");
        MapRecord<String, Object, Object> badRecord = MapRecord.create(
            ProxyStreamConfig.STREAM_KEY, badFields)
            .withId(RecordId.of("1234567890-0"));

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
            .thenReturn(List.of(badRecord));

        // When: Worker processes requests
        service.processProxyRequests();

        // Then: Bad entry was ACKed + DELeted to prevent infinite redelivery
        verify(streamOps).acknowledge(eq(ProxyStreamConfig.STREAM_KEY),
            eq(ProxyStreamConfig.CONSUMER_GROUP), eq("1234567890-0"));
        verify(streamOps).delete(eq(ProxyStreamConfig.STREAM_KEY), eq("1234567890-0"));

        // Then: No provider call was made
        verify(providerClient, never()).sendRequestRawWithHeaders(any(), any(), any(), any());
    }

    // ========== Helper Methods ==========

    private ProxyRequestMetadata createTestMetadata() {
        UUID msgId = UUID.randomUUID();
        SifMessageEnvelope<Object> requestEnvelope = SifMessageEnvelope.<Object>builder()
            .messageId(msgId)
            .requestId(msgId.toString())
            .timestamp(Instant.now())
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload(Map.of("refId", "12345"))
            .build();

        return ProxyRequestMetadata.builder()
            .messageId(UUID.randomUUID())
            .consumerQueueId(UUID.randomUUID())
            .environmentId(UUID.randomUUID())
            .environmentName("TestEnvironment")
            .domainId("test-domain")
            .solutionId("test-solution")
            .httpMethod("POST")
            .providerEnvironmentId(UUID.randomUUID())
            .relativeServicePath("test/path")
            .providerEndpoint("https://provider.example.com/api/students")
            .requestEnvelope(requestEnvelope)
            .createdAt(Instant.now())
            .retryCount(0)
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
