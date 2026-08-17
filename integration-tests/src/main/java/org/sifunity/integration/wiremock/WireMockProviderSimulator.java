// SPDX-License-Identifier: MIT
package org.sifunity.integration.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import lombok.extern.slf4j.Slf4j;

/**
 * Wrapper for WireMock server to simulate external Provider applications.
 * 
 * <p>This class provides a simplified interface for starting and managing a WireMock
 * server during integration tests. The server simulates Provider applications that
 * the broker routes messages to.
 * 
 * <p>The WireMock server can simulate:
 * <ul>
 *   <li>IMMEDIATE pattern: Synchronous HTTP responses</li>
 *   <li>DELAYED pattern: Queue-based request/response (Provider polls queue)</li>
 *   <li>EVENT pattern: Event publication</li>
 *   <li>Error conditions: Timeouts, 500 errors, invalid responses</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>
 * WireMockProviderSimulator simulator = new WireMockProviderSimulator();
 * simulator.start();
 * 
 * // Configure stubs
 * simulator.stubImmediateResponse("/StudentPersonals", 200, responseBody);
 * 
 * // Make requests to simulator.getBaseUrl()
 * 
 * // Verify requests
 * simulator.verifyRequestReceived("/StudentPersonals");
 * 
 * simulator.stop();
 * </pre>
 * 
 * @since 1.0.0
 */
@Slf4j
public class WireMockProviderSimulator {
    
    private WireMockServer wireMockServer;
    private int port;
    
    /**
     * Starts the WireMock server on a random available port.
     */
    public void start() {
        log.info("Starting WireMock provider simulator");
        
        // Configure WireMock with dynamic port
        WireMockConfiguration config = WireMockConfiguration.options()
            .dynamicPort();
            // Note: Request journal is enabled by default in WireMock 3.x
        
        wireMockServer = new WireMockServer(config);
        wireMockServer.start();
        
        port = wireMockServer.port();
        
        // Configure WireMock client to use this server
        WireMock.configureFor("localhost", port);
        
        log.info("WireMock provider simulator started on port: {}", port);
        log.info("Base URL: {}", getBaseUrl());
    }
    
    /**
     * Starts the WireMock server on a specific port.
     * 
     * @param port the port to use
     */
    public void start(int port) {
        log.info("Starting WireMock provider simulator on port: {}", port);
        
        WireMockConfiguration config = WireMockConfiguration.options()
            .port(port);
            // Note: Request journal is enabled by default in WireMock 3.x
        
        wireMockServer = new WireMockServer(config);
        wireMockServer.start();
        
        this.port = port;
        
        WireMock.configureFor("localhost", port);
        
        log.info("WireMock provider simulator started");
        log.info("Base URL: {}", getBaseUrl());
    }
    
