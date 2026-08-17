// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.property;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.sifunity.eventsconnector.health.QueueServiceHealthIndicator;
import org.sifunity.eventsconnector.health.SubscriptionServiceHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for health indicator behavior on non-2xx responses.
 *
 * <p>Validates that for any non-2xx HTTP status code (4xx, 5xx) returned by the
 * health endpoint, both {@link QueueServiceHealthIndicator} and
 * {@link SubscriptionServiceHealthIndicator} return {@code Health.down()} with
 * {@code "service"} and {@code "error"} details.</p>
 *
 * <p>Note: 3xx status codes are excluded because RestClient's default error handler
 * only throws exceptions for 4xx ({@code HttpClientErrorException}) and 5xx
 * ({@code HttpServerErrorException}). 3xx responses are handled as redirects or
 * returned as successful responses, so the health indicator's catch block is not
 * triggered. This matches the task description: "For 3xx, it depends on redirect
 * handling."</p>
 */
// Feature: restclient-netty-removal, Property 6: Health indicator reports down on non-2xx
@Tag("Feature: restclient-netty-removal")
class HealthIndicatorPropertyTest {

    private MockWebServer mockWebServer;
    private QueueServiceHealthIndicator queueHealthIndicator;
    private SubscriptionServiceHealthIndicator subscriptionHealthIndicator;

    @BeforeProperty
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();

        // Use NEVER redirect policy so 3xx responses are not silently followed
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);

        RestClient healthCheckRestClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .build();

        queueHealthIndicator = new QueueServiceHealthIndicator(healthCheckRestClient);
        ReflectionTestUtils.setField(queueHealthIndicator, "queueServiceUrl", "");

        subscriptionHealthIndicator = new SubscriptionServiceHealthIndicator(healthCheckRestClient);
        ReflectionTestUtils.setField(subscriptionHealthIndicator, "subscriptionServiceUrl", "");
    }

    @AfterProperty
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    /**
     * Property 6: Health indicator reports down on non-2xx (QueueServiceHealthIndicator)
     *
     * <b>Validates: Requirements 6.3</b>
     *
     * For any 4xx or 5xx HTTP status code returned by the health endpoint,
     * QueueServiceHealthIndicator shall return Health.down() with a "service"
     * detail and an "error" detail.
     */
    @Property(tries = 100)
    @Tag("Property 6: Health indicator reports down on non-2xx")
    void queueHealthIndicatorReportsDownOnNon2xx(@ForAll("non2xxStatusCodes") int statusCode) {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(statusCode)
                .setBody("error"));

        Health health = queueHealthIndicator.health();

        assertThat(health.getStatus())
                .as("Queue health should be DOWN for status %d", statusCode)
                .isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("service");
        assertThat(health.getDetails().get("service")).isEqualTo("queue-service");
        assertThat(health.getDetails()).containsKey("error");
    }

    /**
     * Property 6: Health indicator reports down on non-2xx (SubscriptionServiceHealthIndicator)
     *
     * <b>Validates: Requirements 6.4</b>
     *
     * For any 4xx or 5xx HTTP status code returned by the health endpoint,
     * SubscriptionServiceHealthIndicator shall return Health.down() with a "service"
     * detail and an "error" detail.
     */
    @Property(tries = 100)
    @Tag("Property 6: Health indicator reports down on non-2xx")
    void subscriptionHealthIndicatorReportsDownOnNon2xx(@ForAll("non2xxStatusCodes") int statusCode) {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(statusCode)
                .setBody("error"));

        Health health = subscriptionHealthIndicator.health();

        assertThat(health.getStatus())
                .as("Subscription health should be DOWN for status %d", statusCode)
                .isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("service");
        assertThat(health.getDetails().get("service")).isEqualTo("subscription-service");
        assertThat(health.getDetails()).containsKey("error");
    }

    /**
     * Generates non-2xx HTTP status codes from standard 4xx and 5xx ranges.
     *
     * <p>3xx codes are included where they cause exceptions (300, 304 with
     * NEVER redirect policy still don't throw in RestClient's default handler,
     * but 301/302/303/307/308 with NEVER policy also don't throw).
     * Only 4xx and 5xx codes reliably trigger RestClient's default error handler.</p>
     */
    @Provide
    Arbitrary<Integer> non2xxStatusCodes() {
        return Arbitraries.of(
                // 4xx Client Errors
                400, // Bad Request
                401, // Unauthorized
                402, // Payment Required
                403, // Forbidden
                404, // Not Found
                405, // Method Not Allowed
                406, // Not Acceptable
                407, // Proxy Authentication Required
                408, // Request Timeout
                409, // Conflict
                410, // Gone
                411, // Length Required
                412, // Precondition Failed
                413, // Payload Too Large
                414, // URI Too Long
                415, // Unsupported Media Type
                416, // Range Not Satisfiable
                417, // Expectation Failed
                418, // I'm a Teapot
                421, // Misdirected Request
                422, // Unprocessable Entity
                423, // Locked
                424, // Failed Dependency
                425, // Too Early
                426, // Upgrade Required
                428, // Precondition Required
                429, // Too Many Requests
                431, // Request Header Fields Too Large
                451, // Unavailable For Legal Reasons
                // 5xx Server Errors
                500, // Internal Server Error
                501, // Not Implemented
                502, // Bad Gateway
                503, // Service Unavailable
                504, // Gateway Timeout
                505, // HTTP Version Not Supported
                506, // Variant Also Negotiates
                507, // Insufficient Storage
                508, // Loop Detected
                510, // Not Extended
                511  // Network Authentication Required
        );
    }
}
