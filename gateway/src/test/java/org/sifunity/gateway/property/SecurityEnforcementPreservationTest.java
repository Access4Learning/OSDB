// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.gateway.filter.EnvironmentContextFilter;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Preservation Property Tests for Security Enforcement - X-Environment-Id Header
 *
 * <p><strong>CRITICAL: These tests are EXPECTED TO PASS on unfixed code.</strong>
 * They capture the baseline behavior that must remain unchanged after the security fix.
 *
 * <p><strong>Purpose:</strong>
 * Verifies that admin and broker-services environments can still use X-Environment-Id
 * for impersonation after the security enforcement fix. The EnvironmentContextFilter
 * strips client-supplied headers and re-injects authenticated values from exchange attributes.
 * For admin/broker-services, impersonation is allowed (via ProxyImpersonationFilter which
 * overrides the exchange attributes before EnvironmentContextFilter runs).
 *
 * <p><strong>Validates: Requirements 3.3</strong>
 *
 * @see EnvironmentContextFilter
 */
class SecurityEnforcementPreservationTest {

    /**
     * Property 2.3: Preservation - Admin/broker-services X-Environment-Id preserved for impersonation
     *
     * <p><strong>Validates: Requirement 3.3</strong>
     *
     * <p>For all admin/broker-services environments, when the ProxyImpersonationFilter
     * has already overridden the exchange attributes (simulating successful impersonation),
     * the EnvironmentContextFilter injects the impersonated environment's ID into the
     * X-Environment-Id header forwarded to backend services.
     *
     * <p>This confirms that after the security fix, privileged environments can still
     * perform impersonation — the impersonated environment's ID (set in exchange attributes
     * by ProxyImpersonationFilter) is injected into the forwarded request header.
     *
     * @param adminEnvironmentId the admin's own environment ID
     * @param impersonatedEnvironmentId the target (impersonated) environment ID
     * @param impersonatedName the target environment's application name
     */
    @Property(tries = 20)
    @Label("Preservation: Admin/broker-services X-Environment-Id header preserved for impersonation")
    void adminBrokerServicesXEnvironmentIdPreservedForImpersonation(
            @ForAll("adminEnvironmentIds") UUID adminEnvironmentId,
            @ForAll("impersonatedEnvironmentIds") UUID impersonatedEnvironmentId,
            @ForAll("environmentNames") String impersonatedName) {

        // Arrange: EnvironmentContextFilter injects from exchange attributes
        EnvironmentContextFilter filter = new EnvironmentContextFilter("test-internal-token");
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);

        // Capture the request forwarded to the chain
        AtomicReference<String> capturedXEnvironmentId = new AtomicReference<>();
        AtomicReference<String> capturedXEnvironmentName = new AtomicReference<>();

        when(mockChain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation -> {
                    ServerWebExchange ex = invocation.getArgument(0);
                    capturedXEnvironmentId.set(
                            ex.getRequest().getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_ID));
                    capturedXEnvironmentName.set(
                            ex.getRequest().getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_NAME));
                    return Mono.empty();
                });

        // Simulate client sending a spoofed X-Environment-Id header
        // (this would be the impersonation scenario)
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/GLOBAL_DOMAIN/GLOBAL_SOLUTION/zones")
                .header(SifHeaders.X_ENVIRONMENT_ID, "spoofed-id-should-be-replaced")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // ProxyImpersonationFilter would have already run (Order 5) and set the
        // impersonated environment's ID in exchange attributes.
        // EnvironmentContextFilter (Order 6) reads from these attributes.
        exchange.getAttributes().put("environmentId", impersonatedEnvironmentId);
        exchange.getAttributes().put("environmentName", impersonatedName);
        exchange.getAttributes().put("fingerprint", "broker-services");

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert: The forwarded header uses the impersonated ID (from exchange attributes),
        // not the spoofed client-supplied value
        assertThat(capturedXEnvironmentId.get())
                .as("X-Environment-Id should be the impersonated environment's ID (from exchange attributes)")
                .isEqualTo(impersonatedEnvironmentId.toString());

        assertThat(capturedXEnvironmentName.get())
                .as("X-Environment-Name should be the impersonated environment's name")
                .isEqualTo(impersonatedName);
    }

    /**
     * Property 2.3b: Preservation - EnvironmentContextFilter always strips and re-injects
     *
     * <p><strong>Validates: Requirement 3.3</strong>
     *
     * <p>For all authenticated environments (regardless of type), the EnvironmentContextFilter
     * strips client-supplied X-Environment-Id and injects the value from exchange attributes.
     * This is the mechanism that enables admin impersonation while blocking non-admin spoofing.
     *
     * <p>On UNFIXED code, this already works correctly — the filter always strips and re-injects.
     * The preservation test confirms this mechanism continues to function.
     *
     * @param authenticatedEnvId the authenticated environment's ID in exchange attributes
     * @param authenticatedEnvName the authenticated environment's name
     * @param clientSuppliedId a spoofed X-Environment-Id sent by the client
     */
    @Property(tries = 20)
    @Label("Preservation: EnvironmentContextFilter strips and re-injects from exchange attributes")
    void environmentContextFilterStripsAndReinjects(
            @ForAll("adminEnvironmentIds") UUID authenticatedEnvId,
            @ForAll("environmentNames") String authenticatedEnvName,
            @ForAll("impersonatedEnvironmentIds") UUID clientSuppliedId) {

        // Arrange
        EnvironmentContextFilter filter = new EnvironmentContextFilter("test-internal-token");
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);

        AtomicReference<String> capturedXEnvironmentId = new AtomicReference<>();
        AtomicReference<String> capturedXFingerprint = new AtomicReference<>();

        when(mockChain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation -> {
                    ServerWebExchange ex = invocation.getArgument(0);
                    capturedXEnvironmentId.set(
                            ex.getRequest().getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_ID));
                    capturedXFingerprint.set(
                            ex.getRequest().getHeaders().getFirst(SifHeaders.X_ENVIRONMENT_FINGERPRINT));
                    return Mono.empty();
                });

        // Client sends spoofed headers
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/test-domain/test-solution/zones")
                .header(SifHeaders.X_ENVIRONMENT_ID, clientSuppliedId.toString())
                .header(SifHeaders.X_ENVIRONMENT_FINGERPRINT, "fake-fingerprint")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Exchange attributes set by AuthenticationFilter (the true authenticated identity)
        exchange.getAttributes().put("environmentId", authenticatedEnvId);
        exchange.getAttributes().put("environmentName", authenticatedEnvName);
        exchange.getAttributes().put("fingerprint", "broker-services");

        // Act
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();

        // Assert: Client-supplied headers are stripped; exchange attribute values are injected
        assertThat(capturedXEnvironmentId.get())
                .as("X-Environment-Id should be the authenticated ID from exchange attributes, not client-supplied")
                .isEqualTo(authenticatedEnvId.toString());

        assertThat(capturedXFingerprint.get())
                .as("X-Environment-Fingerprint should be from exchange attributes")
                .isEqualTo("broker-services");
    }

    // ========== Test Data Generators ==========

    @Provide
    Arbitrary<UUID> adminEnvironmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<UUID> impersonatedEnvironmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<String> environmentNames() {
        return Arbitraries.of(
                "admin-app", "broker-services", "BrokerServicesApp",
                "GlobalAdminApp", "SystemAdmin", "PlatformAdmin",
                "InfraServices", "AdminConsole", "ServiceOrchestrator",
                "DataSyncAdmin");
    }
}
