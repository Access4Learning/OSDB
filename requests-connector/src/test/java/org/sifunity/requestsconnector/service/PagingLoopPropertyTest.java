// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.mockito.ArgumentCaptor;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.requestsconnector.client.ProviderHttpClient;
import org.sifunity.requestsconnector.client.ResponsesConnectorClient;
import org.sifunity.requestsconnector.config.ProxyStreamConfig;
import org.sifunity.requestsconnector.dto.ProxyRequestMetadata;
import org.sifunity.requestsconnector.dto.ProviderRawResponse;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for the paging loop in ProxyWorkerService.
 *
 * <p>Validates Properties 4, 5, 6, and 7 from the design document using
 * mocked ProviderHttpClient and ResponsesConnectorClient.</p>
 *
 * <p>This test class is in the {@code service} package to access the package-private
 * {@code executePagedQuery} method directly.</p>
 */
class PagingLoopPropertyTest {

    // ========== Helper Methods ==========

    /**
     * Creates a ProxyWorkerService with mocked dependencies and given maxPages.
     */
    private ServiceWithMocks createService(int maxPages) {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        ProviderHttpClient providerClient = mock(ProviderHttpClient.class);
        ResponsesConnectorClient responsesClient = mock(ResponsesConnectorClient.class);

        ProxyWorkerService service = new ProxyWorkerService(
                stringRedisTemplate, new ObjectMapper(), providerClient, responsesClient,
                mock(org.sifunity.broker.common.metrics.ConnectorMetricsService.class),
                new org.sifunity.broker.common.util.NoOpTracingUtils(),
                java.util.List.of(),
                new ProxyStreamConfig(120000, 3),
                3, maxPages);

        return new ServiceWithMocks(service, providerClient, responsesClient);
    }

    /**
     * Builds a standard ProxyRequestMetadata for testing.
     */
    private ProxyRequestMetadata buildMetadata(UUID messageId) {
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("queryIntention", "ALL");
        requestHeaders.put("navigationPageSize", "10");
        requestHeaders.put("requestId", messageId.toString());

        SifMessageEnvelope<Object> envelope = SifMessageEnvelope.<Object>builder()
                .messageId(messageId)
                .requestId(messageId.toString())
                .messageType(MessageType.REQUEST)
                .serviceName("StudentPersonals")
                .serviceType(ServiceType.OBJECT)
                .headers(requestHeaders)
                .build();

        return ProxyRequestMetadata.builder()
                .messageId(messageId)
                .consumerQueueId(UUID.randomUUID())
                .environmentId(UUID.randomUUID())
                .environmentName("test-env")
                .domainId("test-domain")
                .solutionId("test-solution")
                .httpMethod("GET")
                .relativeServicePath("StudentPersonals")
                .providerEndpoint("https://provider.example.com/StudentPersonals")
                .requestEnvelope(envelope)
                .createdAt(Instant.now())
                .retryCount(0)
                .build();
    }

    /**
     * Builds a ProviderRawResponse simulating a page from the provider.
     */
    private ProviderRawResponse buildPageResponse(int currentPage, int lastPage, String navigationId) {
        Map<String, String> headers = new HashMap<>();
        headers.put("navigationPage", String.valueOf(currentPage));
        headers.put("navigationLastPage", String.valueOf(lastPage));
        headers.put("navigationPageSize", "10");
        headers.put("navigationId", navigationId);
        headers.put("navigationCount", String.valueOf(lastPage * 10));

        return ProviderRawResponse.builder()
                .body(Map.of("page", currentPage, "data", List.of("item-" + currentPage)))
                .headers(headers)
                .statusCode(200)
                .build();
    }

    /**
     * Configures the mock providerClient to return page responses for pages 2..lastPage.
     */
    private void stubProviderForPages(ProviderHttpClient providerClient, int lastPage, String navigationId) {
        // Pages 2..lastPage are fetched via sendRequestRawWithHeaders in the loop
        if (lastPage >= 2) {
            ProviderRawResponse[] subsequentResponses = new ProviderRawResponse[lastPage - 1];
            for (int page = 2; page <= lastPage; page++) {
                subsequentResponses[page - 2] = buildPageResponse(page, lastPage, navigationId);
            }

            if (subsequentResponses.length == 1) {
                when(providerClient.sendRequestRawWithHeaders(anyString(), any(), any(), anyString()))
                        .thenReturn(subsequentResponses[0]);
            } else {
                // First call returns page 2, subsequent calls return pages 3..N
                ProviderRawResponse first = subsequentResponses[0];
                ProviderRawResponse[] rest = Arrays.copyOfRange(subsequentResponses, 1, subsequentResponses.length);
                when(providerClient.sendRequestRawWithHeaders(anyString(), any(), any(), anyString()))
                        .thenReturn(first, rest);
            }
        }
    }

