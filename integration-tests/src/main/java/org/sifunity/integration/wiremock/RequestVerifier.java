// SPDX-License-Identifier: MIT
package org.sifunity.integration.wiremock;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Utility for verifying requests received by WireMock Provider mocks.
 * 
 * <p>This class provides methods to verify that:
 * <ul>
 *   <li>Requests were received at expected paths</li>
 *   <li>Requests contain required headers (Authorization, zoneId, contextId, messageId)</li>
 *   <li>Requests contain expected payload content</li>
 *   <li>Requests match SIF 3.7 compliance requirements</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>
 * RequestVerifier verifier = new RequestVerifier();
 * 
 * // Verify request was received
 * verifier.verifyRequestReceived("/StudentPersonals");
 * 
 * // Verify request has auth header
 * verifier.verifyAuthHeader("/StudentPersonals", "SIF_HMACSHA256");
 * 
 * // Verify request has zone header
 * verifier.verifyZoneHeader("/StudentPersonals", "test-zone");
 * 
 * // Verify payload contains expected content
 * verifier.verifyPayloadContains("/StudentPersonals", "studentId");
 * </pre>
 * 
 * @since 1.0.0
 */
@Slf4j
public class RequestVerifier {
    
    /**
     * Verifies that at least one request was received at the given path.
     * 
     * @param path the request path
     * @throws AssertionError if no request was received
     */
    public void verifyRequestReceived(String path) {
        log.debug("Verifying request received: {}", path);
        
        WireMock.verify(
            WireMock.postRequestedFor(WireMock.urlPathEqualTo(path))
        );
    }
    
    /**
     * Verifies that exactly N requests were received at the given path.
     * 
     * @param path the request path
     * @param count the expected number of requests
     * @throws AssertionError if the count doesn't match
     */
    public void verifyRequestCount(String path, int count) {
        log.debug("Verifying request count: {} (expected: {})", path, count);
        
        WireMock.verify(count,
            WireMock.postRequestedFor(WireMock.urlPathEqualTo(path))
        );
    }
    
    /**
     * Verifies that no requests were received at the given path.
     * 
     * @param path the request path
     * @throws AssertionError if any request was received
     */
    public void verifyNoRequestReceived(String path) {
        log.debug("Verifying no request received: {}", path);
        
        WireMock.verify(0,
            WireMock.postRequestedFor(WireMock.urlPathEqualTo(path))
        );
    }
    
    /**
     * Verifies that a request has an Authorization header with the expected scheme.
     * 
     * @param path the request path
     * @param scheme the expected auth scheme (e.g., "SIF_HMACSHA256")
     * @throws AssertionError if the header is missing or doesn't match
     */
    public void verifyAuthHeader(String path, String scheme) {
        log.debug("Verifying Authorization header: {} (scheme: {})", path, scheme);
        
        WireMock.verify(
            WireMock.postRequestedFor(WireMock.urlPathEqualTo(path))
                .withHeader("Authorization", WireMock.matching(scheme + ".*"))
        );
    }
    
    /**
     * Verifies that a request has a zoneId header with the expected value.
     * 
     * @param path the request path
     * @param zoneId the expected zone ID
     * @throws AssertionError if the header is missing or doesn't match
     */
    public void verifyZoneHeader(String path, String zoneId) {
        log.debug("Verifying zoneId header: {} (zoneId: {})", path, zoneId);
        
        WireMock.verify(
            WireMock.postRequestedFor(WireMock.urlPathEqualTo(path))
                .withHeader("zoneId", WireMock.equalTo(zoneId))
        );
    }
    
    /**
     * Verifies that a request has a contextId header with the expected value.
     * 
     * @param path the request path
     * @param contextId the expected context ID
     * @throws AssertionError if the header is missing or doesn't match
     */
    public void verifyContextHeader(String path, String contextId) {
        log.debug("Verifying contextId header: {} (contextId: {})", path, contextId);
        
        WireMock.verify(
            WireMock.postRequestedFor(WireMock.urlPathEqualTo(path))
                .withHeader("contextId", WireMock.equalTo(contextId))
        );
    }
    
