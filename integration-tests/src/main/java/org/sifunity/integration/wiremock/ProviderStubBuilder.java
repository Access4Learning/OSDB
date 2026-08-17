// SPDX-License-Identifier: MIT
package org.sifunity.integration.wiremock;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import lombok.extern.slf4j.Slf4j;

/**
 * Builder for creating WireMock stubs that simulate Provider behavior.
 * 
 * <p>This builder provides a fluent API for configuring Provider stubs for different
 * message exchange patterns:
 * <ul>
 *   <li>IMMEDIATE: Synchronous request/response</li>
 *   <li>DELAYED: Asynchronous request/response via queues</li>
 *   <li>EVENT: Asynchronous event publication</li>
 * </ul>
 * 
 * <p>The builder also supports error simulation (timeouts, 500 errors, invalid responses).
 * 
 * <p>Example usage:
 * <pre>
 * ProviderStubBuilder.forImmediatePattern()
 *     .serviceName("StudentPersonals")
 *     .responseBody(studentResponse)
 *     .requireAuthHeader()
 *     .requireZoneHeader("test-zone")
 *     .build();
 * </pre>
 * 
 * @since 1.0.0
 */
@Slf4j
public class ProviderStubBuilder {
    
    private String serviceName;
    private String path;
    private int statusCode = 200;
    private String responseBody = "{}";
    private int delayMillis = 0;
    private boolean requireAuth = false;
    private String requiredZone = null;
    private String requiredContext = null;
    private String requiredMessageId = null;
    
    /**
     * Creates a builder for IMMEDIATE pattern stubs.
     * 
     * @return a new builder instance
     */
    public static ProviderStubBuilder forImmediatePattern() {
        return new ProviderStubBuilder();
    }
    
    /**
     * Creates a builder for EVENT pattern stubs.
     * 
     * @return a new builder instance
     */
    public static ProviderStubBuilder forEventPattern() {
        ProviderStubBuilder builder = new ProviderStubBuilder();
        builder.statusCode = 202; // Accepted
        return builder;
    }
    
    /**
     * Sets the service name (used to construct the path).
     * 
     * @param serviceName the service name (e.g., "StudentPersonals")
     * @return this builder for chaining
     */
    public ProviderStubBuilder serviceName(String serviceName) {
        this.serviceName = serviceName;
        this.path = "/" + serviceName;
        return this;
    }
    
    /**
     * Sets a custom path (overrides service name path).
     * 
     * @param path the request path
     * @return this builder for chaining
     */
    public ProviderStubBuilder path(String path) {
        this.path = path;
        return this;
    }
    
    /**
     * Sets the HTTP status code to return.
     * 
     * @param statusCode the status code
     * @return this builder for chaining
     */
    public ProviderStubBuilder statusCode(int statusCode) {
        this.statusCode = statusCode;
        return this;
    }
    
    /**
     * Sets the response body.
     * 
     * @param responseBody the response body (JSON or XML)
     * @return this builder for chaining
     */
    public ProviderStubBuilder responseBody(String responseBody) {
        this.responseBody = responseBody;
        return this;
    }
    
    /**
     * Sets a delay before returning the response (simulates slow Provider).
     * 
     * @param delayMillis the delay in milliseconds
     * @return this builder for chaining
     */
    public ProviderStubBuilder withDelay(int delayMillis) {
        this.delayMillis = delayMillis;
        return this;
    }
    
    /**
     * Requires an Authorization header with SIFHMACSHA256 scheme.
     * 
     * @return this builder for chaining
     */
    public ProviderStubBuilder requireAuthHeader() {
        this.requireAuth = true;
        return this;
    }
    
    /**
     * Requires a specific zoneId header.
     * 
     * @param zoneId the required zone ID
     * @return this builder for chaining
     */
    public ProviderStubBuilder requireZoneHeader(String zoneId) {
        this.requiredZone = zoneId;
        return this;
    }
    
    /**
     * Requires a specific contextId header.
     * 
     * @param contextId the required context ID
     * @return this builder for chaining
     */
    public ProviderStubBuilder requireContextHeader(String contextId) {
        this.requiredContext = contextId;
        return this;
    }
    
    /**
     * Requires a specific messageId header.
     * 
     * @param messageId the required message ID
     * @return this builder for chaining
     */
    public ProviderStubBuilder requireMessageIdHeader(String messageId) {
        this.requiredMessageId = messageId;
        return this;
    }
    
    /**
     * Configures the stub to return a 500 Internal Server Error.
     * 
     * @return this builder for chaining
     */
    public ProviderStubBuilder withServerError() {
        this.statusCode = 500;
        this.responseBody = "{\"error\": \"Internal Server Error\"}";
        return this;
    }
    
