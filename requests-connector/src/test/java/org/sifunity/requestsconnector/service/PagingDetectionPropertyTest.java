// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Property-based tests for paging detection logic in ProxyWorkerService.
 *
 * <p>Validates Property 1: for any combination of HTTP method, queryIntention header,
 * response status code, navigationPage, and navigationLastPage values,
 * {@code isPagingRequired()} returns true if and only if ALL five conditions are met.</p>
 *
 * <p>This test class is in the {@code service} package to access the package-private
 * {@code isPagingRequired} method directly, as documented in the design.</p>
 *
 * <p><b>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5</b></p>
 */
class PagingDetectionPropertyTest {

    private ProxyWorkerService createService() {
        return new ProxyWorkerService(
                mock(StringRedisTemplate.class),
                new ObjectMapper(),
                mock(ProviderHttpClient.class),
                mock(ResponsesConnectorClient.class),
                mock(org.sifunity.broker.common.metrics.ConnectorMetricsService.class),
                new org.sifunity.broker.common.util.NoOpTracingUtils(),
                java.util.List.of(),
                new ProxyStreamConfig(120000, 3),
                3, 1000);
    }

    /**
     * Property 1: Paging detection is correct.
     *
     * <p>For any combination of HTTP method, queryIntention header value, response status code,
     * navigationPage value, and navigationLastPage value, {@code isPagingRequired()} SHALL return
     * true if and only if: method is GET, queryIntention is "ALL", status is 200, both
     * navigationPage and navigationLastPage are present, and navigationPage != navigationLastPage.</p>
     *
     * <p><b>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5</b></p>
     */
    @Property(tries = 20)
    @Tag("Feature: paged-delayed-query, Property 1: Paging detection is correct")
    void pagingRequiredIffAllFiveConditionsMet(
            @ForAll("httpMethods") String httpMethod,
            @ForAll("queryIntentions") String queryIntention,
            @ForAll("statusCodes") int statusCode,
            @ForAll("navigationValues") String navigationPage,
            @ForAll("navigationValues") String navigationLastPage) {

        ProxyWorkerService service = createService();

        // Build request envelope headers with queryIntention
        Map<String, String> requestHeaders = new HashMap<>();
        if (queryIntention != null) {
            requestHeaders.put("queryIntention", queryIntention);
        }

        SifMessageEnvelope<Object> envelope = SifMessageEnvelope.<Object>builder()
                .messageType(MessageType.REQUEST)
                .serviceName("TestService")
                .serviceType(ServiceType.OBJECT)
                .headers(requestHeaders)
                .build();

        ProxyRequestMetadata metadata = ProxyRequestMetadata.builder()
                .messageId(UUID.randomUUID())
                .consumerQueueId(UUID.randomUUID())
                .environmentId(UUID.randomUUID())
                .environmentName("test-env")
                .domainId("test-domain")
                .solutionId("test-solution")
                .httpMethod(httpMethod)
                .relativeServicePath("TestService")
                .providerEndpoint("https://provider.example.com/test")
                .requestEnvelope(envelope)
                .createdAt(Instant.now())
                .retryCount(0)
                .build();

        // Build response headers
        Map<String, String> responseHeaders = new HashMap<>();
        if (navigationPage != null) {
            responseHeaders.put("navigationPage", navigationPage);
        }
        if (navigationLastPage != null) {
            responseHeaders.put("navigationLastPage", navigationLastPage);
        }

        ProviderRawResponse response = ProviderRawResponse.builder()
                .body(Map.of("data", "test"))
                .headers(responseHeaders)
                .statusCode(statusCode)
                .build();

        // Compute expected result from the five conditions
        boolean isGet = "GET".equalsIgnoreCase(httpMethod);
        boolean isQueryAll = "ALL".equals(queryIntention);
        boolean isStatus200 = statusCode == 200;
        boolean hasNavPage = navigationPage != null;
        boolean hasNavLastPage = navigationLastPage != null;
        boolean navPagesDiffer = hasNavPage && hasNavLastPage
                && !navigationPage.equals(navigationLastPage);

        boolean expected = isGet && isQueryAll && isStatus200
                && hasNavPage && hasNavLastPage && navPagesDiffer;

        // Direct call to package-private method
        boolean actual = service.isPagingRequired(metadata, response);

        assertThat(actual)
                .as("isPagingRequired(method=%s, queryIntention=%s, status=%d, navPage=%s, navLastPage=%s)",
                        httpMethod, queryIntention, statusCode, navigationPage, navigationLastPage)
                .isEqualTo(expected);
    }

    // ========== Arbitraries ==========

    /**
     * Generates HTTP methods: GET (positive case, multiple casings) plus POST, PUT, DELETE.
     */
    @Provide
    Arbitrary<String> httpMethods() {
        return Arbitraries.of("GET", "get", "Get", "POST", "PUT", "DELETE");
    }

    /**
     * Generates queryIntention values: "ALL" (positive case), plus "ONE-OFF", null, and others.
     */
    @Provide
    Arbitrary<String> queryIntentions() {
        return Arbitraries.of("ALL", "ONE-OFF", "NO-CACHING", null, "all", "SOME", "");
    }

    /**
     * Generates HTTP status codes: 200 (positive case) plus common error codes.
     */
    @Provide
    Arbitrary<Integer> statusCodes() {
        return Arbitraries.of(200, 201, 204, 400, 401, 403, 404, 500, 502, 503);
    }

    /**
     * Generates navigation header values: null (absent), plus various numeric strings.
     * Includes matching pairs and differing pairs to exercise condition 5.
     */
    @Provide
    Arbitrary<String> navigationValues() {
        return Arbitraries.of(null, "1", "2", "3", "5", "10", "100");
    }
}