    /**
     * Verifies that a request has a messageId header.
     * 
     * @param path the request path
     * @throws AssertionError if the header is missing
     */
    public void verifyMessageIdHeader(String path) {
        log.debug("Verifying messageId header: {}", path);
        
        WireMock.verify(
            WireMock.postRequestedFor(WireMock.urlPathEqualTo(path))
                .withHeader("messageId", WireMock.matching(".+"))
        );
    }
    
    /**
     * Verifies that a request has a timestamp header.
     * 
     * @param path the request path
     * @throws AssertionError if the header is missing
     */
    public void verifyTimestampHeader(String path) {
        log.debug("Verifying timestamp header: {}", path);
        
        WireMock.verify(
            WireMock.postRequestedFor(WireMock.urlPathEqualTo(path))
                .withHeader("timestamp", WireMock.matching(".+"))
        );
    }
    
    /**
     * Verifies that a request has all required SIF 3.7 headers.
     * 
     * @param path the request path
     * @param zoneId the expected zone ID
     * @param contextId the expected context ID
     * @throws AssertionError if any required header is missing
     */
    public void verifySifHeaders(String path, String zoneId, String contextId) {
        log.debug("Verifying SIF headers: {} (zone: {}, context: {})", path, zoneId, contextId);
        
        verifyAuthHeader(path, "SIF_HMACSHA256");
        verifyZoneHeader(path, zoneId);
        verifyContextHeader(path, contextId);
        verifyMessageIdHeader(path);
        verifyTimestampHeader(path);
    }
    
    /**
     * Verifies that a request payload contains the expected content.
     * 
     * @param path the request path
     * @param expectedContent the expected content (substring or regex)
     * @throws AssertionError if the payload doesn't contain the expected content
     */
    public void verifyPayloadContains(String path, String expectedContent) {
        log.debug("Verifying payload contains: {} (content: {})", path, expectedContent);
        
        WireMock.verify(
            WireMock.postRequestedFor(WireMock.urlPathEqualTo(path))
                .withRequestBody(WireMock.containing(expectedContent))
        );
    }
    
    /**
     * Verifies that a request payload matches the expected pattern.
     * 
     * @param path the request path
     * @param pattern the expected pattern (regex)
     * @throws AssertionError if the payload doesn't match the pattern
     */
    public void verifyPayloadMatches(String path, String pattern) {
        log.debug("Verifying payload matches: {} (pattern: {})", path, pattern);
        
        WireMock.verify(
            WireMock.postRequestedFor(WireMock.urlPathEqualTo(path))
                .withRequestBody(WireMock.matching(pattern))
        );
    }
    
    /**
     * Verifies a request using a custom request pattern.
     * 
     * @param pattern the request pattern
     * @throws AssertionError if no matching request was found
     */
    public void verifyRequest(RequestPatternBuilder pattern) {
        log.debug("Verifying custom request pattern");
        
        WireMock.verify(pattern);
    }
    
    /**
     * Gets all requests received at the given path.
     * 
     * @param path the request path
     * @return list of logged requests
     */
    public List<LoggedRequest> getRequestsAt(String path) {
        log.debug("Getting requests at: {}", path);
        
        return WireMock.findAll(
            WireMock.postRequestedFor(WireMock.urlPathEqualTo(path))
        );
    }
    
    /**
     * Gets the first request received at the given path.
     * 
     * @param path the request path
     * @return the first logged request, or null if no requests
     */
    public LoggedRequest getFirstRequestAt(String path) {
        List<LoggedRequest> requests = getRequestsAt(path);
        return requests.isEmpty() ? null : requests.get(0);
    }
    
    /**
     * Gets the request body of the first request at the given path.
     * 
     * @param path the request path
     * @return the request body as a string, or null if no requests
     */
    public String getRequestBody(String path) {
        LoggedRequest request = getFirstRequestAt(path);
        return request != null ? request.getBodyAsString() : null;
    }
    
    /**
     * Gets a header value from the first request at the given path.
     * 
     * @param path the request path
     * @param headerName the header name
     * @return the header value, or null if not found
     */
    public String getRequestHeader(String path, String headerName) {
        LoggedRequest request = getFirstRequestAt(path);
        if (request != null && request.getHeader(headerName) != null) {
            return request.getHeader(headerName);
        }
        return null;
    }
}