    /**
     * Configures the stub to return a 503 Service Unavailable.
     * 
     * @return this builder for chaining
     */
    public ProviderStubBuilder withServiceUnavailable() {
        this.statusCode = 503;
        this.responseBody = "{\"error\": \"Service Unavailable\"}";
        return this;
    }
    
    /**
     * Configures the stub to simulate a timeout (10 second delay).
     * 
     * @return this builder for chaining
     */
    public ProviderStubBuilder withTimeout() {
        this.delayMillis = 10000; // 10 seconds
        return this;
    }
    
    /**
     * Builds and registers the stub with WireMock using the static client.
     * 
     * @return the created stub mapping
     */
    public StubMapping build() {
        if (path == null) {
            throw new IllegalStateException("Path or service name must be set");
        }
        
        log.debug("Building Provider stub: {} -> {} (delay: {}ms)", path, statusCode, delayMillis);
        
        // Build request matcher
        var requestBuilder = WireMock.post(WireMock.urlPathEqualTo(path));
        
        // Add header requirements
        if (requireAuth) {
            requestBuilder.withHeader("Authorization", WireMock.matching("SIF_HMACSHA256.*"));
        }
        if (requiredZone != null) {
            requestBuilder.withHeader("zoneId", WireMock.equalTo(requiredZone));
        }
        if (requiredContext != null) {
            requestBuilder.withHeader("contextId", WireMock.equalTo(requiredContext));
        }
        if (requiredMessageId != null) {
            requestBuilder.withHeader("messageId", WireMock.equalTo(requiredMessageId));
        }
        
        // Build response
        var responseBuilder = WireMock.aResponse()
            .withStatus(statusCode)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody);
        
        // Add delay if specified
        if (delayMillis > 0) {
            responseBuilder.withFixedDelay(delayMillis);
        }
        
        // Register stub
        return WireMock.stubFor(requestBuilder.willReturn(responseBuilder));
    }
    
    /**
     * Builds and registers the stub with a specific WireMock server instance.
     * This is the preferred method when using multiple WireMock servers or when
     * you need to ensure the stub is registered on a specific server.
     * 
     * @param wireMockServer the WireMock server to register the stub on
     * @return the created stub mapping
     */
    public StubMapping buildOn(com.github.tomakehurst.wiremock.WireMockServer wireMockServer) {
        if (path == null) {
            throw new IllegalStateException("Path or service name must be set");
        }
        
        log.debug("Building Provider stub on server: {} -> {} (delay: {}ms)", path, statusCode, delayMillis);
        
        // Build request matcher
        var requestBuilder = WireMock.post(WireMock.urlPathEqualTo(path));
        
        // Add header requirements
        if (requireAuth) {
            requestBuilder.withHeader("Authorization", WireMock.matching("SIF_HMACSHA256.*"));
        }
        if (requiredZone != null) {
            requestBuilder.withHeader("zoneId", WireMock.equalTo(requiredZone));
        }
        if (requiredContext != null) {
            requestBuilder.withHeader("contextId", WireMock.equalTo(requiredContext));
        }
        if (requiredMessageId != null) {
            requestBuilder.withHeader("messageId", WireMock.equalTo(requiredMessageId));
        }
        
        // Build response
        var responseBuilder = WireMock.aResponse()
            .withStatus(statusCode)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody);
        
        // Add delay if specified
        if (delayMillis > 0) {
            responseBuilder.withFixedDelay(delayMillis);
        }
        
        // Register stub on the specific server instance
        return wireMockServer.stubFor(requestBuilder.willReturn(responseBuilder));
    }
    
    /**
     * Creates a request pattern for verifying requests.
     * 
     * @return a request pattern builder
     */
    public RequestPatternBuilder buildVerificationPattern() {
        if (path == null) {
            throw new IllegalStateException("Path or service name must be set");
        }
        
        var patternBuilder = WireMock.postRequestedFor(WireMock.urlPathEqualTo(path));
        
        // Add header requirements
        if (requireAuth) {
            patternBuilder.withHeader("Authorization", WireMock.matching("SIF_HMACSHA256.*"));
        }
        if (requiredZone != null) {
            patternBuilder.withHeader("zoneId", WireMock.equalTo(requiredZone));
        }
        if (requiredContext != null) {
            patternBuilder.withHeader("contextId", WireMock.equalTo(requiredContext));
        }
        if (requiredMessageId != null) {
            patternBuilder.withHeader("messageId", WireMock.equalTo(requiredMessageId));
        }
        
        return patternBuilder;
    }
}