    // ========== Property 4: N-page delivery count ==========

    /**
     * Property 4: Paging loop delivers exactly N pages.
     *
     * <p>For any provider returning N pages (where 1 ≤ N ≤ maxPages), the paging loop
     * SHALL invoke {@code routeResponse()} exactly N times.</p>
     *
     * <p><b>Validates: Requirements 3.1</b></p>
     */
    @Property(tries = 10)
    @Tag("Feature: paged-delayed-query, Property 4")
    void pagingLoopDeliversExactlyNPages(
            @ForAll @IntRange(min = 1, max = 20) int totalPages) {

        int maxPages = 1000; // large enough to not interfere
        ServiceWithMocks swm = createService(maxPages);
        String navigationId = "nav-" + UUID.randomUUID();
        UUID originalMessageId = UUID.randomUUID();

        ProxyRequestMetadata metadata = buildMetadata(originalMessageId);
        SifMessageEnvelope<?> requestEnvelope = (SifMessageEnvelope<?>) metadata.getRequestEnvelope();
        Map<String, String> originalHeaders = new HashMap<>(requestEnvelope.getHeaders());

        // Build first page response
        ProviderRawResponse firstPageResponse = buildPageResponse(1, totalPages, navigationId);

        // Stub provider for subsequent pages
        stubProviderForPages(swm.providerClient, totalPages, navigationId);

        // Execute paging loop
        swm.service.executePagedQuery(metadata, requestEnvelope, originalHeaders, firstPageResponse);

        // Verify routeResponse called exactly N times
        verify(swm.responsesClient, times(totalPages)).routeResponse(
                eq(metadata.getEnvironmentId()),
                eq(metadata.getEnvironmentName()),
                eq(metadata.getDomainId()),
                eq(metadata.getSolutionId()),
                eq(metadata.getConsumerQueueId()),
                anyString(),
                any(SifMessageEnvelope.class),
                eq(metadata.getRelativeServicePath()));
    }

    // ========== Property 5: Message identity ==========

