// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.gateway.filter.EnvironmentContextFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bug Condition Exploration Test for Security Enforcement Gap 3.
 *
 * <p><strong>CRITICAL: This test MUST FAIL on unfixed code — failure confirms the bug exists.</strong></p>
 *
 * <p>Gap 3: Non-admin environment sends request with client-supplied X-Environment-Id header —
 * the gateway should strip that header and inject only the authenticated environment's own ID.
 * On unfixed code where certain code paths allow the header to pass through, the backend
 * receives the spoofed ID.</p>
 *
 * <p>The EnvironmentContextFilter currently strips X-Environment-Id correctly when
 * environmentId is set in exchange attributes. However, the bug description indicates there
 * may be code paths where this doesn't happen (e.g., if environmentId attribute is null on
 * certain exempt paths that still reach backends). This test verifies that regardless of
 * whether a client supplies X-Environment-Id, the downstream request always contains only
 * the authenticated environment's own ID from exchange attributes.</p>
 *
 * <p><strong>Validates: Requirements 1.3</strong></p>
 */
class SecurityEnforcementHeaderBugConditionExplorationTest {

    /**
     * Property 1: Bug Condition — Client-supplied X-Environment-Id must be replaced with
     * the authenticated environment's own ID.
     *
     * <p>For any non-admin, non-broker-services environment that sends a request with
     * a client-supplied X-Environment-Id header value (attempting impersonation), the
     * EnvironmentContextFilter SHALL strip that header and inject only the authenticated
     * environment's own ID.</p>
     *
     * <p>The bug manifests when environmentId is null in exchange attributes (exempt paths
     * like POST /environments or /actuator/health). In this case, the filter skips header
     * injection entirely, meaning the client-supplied X-Environment-Id passes through to
     * the backend unstripped.</p>
     *
     * <p><strong>Expected on UNFIXED code:</strong> FAILS — header passes through on exempt paths.</p>
     * <p><strong>Expected on FIXED code:</strong> PASSES — header always stripped regardless of path.</p>
     *
     * <p><strong>Validates: Requirements 1.3</strong></p>
     */
    @Property(tries = 20)
    @Label("Gap 3: Non-admin X-Environment-Id header must be stripped even on exempt paths")
    void nonAdminClientSuppliedEnvironmentIdHeaderMustBeStrippedOnExemptPaths(
            @ForAll("environmentTypes") String environmentType,
            @ForAll("spoofedIds") UUID spoofedEnvironmentId) {

        // Arrange: Create request with client-supplied X-Environment-Id (impersonation attempt)
        // This simulates an EXEMPT path where environmentId attribute is NOT set
        // (e.g., registration endpoint, health check)
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health")
                .header(SifHeaders.X_ENVIRONMENT_ID, spoofedEnvironmentId.toString())
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // DO NOT set environmentId attribute — simulates exempt path
        // On exempt paths, the filter currently skips injection AND skipping stripping

        EnvironmentContextFilter filter = new EnvironmentContextFilter("test-internal-token");

        // Capture the request that goes to the backend
        AtomicReference<ServerWebExchange> capturedExchange = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            capturedExchange.set(ex);
            return Mono.empty();
        };

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Assert: Even on exempt paths, the client-supplied X-Environment-Id should be stripped
        ServerWebExchange downstream = capturedExchange.get();
        assertThat(downstream).isNotNull();

        String downstreamEnvironmentId = downstream.getRequest().getHeaders()
                .getFirst(SifHeaders.X_ENVIRONMENT_ID);

        // On UNFIXED code: This will FAIL because the filter skips exempt paths entirely,
        // leaving the client-supplied X-Environment-Id header in place
        // On FIXED code: This will PASS because the filter strips X-Environment-Id on ALL paths
        assertThat(downstreamEnvironmentId)
                .as("Client-supplied X-Environment-Id header must be stripped even on exempt paths "
                        + "(environment type: %s, spoofed ID: %s)", environmentType, spoofedEnvironmentId)
                .isNull();
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> environmentTypes() {
        return Arbitraries.of("DIRECT", "BROKERED");
    }

    @Provide
    Arbitrary<UUID> spoofedIds() {
        return Arbitraries.create(UUID::randomUUID);
    }
}