    /**
     * Stops the WireMock server.
     */
    public void stop() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            log.info("Stopping WireMock provider simulator");
            wireMockServer.stop();
            log.info("WireMock provider simulator stopped");
        }
    }
    
    /**
     * Resets all stubs and request history.
     * This should be called between tests to ensure test isolation.
     */
    public void reset() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            log.debug("Resetting WireMock stubs and request history");
            wireMockServer.resetAll();
        }
    }
    
    /**
     * Resets only the request history, keeping stubs intact.
     */
    public void resetRequests() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            log.debug("Resetting WireMock request history");
            wireMockServer.resetRequests();
        }
    }
    
    /**
     * Resets only the stubs, keeping request history intact.
     */
    public void resetStubs() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            log.debug("Resetting WireMock stubs");
            wireMockServer.resetMappings();
        }
    }
    
    /**
     * Gets the base URL of the WireMock server.
     * 
     * @return the base URL (e.g., http://localhost:8089)
     */
    public String getBaseUrl() {
        if (wireMockServer == null || !wireMockServer.isRunning()) {
            throw new IllegalStateException("WireMock server is not running");
        }
        return "http://localhost:" + port;
    }
    
    /**
     * Gets the port the WireMock server is listening on.
     * 
     * @return the port number
     */
    public int getPort() {
        if (wireMockServer == null || !wireMockServer.isRunning()) {
            throw new IllegalStateException("WireMock server is not running");
        }
        return port;
    }
    
    /**
     * Checks if the WireMock server is running.
     * 
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return wireMockServer != null && wireMockServer.isRunning();
    }
    
    /**
     * Gets the underlying WireMock server instance.
     * This allows direct access to WireMock APIs for advanced scenarios.
     * 
     * @return the WireMock server
     */
    public WireMockServer getWireMockServer() {
        return wireMockServer;
    }
    
    /**
     * Stubs an immediate (synchronous) response for a given path.
     * 
     * @param path the request path (e.g., "/StudentPersonals")
     * @param statusCode the HTTP status code to return
     * @param responseBody the response body
     */
    public void stubImmediateResponse(String path, int statusCode, String responseBody) {
        log.debug("Stubbing immediate response: {} -> {}", path, statusCode);
        
        WireMock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo(path))
                .willReturn(WireMock.aResponse()
                    .withStatus(statusCode)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseBody))
        );
    }
    
    /**
     * Stubs a delayed response (simulates slow Provider).
     * 
     * @param path the request path
     * @param statusCode the HTTP status code to return
     * @param responseBody the response body
     * @param delayMillis the delay in milliseconds
     */
    public void stubDelayedResponse(String path, int statusCode, String responseBody, int delayMillis) {
        log.debug("Stubbing delayed response: {} -> {} (delay: {}ms)", path, statusCode, delayMillis);
        
        WireMock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo(path))
                .willReturn(WireMock.aResponse()
                    .withStatus(statusCode)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseBody)
                    .withFixedDelay(delayMillis))
        );
    }
    
    /**
     * Stubs an error response.
     * 
     * @param path the request path
     * @param statusCode the HTTP error status code
     * @param errorMessage the error message
     */
    public void stubErrorResponse(String path, int statusCode, String errorMessage) {
        log.debug("Stubbing error response: {} -> {}", path, statusCode);
        
        WireMock.stubFor(
            WireMock.post(WireMock.urlPathEqualTo(path))
                .willReturn(WireMock.aResponse()
                    .withStatus(statusCode)
                    .withHeader("Content-Type", "application/json")
                    .withBody(errorMessage))
        );
    }
    
    /**
     * Verifies that a request was received at the given path.
     * 
     * @param path the request path
     */
    public void verifyRequestReceived(String path) {
        log.debug("Verifying request received: {}", path);
        
        WireMock.verify(
            WireMock.postRequestedFor(WireMock.urlPathEqualTo(path))
        );
    }
    
    /**
     * Verifies that a request was received at the given path with a specific header.
     * 
     * @param path the request path
     * @param headerName the header name
     * @param headerPattern the header value pattern (regex)
     */
    public void verifyRequestReceivedWithHeader(String path, String headerName, String headerPattern) {
        log.debug("Verifying request received: {} with header {}={}", path, headerName, headerPattern);
        
        WireMock.verify(
            WireMock.postRequestedFor(WireMock.urlPathEqualTo(path))
                .withHeader(headerName, WireMock.matching(headerPattern))
        );
    }
    
    /**
     * Verifies that a request was NOT received at the given path.
     * 
     * @param path the request path
     */
    public void verifyRequestNotReceived(String path) {
        log.debug("Verifying request NOT received: {}", path);
        
        WireMock.verify(0,
            WireMock.postRequestedFor(WireMock.urlPathEqualTo(path))
        );
    }
    
    /**
     * Gets the number of requests received at the given path.
     * 
     * @param path the request path
     * @return the number of requests
     */
    public int getRequestCount(String path) {
        return wireMockServer.countRequestsMatching(
            WireMock.postRequestedFor(WireMock.urlPathEqualTo(path)).build()
        ).getCount();
    }
}
