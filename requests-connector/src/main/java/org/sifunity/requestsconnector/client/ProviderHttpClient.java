// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.client;

import lombok.extern.slf4j.Slf4j;
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.requestsconnector.dto.ProviderRawResponse;
import org.sifunity.requestsconnector.exception.ProviderTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP client for making synchronous calls to provider endpoints.
 * Provides methods for sending requests to providers with configurable timeouts.
 * 
 * <p>This client is responsible for making HTTP calls to provider endpoints in both
 * the IMMEDIATE pattern (synchronous request/response) and the DELAYED_to_IMMEDIATE
 * pattern (background worker making synchronous calls on behalf of consumers).</p>
 * 
 * <p>The client applies configurable connection and read timeouts to prevent slow
 * providers from blocking the connector. Timeout exceptions are propagated to the
 * caller for appropriate error handling.</p>
 * 
 * <p>Requirements:</p>
 * <ul>
 *   <li>1.2: Forward request to provider's endpoint using HTTP</li>
 *   <li>11.1: Apply configurable connection timeout to provider calls</li>
 *   <li>11.2: Apply configurable read timeout to provider calls</li>
 *   <li>11.3: Return HTTP 504 Gateway Timeout when provider call times out</li>
 * </ul>
 */
@Component
@Slf4j
public class ProviderHttpClient {
    
    private final WebClient webClient;
    private final Duration connectionTimeout;
    private final Duration readTimeout;
    
    /**
     * Constructs a ProviderHttpClient with configurable timeouts.
     * 
     * <p>The connection timeout controls how long to wait for a connection to be established
     * with the provider. The read timeout controls how long to wait for the provider to
     * send a response after the connection is established.</p>
     * 
     * <p>Default timeouts are 5 seconds for connection and 30 seconds for read if not
     * configured in application.yml.</p>
     * 
     * @param webClientBuilder WebClient builder for creating the client
     * @param connectionTimeoutMs connection timeout in milliseconds (default: 5000)
     * @param readTimeoutMs read timeout in milliseconds (default: 30000)
     */
    public ProviderHttpClient(
            WebClient.Builder webClientBuilder,
            @Value("${provider.timeout.connection:5000}") long connectionTimeoutMs,
            @Value("${provider.timeout.read:30000}") long readTimeoutMs) {
        this.webClient = webClientBuilder.build();
        this.connectionTimeout = Duration.ofMillis(connectionTimeoutMs);
        this.readTimeout = Duration.ofMillis(readTimeoutMs);
        
        log.info("ProviderHttpClient initialized with timeouts: connection={}ms, read={}ms", 
            connectionTimeoutMs, readTimeoutMs);
    }
    
