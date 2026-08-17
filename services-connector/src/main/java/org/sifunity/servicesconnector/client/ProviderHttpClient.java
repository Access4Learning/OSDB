// SPDX-License-Identifier: MIT
package org.sifunity.servicesconnector.client;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.servicesconnector.exception.ProviderTimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * HTTP client for forwarding functional service requests to provider endpoints.
 *
 * <p>This client supports all HTTP methods (GET, POST, PUT, DELETE) needed for
 * the various functional service operations (job CRUD, phase operations, state
 * operations). It forwards the raw {@link SifMessageEnvelope} to the provider
 * endpoint with the correct path suffix and returns the provider's response
 * unchanged.</p>
 *
 * <p>Matches the requests-connector's {@code ProviderHttpClient} pattern:
 * WebClient-based, configurable timeouts, timeout-to-exception mapping.</p>
 *
 * <p>Requirements:</p>
 * <ul>
 *   <li>2.2: Forward raw request body to provider and return response unchanged</li>
 *   <li>2.4: Forward sanitized HTTP headers to provider endpoint</li>
 *   <li>2.5: Return provider's HTTP status code and response body without transformation</li>
 *   <li>2.6: Forward phase operations to correct provider path</li>
 *   <li>2.7: Forward state operations to correct provider path</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Component
@Slf4j
public class ProviderHttpClient {

    private final WebClient webClient;
    private final Duration readTimeout;

    /**
     * Constructs a ProviderHttpClient with configurable timeouts.
     *
     * <p>The connection timeout is configured on the WebClient builder via
     * {@code WebClientConfig}. The read timeout is applied per-request via
     * Reactor's {@code timeout()} operator.</p>
     *
     * @param webClientBuilder WebClient builder configured for provider endpoints
     * @param readTimeoutMs    read timeout in milliseconds (default: 30000)
     */
    public ProviderHttpClient(
            @Qualifier("providerWebClientBuilder") WebClient.Builder webClientBuilder,
            @Value("${provider.timeout.read:30000}") long readTimeoutMs) {
        this.webClient = webClientBuilder.build();
        this.readTimeout = Duration.ofMillis(readTimeoutMs);

        log.info("ProviderHttpClient initialized with read timeout={}ms", readTimeoutMs);
    }

    /**
     * Sends a request to a provider endpoint and returns the response.
     *
     * <p>This method makes an HTTP call to the provider's endpoint using the
     * specified HTTP method. For POST and PUT, the SIF message envelope is sent
     * as the request body. For GET and DELETE, no body is sent but the envelope's
     * metadata (messageId) is used for logging/tracing.</p>
     *
     * <p>The method applies the configured read timeout. If the provider does not
     * respond within the timeout, a {@link ProviderTimeoutException} is thrown,
     * which the GlobalExceptionHandler maps to HTTP 504 Gateway Timeout.</p>
     *
     * @param providerEndpoint   the full URL of the provider's endpoint (base + path suffix)
     * @param httpMethod         the HTTP method to use (GET, POST, PUT, DELETE)
     * @param envelope           the SIF message envelope containing the request data
     * @param passthroughHeaders sanitized headers to forward to the provider
     * @return the provider's response body as an Object (returned unchanged)
     * @throws WebClientRequestException  if connection fails or times out
     * @throws WebClientResponseException if provider returns an HTTP error response (4xx or 5xx)
     * @throws ProviderTimeoutException   if the read timeout is exceeded
     */
    public Object sendRequest(
            String providerEndpoint,
            HttpMethod httpMethod,
            SifMessageEnvelope<?> envelope,
            Map<String, String> passthroughHeaders) {

        log.debug("Sending request to provider: method={}, endpoint={}, messageId={}, passthroughHeaderCount={}",
                httpMethod, providerEndpoint, envelope.getMessageId(),
                passthroughHeaders != null ? passthroughHeaders.size() : 0);

        long startTime = System.currentTimeMillis();
        Map<String, String> headers = passthroughHeaders != null ? passthroughHeaders : Collections.emptyMap();

        try {
            Object response;

            if (httpMethod == HttpMethod.GET || httpMethod == HttpMethod.DELETE) {
                // GET and DELETE typically have no body
                response = webClient.method(httpMethod)
                        .uri(providerEndpoint)
                        .headers(h -> headers.forEach(h::set))
                        .retrieve()
                        .bodyToMono(Object.class)
                        .timeout(readTimeout)
                        .onErrorMap(this::mapTimeoutException)
                        .block();
            } else {
                // POST, PUT — send the envelope as the body
                response = webClient.method(httpMethod)
                        .uri(providerEndpoint)
                        .headers(h -> headers.forEach(h::set))
                        .bodyValue(envelope)
                        .retrieve()
                        .bodyToMono(Object.class)
                        .timeout(readTimeout)
                        .onErrorMap(this::mapTimeoutException)
                        .block();
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Received response from provider: method={}, endpoint={}, messageId={}, duration={}ms",
                    httpMethod, providerEndpoint, envelope.getMessageId(), duration);

            return response;

        } catch (ProviderTimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Provider timed out: method={}, endpoint={}, messageId={}, duration={}ms",
                    httpMethod, providerEndpoint, envelope.getMessageId(), duration);
            throw e;

        } catch (WebClientResponseException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("Provider returned error: method={}, endpoint={}, messageId={}, status={}, duration={}ms",
                    httpMethod, providerEndpoint, envelope.getMessageId(), e.getStatusCode(), duration);
            throw e;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Provider call failed: method={}, endpoint={}, messageId={}, duration={}ms",
                    httpMethod, providerEndpoint, envelope.getMessageId(), duration, e);
            throw e;
        }
    }

    /**
     * Maps timeout exceptions to ProviderTimeoutException.
     *
     * <p>Converts generic timeout exceptions into {@link ProviderTimeoutException}
     * with a clear message. This ensures timeout errors are handled correctly
     * by the GlobalExceptionHandler (returning HTTP 504 Gateway Timeout).</p>
     *
     * @param throwable the original exception
     * @return the mapped exception (or original if not a timeout)
     */
    private Throwable mapTimeoutException(Throwable throwable) {
        if (throwable instanceof java.util.concurrent.TimeoutException) {
            return new ProviderTimeoutException(
                    "Provider request timed out after " + readTimeout.toMillis() + "ms",
                    throwable);
        }
        return throwable;
    }


}
