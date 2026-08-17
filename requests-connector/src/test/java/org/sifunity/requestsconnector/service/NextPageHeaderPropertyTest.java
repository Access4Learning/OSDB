// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.sifunity.requestsconnector.client.ProviderHttpClient;
import org.sifunity.requestsconnector.client.ResponsesConnectorClient;
import org.sifunity.requestsconnector.config.ProxyStreamConfig;
import org.sifunity.requestsconnector.dto.ProviderRawResponse;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Property-based tests for next-page header construction in ProxyWorkerService.
 *
 * <p>Validates Property 2: header construction preserves navigation state, and
 * Property 3: queryIntention inclusion follows the final-page rule.</p>
 *
 * <p>This test class is in the {@code service} package to access the package-private
 * {@code buildNextPageHeaders} method directly.</p>
 */
class NextPageHeaderPropertyTest {

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
     * Property 2: Next-page header construction preserves navigation state.
     *
     * <p>For any valid navigation state (navigationPage in 1..999, navigationId as
     * non-empty string, navigationPageSize as positive integer),
     * {@code buildNextPageHeaders()} SHALL produce headers where:
     * navigationPage equals the requested next page number,
     * navigationId matches the value from the previous provider response,
     * and navigationPageSize matches the original request value.</p>
     *
     * <p><b>Validates: Requirements 2.1, 2.2, 2.3</b></p>
     */
    @Property(tries = 20)
    @Tag("Feature: paged-delayed-query, Property 2")
    void headerConstructionPreservesNavigationState(
            @ForAll @IntRange(min = 1, max = 999) int nextPage,
            @ForAll("navigationIds") String navigationId,
            @ForAll @IntRange(min = 1, max = 10000) int pageSize,
            @ForAll @IntRange(min = 2, max = 1000) int lastPage) {

        ProxyWorkerService service = createService();

        // Build original headers with navigationPageSize
        Map<String, String> originalHeaders = new HashMap<>();
        originalHeaders.put("navigationPageSize", String.valueOf(pageSize));
        originalHeaders.put("queryIntention", "ALL");

        // Build previous response with navigationId
        Map<String, String> prevResponseHeaders = new HashMap<>();
        prevResponseHeaders.put("navigationId", navigationId);
        prevResponseHeaders.put("navigationPage", String.valueOf(nextPage - 1));

        ProviderRawResponse previousResponse = ProviderRawResponse.builder()
                .body(Map.of("data", "test"))
                .headers(prevResponseHeaders)
                .statusCode(200)
                .build();

        // Act
        Map<String, String> result = service.buildNextPageHeaders(
                originalHeaders, previousResponse, nextPage, lastPage);

        // Assert Property 2 conditions:
        // 2.1 navigationPage equals the requested next page number
        assertThat(result.get("navigationPage"))
                .as("navigationPage should equal requested next page %d", nextPage)
                .isEqualTo(String.valueOf(nextPage));

        // 2.2 navigationId matches the value from the previous provider response
        assertThat(result.get("navigationId"))
                .as("navigationId should match previous response value '%s'", navigationId)
                .isEqualTo(navigationId);

        // 2.3 navigationPageSize matches the original request value
        assertThat(result.get("navigationPageSize"))
                .as("navigationPageSize should match original request value %d", pageSize)
                .isEqualTo(String.valueOf(pageSize));
    }

    /**
     * Property 3: queryIntention inclusion follows final-page rule.
     *
     * <p>For any page request where the requested page number is less than
     * navigationLastPage, {@code buildNextPageHeaders()} SHALL include
     * {@code queryIntention: ALL}. For any page request where the requested
     * page number equals navigationLastPage, {@code buildNextPageHeaders()}
     * SHALL omit the queryIntention header.</p>
     *
     * <p><b>Validates: Requirements 2.4</b></p>
     */
    @Property(tries = 20)
    @Tag("Feature: paged-delayed-query, Property 3")
    void queryIntentionFollowsFinalPageRule(
            @ForAll @IntRange(min = 1, max = 999) int nextPage,
            @ForAll("navigationIds") String navigationId,
            @ForAll @IntRange(min = 1, max = 10000) int pageSize) {

        ProxyWorkerService service = createService();

        // Ensure lastPage >= nextPage so we can test both cases
        // We test two scenarios: nextPage < lastPage, and nextPage == lastPage

        Map<String, String> originalHeaders = new HashMap<>();
        originalHeaders.put("navigationPageSize", String.valueOf(pageSize));
        originalHeaders.put("queryIntention", "ALL");

        Map<String, String> prevResponseHeaders = new HashMap<>();
        prevResponseHeaders.put("navigationId", navigationId);

        ProviderRawResponse previousResponse = ProviderRawResponse.builder()
                .body(Map.of("data", "test"))
                .headers(prevResponseHeaders)
                .statusCode(200)
                .build();

        // Case A: nextPage < lastPage → queryIntention should be "ALL"
        int lastPageGreater = nextPage + 1; // guarantees nextPage < lastPage
        Map<String, String> resultNonFinal = service.buildNextPageHeaders(
                originalHeaders, previousResponse, nextPage, lastPageGreater);

        assertThat(resultNonFinal.get("queryIntention"))
                .as("queryIntention should be 'ALL' when nextPage(%d) < lastPage(%d)",
                        nextPage, lastPageGreater)
                .isEqualTo("ALL");

        // Case B: nextPage == lastPage → queryIntention should be absent
        int lastPageEqual = nextPage; // guarantees nextPage == lastPage
        Map<String, String> resultFinal = service.buildNextPageHeaders(
                originalHeaders, previousResponse, nextPage, lastPageEqual);

        assertThat(resultFinal.get("queryIntention"))
                .as("queryIntention should be absent when nextPage(%d) == lastPage(%d)",
                        nextPage, lastPageEqual)
                .isNull();
    }

    // ========== Arbitraries ==========

    /**
     * Generates non-empty navigationId strings representing server-side cursor identifiers.
     */
    @Provide
    Arbitrary<String> navigationIds() {
        return Arbitraries.of(
                "abc-123", "nav-cursor-001", "GUID-4f3a", "session_xyz",
                "a", "12345", "cursor-with-special-chars_!@#",
                "very-long-navigation-id-that-represents-a-real-server-cursor-value"
        );
    }
}