    /**
     * Sends a request to a provider endpoint and returns the response.
     * 
     * <p>This method makes a synchronous HTTP POST call to the provider's endpoint,
     * sending the SIF message envelope as the request body. The provider is expected
     * to return a SIF message envelope as the response.</p>
     * 
     * <p>The method applies the configured connection and read timeouts. If the provider
     * does not respond within the read timeout, a timeout exception is thrown which
     * should be handled by the caller (typically resulting in HTTP 504 Gateway Timeout).</p>
     * 
     * <p>This method is used in two scenarios:</p>
     * <ul>
     *   <li>IMMEDIATE pattern: Called directly from ImmediateRequestHandler</li>
     *   <li>DELAYED_to_IMMEDIATE pattern: Called by ProxyWorkerService background worker</li>
     * </ul>
     * 
     * @param providerEndpoint the full URL of the provider's endpoint
     * @param request the SIF message envelope containing the request data
     * @return the SIF message envelope containing the provider's response
     * @throws org.springframework.web.reactive.function.client.WebClientRequestException 
     *         if connection fails or times out
     * @throws org.springframework.web.reactive.function.client.WebClientResponseException 
     *         if provider returns an error response (4xx or 5xx)
     * @throws reactor.core.Exceptions.ReactiveException wrapping TimeoutException
     *         if the read timeout is exceeded
     */
    public SifMessageEnvelope<?> sendRequest(
            String providerEndpoint, 
            SifMessageEnvelope<?> request,
            Map<String, String> passthroughHeaders) {
        log.debug("Sending request to provider: endpoint={}, messageId={}, passthroughHeaderCount={}", 
            providerEndpoint, request.getMessageId(), 
            passthroughHeaders != null ? passthroughHeaders.size() : 0);
        
        long startTime = System.currentTimeMillis();
        
        Map<String, String> headers = passthroughHeaders != null ? passthroughHeaders : Collections.emptyMap();
        
        try {
            SifMessageEnvelope<?> response = webClient.post()
                .uri(providerEndpoint)
                .headers(h -> headers.forEach(h::set))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(SifMessageEnvelope.class)
                .timeout(readTimeout)
                .onErrorMap(this::mapTimeoutException)
                .block();
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Received response from provider: endpoint={}, messageId={}, duration={}ms", 
                providerEndpoint, request.getMessageId(), duration);
            
            return response;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Provider call failed: endpoint={}, messageId={}, duration={}ms", 
                providerEndpoint, request.getMessageId(), duration, e);
            throw e;
        }
    }
    
    /**
     * Sends a request to a provider endpoint and returns the raw response as Object.
     * Used for transparent pass-through where the provider's response format is unknown.
     */
    public Object sendRequestRaw(
            String providerEndpoint,
            Object requestBody,
            Map<String, String> passthroughHeaders,
            String httpMethod) {
        log.debug("Sending raw {} request to provider: endpoint={}, passthroughHeaderCount={}",
            httpMethod, providerEndpoint, passthroughHeaders != null ? passthroughHeaders.size() : 0);
        
        long startTime = System.currentTimeMillis();
        Map<String, String> headers = passthroughHeaders != null ? passthroughHeaders : Collections.emptyMap();
        
        org.springframework.http.HttpMethod method = org.springframework.http.HttpMethod.valueOf(
            httpMethod != null ? httpMethod.toUpperCase() : "POST");
        
        try {
            var spec = webClient.method(method)
                .uri(providerEndpoint)
                .headers(h -> headers.forEach(h::set));
            
            var headersSpec = (requestBody != null && (method == org.springframework.http.HttpMethod.POST || method == org.springframework.http.HttpMethod.PUT))
                ? spec.bodyValue(requestBody) : spec;
            
            Object response = headersSpec
                .retrieve()
                .bodyToMono(Object.class)
                .timeout(readTimeout)
                .onErrorMap(this::mapTimeoutException)
                .block();
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Received raw response from provider: endpoint={}, duration={}ms",
                providerEndpoint, duration);
            
            return response;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Provider raw call failed: endpoint={}, duration={}ms",
                providerEndpoint, duration, e);
            throw e;
        }
    }
    
    /**
     * Sends a request to a provider endpoint and returns the full response including
     * body, headers, and status code as a {@link ProviderRawResponse}.
     * Used by the paging loop to inspect navigation headers from the provider.
     *
     * @param providerEndpoint the full URL of the provider's endpoint
     * @param requestBody the request body (may be null for GET/DELETE)
     * @param passthroughHeaders headers to forward to the provider
     * @param httpMethod the HTTP method to use (GET, POST, PUT, DELETE)
     * @return a ProviderRawResponse containing body, headers, and status code
     * @throws ProviderTimeoutException if the read timeout is exceeded
     */
    public ProviderRawResponse sendRequestRawWithHeaders(
            String providerEndpoint,
            Object requestBody,
            Map<String, String> passthroughHeaders,
            String httpMethod) {
        log.debug("Sending raw {} request with header capture to provider: endpoint={}, passthroughHeaderCount={}",
            httpMethod, providerEndpoint, passthroughHeaders != null ? passthroughHeaders.size() : 0);

        long startTime = System.currentTimeMillis();
        Map<String, String> headers = passthroughHeaders != null ? passthroughHeaders : Collections.emptyMap();

        org.springframework.http.HttpMethod method = org.springframework.http.HttpMethod.valueOf(
            httpMethod != null ? httpMethod.toUpperCase() : "POST");

        try {
            var spec = webClient.method(method)
                .uri(providerEndpoint)
                .headers(h -> headers.forEach(h::set));

            var headersSpec = (requestBody != null && (method == org.springframework.http.HttpMethod.POST || method == org.springframework.http.HttpMethod.PUT))
                ? spec.bodyValue(requestBody) : spec;

            var responseEntity = headersSpec
                .retrieve()
                .toEntity(Object.class)
                .timeout(readTimeout)
                .onErrorMap(this::mapTimeoutException)
                .block();

            long duration = System.currentTimeMillis() - startTime;

            Map<String, String> responseHeaders = new LinkedHashMap<>();
            if (responseEntity != null && responseEntity.getHeaders() != null) {
                responseEntity.getHeaders().forEach((key, values) -> {
                    if (values != null && !values.isEmpty()) {
                        responseHeaders.put(key, values.get(0));
                    }
                });
            }

            Object body = responseEntity != null ? responseEntity.getBody() : null;
            int statusCode = responseEntity != null ? responseEntity.getStatusCode().value() : 0;

            log.info("Received raw response with headers from provider: endpoint={}, statusCode={}, headerCount={}, duration={}ms",
                providerEndpoint, statusCode, responseHeaders.size(), duration);

            return ProviderRawResponse.builder()
                .body(body)
                .headers(responseHeaders)
                .statusCode(statusCode)
                .build();

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Provider raw call with headers failed: endpoint={}, duration={}ms",
                providerEndpoint, duration, e);
            throw e;
        }
    }

    /**
     * Maps timeout exceptions to ProviderTimeoutException.
     * 
     * <p>This method converts generic timeout exceptions into ProviderTimeoutException
     * with a clear message indicating that the provider timed out. This helps distinguish
     * timeout errors from other types of errors and ensures they are handled correctly
     * by the GlobalExceptionHandler (returning HTTP 504 Gateway Timeout).</p>
     * 
     * @param throwable the original exception
     * @return the mapped exception (or original if not a timeout)
     */
    private Throwable mapTimeoutException(Throwable throwable) {
        if (throwable instanceof java.util.concurrent.TimeoutException) {
            return new ProviderTimeoutException(
                "Provider request timed out after " + readTimeout.toMillis() + "ms", 
                throwable
            );
        }
        return throwable;
    }
}