    /**
     * Property 5: Message identity — unique messageIds with shared requestId.
     *
     * <p>For any paged query delivering N pages (N ≥ 1), the first page's messageId
     * SHALL equal the original request messageId, all subsequent pages SHALL have
     * unique messageIds different from each other and from the original, and all
     * pages SHALL share the same requestId equal to the original request messageId.</p>
     *
     * <p><b>Validates: Requirements 3.2, 3.3</b></p>
     */
    @Property(tries = 10)
    @Tag("Feature: paged-delayed-query, Property 5")
    void messageIdentityUniqueIdsSharedRequestId(
            @ForAll @IntRange(min = 1, max = 15) int totalPages) {

        int maxPages = 1000;
        ServiceWithMocks swm = createService(maxPages);
        String navigationId = "nav-" + UUID.randomUUID();
        UUID originalMessageId = UUID.randomUUID();

        ProxyRequestMetadata metadata = buildMetadata(originalMessageId);
        SifMessageEnvelope<?> requestEnvelope = (SifMessageEnvelope<?>) metadata.getRequestEnvelope();
        Map<String, String> originalHeaders = new HashMap<>(requestEnvelope.getHeaders());

        ProviderRawResponse firstPageResponse = buildPageResponse(1, totalPages, navigationId);
        stubProviderForPages(swm.providerClient, totalPages, navigationId);

        // Execute paging loop
        swm.service.executePagedQuery(metadata, requestEnvelope, originalHeaders, firstPageResponse);

        // Capture all SifMessageEnvelope arguments passed to routeResponse
        @SuppressWarnings("unchecked")
        ArgumentCaptor<SifMessageEnvelope<?>> envelopeCaptor =
                (ArgumentCaptor<SifMessageEnvelope<?>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(SifMessageEnvelope.class);

        verify(swm.responsesClient, times(totalPages)).routeResponse(any(UUID.class), anyString(), anyString(), anyString(), any(UUID.class), anyString(),
                envelopeCaptor.capture(),
                anyString());

        List<SifMessageEnvelope<?>> deliveredEnvelopes = envelopeCaptor.getAllValues();

        assertThat(deliveredEnvelopes).hasSize(totalPages);

        // First page messageId equals original
        assertThat(deliveredEnvelopes.get(0).getMessageId())
                .as("First page messageId should equal original request messageId")
                .isEqualTo(originalMessageId);

        // Collect all messageIds
        List<UUID> allMessageIds = new ArrayList<>();
        for (SifMessageEnvelope<?> env : deliveredEnvelopes) {
            allMessageIds.add(env.getMessageId());
        }

        // All messageIds should be unique
        Set<UUID> uniqueIds = new HashSet<>(allMessageIds);
        assertThat(uniqueIds)
                .as("All messageIds should be unique across all pages")
                .hasSize(totalPages);

        // All subsequent pages should have messageIds different from original
        for (int i = 1; i < totalPages; i++) {
            assertThat(deliveredEnvelopes.get(i).getMessageId())
                    .as("Page %d messageId should differ from original", i + 1)
                    .isNotEqualTo(originalMessageId);
        }

        // Verify requestId passed to routeResponse is always the original messageId
        ArgumentCaptor<String> requestIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(swm.responsesClient, times(totalPages)).routeResponse(
                any(UUID.class), anyString(), anyString(), anyString(),
                any(UUID.class), requestIdCaptor.capture(),
                any(SifMessageEnvelope.class),
                anyString());

        for (String capturedRequestId : requestIdCaptor.getAllValues()) {
            assertThat(capturedRequestId)
                    .as("requestId passed to routeResponse should always be original messageId")
                    .isEqualTo(originalMessageId.toString());
        }
    }

    // ========== Property 6: Navigation header passthrough ==========

    /**
     * Property 6: Navigation headers passed through to consumer.
     *
     * <p>For any page response from the provider containing navigation headers
     * (navigationPage, navigationLastPage, navigationId, navigationCount,
     * navigationPageSize), the delivered SifMessageEnvelope's headers map SHALL
     * contain those same navigation header key-value pairs.</p>
     *
     * <p><b>Validates: Requirements 3.4</b></p>
     */
    @Property(tries = 10)
    @Tag("Feature: paged-delayed-query, Property 6")
    void navigationHeadersPassedThroughToConsumer(
            @ForAll @IntRange(min = 1, max = 10) int totalPages,
            @ForAll("navigationIds") String navigationId,
            @ForAll @IntRange(min = 1, max = 500) int pageSize) {

        int maxPages = 1000;
        ServiceWithMocks swm = createService(maxPages);
        UUID originalMessageId = UUID.randomUUID();

        ProxyRequestMetadata metadata = buildMetadata(originalMessageId);
        SifMessageEnvelope<?> requestEnvelope = (SifMessageEnvelope<?>) metadata.getRequestEnvelope();
        Map<String, String> originalHeaders = new HashMap<>(requestEnvelope.getHeaders());

        // Build first page with specific nav headers
        Map<String, String> firstPageHeaders = new HashMap<>();
        firstPageHeaders.put("navigationPage", "1");
        firstPageHeaders.put("navigationLastPage", String.valueOf(totalPages));
        firstPageHeaders.put("navigationPageSize", String.valueOf(pageSize));
        firstPageHeaders.put("navigationId", navigationId);
        firstPageHeaders.put("navigationCount", String.valueOf(totalPages * pageSize));

        ProviderRawResponse firstPageResponse = ProviderRawResponse.builder()
                .body(Map.of("data", "page1"))
                .headers(firstPageHeaders)
                .statusCode(200)
                .build();

        // Stub subsequent pages with matching nav headers
        if (totalPages >= 2) {
            List<ProviderRawResponse> subsequentResponses = new ArrayList<>();
            for (int page = 2; page <= totalPages; page++) {
                Map<String, String> pageHeaders = new HashMap<>();
                pageHeaders.put("navigationPage", String.valueOf(page));
                pageHeaders.put("navigationLastPage", String.valueOf(totalPages));
                pageHeaders.put("navigationPageSize", String.valueOf(pageSize));
                pageHeaders.put("navigationId", navigationId);
                pageHeaders.put("navigationCount", String.valueOf(totalPages * pageSize));

                subsequentResponses.add(ProviderRawResponse.builder()
                        .body(Map.of("data", "page" + page))
                        .headers(pageHeaders)
                        .statusCode(200)
                        .build());
            }

            if (subsequentResponses.size() == 1) {
                when(swm.providerClient.sendRequestRawWithHeaders(anyString(), any(), any(), anyString()))
                        .thenReturn(subsequentResponses.get(0));
            } else {
                ProviderRawResponse first = subsequentResponses.get(0);
                ProviderRawResponse[] rest = subsequentResponses.subList(1, subsequentResponses.size())
                        .toArray(new ProviderRawResponse[0]);
                when(swm.providerClient.sendRequestRawWithHeaders(anyString(), any(), any(), anyString()))
                        .thenReturn(first, rest);
            }
        }

        // Execute paging loop
        swm.service.executePagedQuery(metadata, requestEnvelope, originalHeaders, firstPageResponse);

        // Capture delivered envelopes
        @SuppressWarnings("unchecked")
        ArgumentCaptor<SifMessageEnvelope<?>> envelopeCaptor =
                (ArgumentCaptor<SifMessageEnvelope<?>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(SifMessageEnvelope.class);

        verify(swm.responsesClient, times(totalPages)).routeResponse(any(UUID.class), anyString(), anyString(), anyString(), any(UUID.class), anyString(),
                envelopeCaptor.capture(),
                anyString());

        List<SifMessageEnvelope<?>> deliveredEnvelopes = envelopeCaptor.getAllValues();

        // Verify each delivered envelope contains correct navigation headers
        for (int i = 0; i < totalPages; i++) {
            int expectedPage = i + 1;
            Map<String, String> envHeaders = deliveredEnvelopes.get(i).getHeaders();

            assertThat(envHeaders.get("navigationPage"))
                    .as("Page %d envelope should have navigationPage=%d", expectedPage, expectedPage)
                    .isEqualTo(String.valueOf(expectedPage));

            assertThat(envHeaders.get("navigationLastPage"))
                    .as("Page %d envelope should have navigationLastPage=%d", expectedPage, totalPages)
                    .isEqualTo(String.valueOf(totalPages));

            assertThat(envHeaders.get("navigationPageSize"))
                    .as("Page %d envelope should have navigationPageSize=%d", expectedPage, pageSize)
                    .isEqualTo(String.valueOf(pageSize));

            assertThat(envHeaders.get("navigationId"))
                    .as("Page %d envelope should have navigationId=%s", expectedPage, navigationId)
                    .isEqualTo(navigationId);

            assertThat(envHeaders.get("navigationCount"))
                    .as("Page %d envelope should have navigationCount=%d", expectedPage, totalPages * pageSize)
                    .isEqualTo(String.valueOf(totalPages * pageSize));
        }
    }

    // ========== Property 7: Max page limit enforcement ==========

    /**
     * Property 7: Max page limit enforcement.
     *
     * <p>For any configured maxPages value M and provider indicating lastPage value L,
     * the paging loop SHALL deliver at most min(M, L) pages and SHALL NOT request
     * page M+1.</p>
     *
     * <p><b>Validates: Requirements 6.1, 6.2</b></p>
     */
    @Property(tries = 10)
    @Tag("Feature: paged-delayed-query, Property 7")
    void maxPageLimitEnforcement(
            @ForAll @IntRange(min = 1, max = 20) int maxPages,
            @ForAll @IntRange(min = 1, max = 50) int providerLastPage) {

        ServiceWithMocks swm = createService(maxPages);
        String navigationId = "nav-" + UUID.randomUUID();
        UUID originalMessageId = UUID.randomUUID();

        ProxyRequestMetadata metadata = buildMetadata(originalMessageId);
        SifMessageEnvelope<?> requestEnvelope = (SifMessageEnvelope<?>) metadata.getRequestEnvelope();
        Map<String, String> originalHeaders = new HashMap<>(requestEnvelope.getHeaders());

        // First page response indicates providerLastPage total pages
        ProviderRawResponse firstPageResponse = buildPageResponse(1, providerLastPage, navigationId);

        // Stub provider for all possible subsequent pages (up to providerLastPage)
        stubProviderForPages(swm.providerClient, providerLastPage, navigationId);

        // Execute paging loop
        swm.service.executePagedQuery(metadata, requestEnvelope, originalHeaders, firstPageResponse);

        int expectedDelivered = Math.min(maxPages, providerLastPage);

        // Verify routeResponse called exactly min(M, L) times
        verify(swm.responsesClient, times(expectedDelivered)).routeResponse(
                eq(metadata.getEnvironmentId()),
                eq(metadata.getEnvironmentName()),
                eq(metadata.getDomainId()),
                eq(metadata.getSolutionId()),
                eq(metadata.getConsumerQueueId()),
                anyString(),
                any(SifMessageEnvelope.class),
                eq(metadata.getRelativeServicePath()));

        // Verify provider was called the correct number of times for subsequent pages
        int expectedProviderCalls = Math.max(0, expectedDelivered - 1);
        verify(swm.providerClient, times(expectedProviderCalls))
                .sendRequestRawWithHeaders(anyString(), any(), any(), anyString());
    }

    // ========== Arbitraries ==========

    /**
     * Generates non-empty navigationId strings representing server-side cursor identifiers.
     */
    @Provide
    Arbitrary<String> navigationIds() {
        return Arbitraries.of(
                "abc-123", "nav-cursor-001", "GUID-4f3a", "session_xyz",
                "a", "12345", "cursor-special_!@#",
                "long-navigation-id-representing-real-server-cursor"
        );
    }

    // ========== Property 8: Provider-facing requestId constancy ==========

    /**
     * Property 8: requestId header sent to the provider is constant across all pages.
     *
     * <p>For a paged DELAYED→IMMEDIATE loop of N pages (N ≥ 2), the requestId header
     * in the provider-bound HTTP calls is identical on every page while navigationPage
     * increments. This guards against a future refactor silently dropping requestId
     * from the provider-facing path.</p>
     *
     * <p><b>Validates: Contract #1 (requestId constant across pages, provider side)</b></p>
     */
    @Property(tries = 10)
    @Tag("Feature: paged-delayed-query, Property 8")
    void requestIdHeaderConstantAcrossProviderCalls(
            @ForAll @IntRange(min = 2, max = 10) int totalPages) {

        int maxPages = 1000;
        ServiceWithMocks swm = createService(maxPages);
        String navigationId = "nav-" + UUID.randomUUID();
        UUID originalMessageId = UUID.randomUUID();

        ProxyRequestMetadata metadata = buildMetadata(originalMessageId);
        SifMessageEnvelope<?> requestEnvelope = (SifMessageEnvelope<?>) metadata.getRequestEnvelope();
        Map<String, String> originalHeaders = new HashMap<>(requestEnvelope.getHeaders());

        ProviderRawResponse firstPageResponse = buildPageResponse(1, totalPages, navigationId);
        stubProviderForPages(swm.providerClient, totalPages, navigationId);

        swm.service.executePagedQuery(metadata, requestEnvelope, originalHeaders, firstPageResponse);

        // Capture the headers map sent to the provider on each subsequent page call
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(swm.providerClient, times(totalPages - 1))
                .sendRequestRawWithHeaders(anyString(), any(), headersCaptor.capture(), anyString());

        String expectedRequestId = requestEnvelope.getRequestId();
        assertThat(expectedRequestId).isNotNull().isNotBlank();

        for (Map<String, String> capturedHeaders : headersCaptor.getAllValues()) {
            assertThat(capturedHeaders.get("requestId"))
                    .as("requestId header sent to provider must be constant across all pages")
                    .isEqualTo(expectedRequestId);
        }

        // Also verify the first page (sent via originalHeaders) has requestId
        assertThat(originalHeaders.get("requestId"))
                .as("First page headers must contain requestId")
                .isEqualTo(expectedRequestId);
    }

    // ========== Inner record for service + mocks ==========

    /**
     * Groups the service instance with its mocked dependencies for easy access in tests.
     */
    private record ServiceWithMocks(
            ProxyWorkerService service,
            ProviderHttpClient providerClient,
            ResponsesConnectorClient responsesClient) {
    }
}
