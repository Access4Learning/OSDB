// SPDX-License-Identifier: MIT
package org.sifunity.integration.base;

import lombok.extern.slf4j.Slf4j;

/**
 * Base class for integration tests that require all microservices to be running.
 * 
 * <p>This class extends {@link IntegrationTestBase} and enables service startup
 * by overriding {@link #shouldStartServices()} to return true.
 * 
 * <p>When tests extend this class:
 * <ul>
 *   <li>All Testcontainers are started (PostgreSQL, Redis, LocalStack)</li>
 *   <li>Database migrations are applied</li>
 *   <li>All 9 microservices are started in the correct order</li>
 *   <li>RestAssured is configured to use the Gateway</li>
 * </ul>
 * 
 * <p>Service startup adds significant time to test execution (~2-3 minutes),
 * so use this base class only for tests that truly need end-to-end service
 * integration. For tests that only need infrastructure (containers), use
 * {@link IntegrationTestBase} directly.
 * 
 * <p>Example usage:
 * <pre>
 * &#64;Tag("message-pattern")
 * public class ImmediatePatternTest extends ServiceEnabledIntegrationTestBase {
 *     
 *     &#64;Test
 *     public void testImmediateRequest() {
 *         // Services are already running
 *         // Make requests via RestAssured
 *         given()
 *             .spec(sifRequest(sessionToken, sharedSecret, zoneId, contextId))
 *             .body(requestPayload)
 *         .when()
 *             .post("/requests/StudentPersonals")
 *         .then()
 *             .statusCode(200);
 *     }
 * }
 * </pre>
 * 
 * @since 1.0.0
 */
@Slf4j
public abstract class ServiceEnabledIntegrationTestBase extends IntegrationTestBase {
    
    /**
     * Enables service startup for tests extending this class.
     * 
     * @return true to enable service startup
     */
    @Override
    protected boolean shouldStartServices() {
        return true;
    }
}
