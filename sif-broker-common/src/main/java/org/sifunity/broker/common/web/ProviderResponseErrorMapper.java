// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.web;

import org.springframework.core.io.buffer.DataBufferLimitException;

/**
 * Shared decision logic for mapping failures that occur while a broker service
 * reads or decodes an upstream (provider or service) HTTP response into a
 * consistent, client-facing error.
 *
 * <p>This centralizes the behavior that was previously duplicated (and buggy)
 * in each connector's exception handler:</p>
 * <ul>
 *   <li>A {@code WebClientResponseException} carrying a <em>non-error</em> status
 *       (2xx/3xx) means the upstream responded OK but the body could not be read
 *       or decoded (most commonly because it exceeded the in-memory buffer
 *       limit). This must surface as a {@code 502 Bad Gateway}, not a misleading
 *       echo of the upstream 200 with an empty body.</li>
 *   <li>A {@link DataBufferLimitException} (possibly nested in the cause chain)
 *       indicates the response exceeded the configured buffer limit.</li>
 * </ul>
 *
 * <p>This class is intentionally free of any spring-webflux dependency
 * ({@link DataBufferLimitException} is spring-core) so it can live in
 * {@code sif-broker-common}. Callers pass the upstream status code as an
 * {@code int} and the thrown {@link Throwable}; the per-service exception
 * handler remains responsible for catching the webflux exception types and
 * building the HTTP response.</p>
 */
public final class ProviderResponseErrorMapper {

    /** Error code when an OK upstream response could not be read/decoded. */
    public static final String UNPROCESSABLE_CODE = "PROVIDER_RESPONSE_UNPROCESSABLE";

    /** Error code when the upstream response exceeded the broker's size limit. */
    public static final String TOO_LARGE_CODE = "PROVIDER_RESPONSE_TOO_LARGE";

    /** HTTP status returned for both processing-failure cases. */
    public static final int BAD_GATEWAY = 502;

    private ProviderResponseErrorMapper() {
        // Utility class — prevent instantiation
    }

    /**
     * Determines whether an HTTP status code represents a successful (non-error)
     * upstream response. A {@code WebClientResponseException} with such a status
     * indicates a body read/decode failure rather than a true provider error
     * status.
     *
     * @param statusCode the upstream HTTP status code
     * @return {@code true} if the status is 1xx/2xx/3xx (not an error status)
     */
    public static boolean isNonErrorStatus(int statusCode) {
        return statusCode < 400;
    }

    /**
     * Walks the cause chain to determine whether the failure was caused by the
     * response exceeding the in-memory buffer limit.
     *
     * @param throwable the thrown exception (may be {@code null})
     * @return {@code true} if a {@link DataBufferLimitException} is found
     */
    public static boolean isResponseTooLarge(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataBufferLimitException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the SIF error code to use for a body processing failure, based on
     * whether the failure was a size/buffer overflow.
     *
     * @param throwable the thrown exception
     * @return {@link #TOO_LARGE_CODE} or {@link #UNPROCESSABLE_CODE}
     */
    public static String codeForProcessingFailure(Throwable throwable) {
        return isResponseTooLarge(throwable) ? TOO_LARGE_CODE : UNPROCESSABLE_CODE;
    }

    /**
     * Builds a descriptive, actionable message for a body read/decode failure.
     *
     * @param throwable the thrown exception
     * @return a human-readable message
     */
    public static String messageForProcessingFailure(Throwable throwable) {
        if (isResponseTooLarge(throwable)) {
            return "The provider response exceeded the maximum size the broker can process. "
                    + "Reduce the result size (e.g. lower navigationPageSize) and retry.";
        }
        return "The provider returned a response that the broker could not read or decode.";
    }
}
