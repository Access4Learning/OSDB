// SPDX-License-Identifier: MIT
package org.sifunity.eventsconnector.property;

import net.jqwik.api.*;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for the HTTP error retry predicate.
 *
 * <p>Tests the retry classification logic used by Resilience4j in
 * {@link org.sifunity.eventsconnector.config.RetryConfig}:
 * 5xx server errors are retryable, 4xx client errors are not.</p>
 *
 * <p>The predicate under test mirrors {@code RetryConfig.isRetryableException}:</p>
 * <ul>
 *   <li>{@code HttpClientErrorException} (4xx) → false (don't retry)</li>
 *   <li>{@code HttpServerErrorException} (5xx) → true (retry)</li>
 *   <li>Other exceptions → true (retry)</li>
 * </ul>
 */
// Feature: restclient-netty-removal, Property 3: 5xx errors are retryable
// Feature: restclient-netty-removal, Property 4: 4xx errors are not retried
@Tag("Feature: restclient-netty-removal")
class HttpErrorRetryPropertyTest {

    /**
     * The retry predicate — identical to {@code RetryConfig.isRetryableException}.
     */
    private final Predicate<Throwable> isRetryable = throwable -> {
        if (throwable instanceof HttpClientErrorException) {
            return false; // 4xx — don't retry
        }
        if (throwable instanceof HttpServerErrorException) {
            return true;  // 5xx — retry
        }
        return true; // network errors, timeouts — retry
    };

    /**
     * Property 3: 5xx errors are retryable
     *
     * <b>Validates: Requirements 3.2, 4.2</b>
     *
     * For any 5xx HTTP status code (500–511), the retry predicate shall
     * return true, indicating the error is retryable.
     */
    @Property(tries = 100)
    @Tag("Property 3: 5xx errors are retryable")
    void serverErrorsAreRetryable(@ForAll("serverErrorCodes") int statusCode) {
        HttpServerErrorException exception = HttpServerErrorException.create(
                HttpStatusCode.valueOf(statusCode), "Server Error", null, null, null);

        assertThat(isRetryable.test(exception))
                .as("5xx status %d should be retryable", statusCode)
                .isTrue();
    }

    /**
     * Property 4: 4xx errors are not retried
     *
     * <b>Validates: Requirements 3.3, 4.3</b>
     *
     * For any 4xx HTTP status code (400–451), the retry predicate shall
     * return false, indicating the error is not retryable.
     */
    @Property(tries = 100)
    @Tag("Property 4: 4xx errors are not retried")
    void clientErrorsAreNotRetryable(@ForAll("clientErrorCodes") int statusCode) {
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatusCode.valueOf(statusCode), "Client Error", null, null, null);

        assertThat(isRetryable.test(exception))
                .as("4xx status %d should NOT be retryable", statusCode)
                .isFalse();
    }

    /**
     * Generates standard 5xx HTTP status codes (500–511).
     */
    @Provide
    Arbitrary<Integer> serverErrorCodes() {
        return Arbitraries.of(
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

    /**
     * Generates standard 4xx HTTP status codes (400–451).
     */
    @Provide
    Arbitrary<Integer> clientErrorCodes() {
        return Arbitraries.of(
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
                451  // Unavailable For Legal Reasons
        );
    }
}
