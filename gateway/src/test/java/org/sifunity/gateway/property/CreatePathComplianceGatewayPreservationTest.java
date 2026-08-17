// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.broker.common.dto.PermissionCheckRequest;
import org.sifunity.broker.common.dto.PermissionCheckResponse;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import org.sifunity.gateway.filter.AuthorizationFilter;
import org.sifunity.gateway.metrics.GatewayMetricsService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Preservation Property Tests for SIF 3.7 create-path compliance (gateway).
 *
 * <p><strong>Property 2: Preservation — gateway routing/authorization behavior unchanged.</strong></p>
 *
 * <p><strong>CRITICAL: These tests are EXPECTED TO PASS on the UNFIXED code.</strong> They capture the
 * baseline gateway authorization behavior that must be preserved after the fix (task 3.5).</p>
 *
 * <p><strong>Observation-first methodology:</strong> the {@link AuthorizationFilter} classifies each
 * request path into one of two categories — "authenticate but no ACL" (infrastructure/utility paths,
 * which skip {@code checkPermission}) and "ACL enforced" (connector paths, which call
 * {@code checkPermission}). This test runs the real filter against the UNFIXED code and records that
 * classification, then asserts it.</p>
 *
 * <p>The provider rename ({@code /provider-registrations} → {@code /providers}) keeps provider in the
 * exact same "authenticate, no ACL" category — only the literal path segment changes. This test
 * therefore exercises the <em>other</em> stable infrastructure paths (which the fix does not touch) plus
 * the connector ACL-enforcement behavior, so it remains valid both before and after the fix. The
 * provider path's own rename is validated by the bug-condition / Property 1 tests.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.5</strong></p>
 */
class CreatePathComplianceGatewayPreservationTest {

    private static final String PREFIX = "/iowa/production";

    private final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    /**
     * Preservation: infrastructure/utility paths are authorized without an ACL check.
     *
     * <p>For each stable infrastructure/utility resource path, the AuthorizationFilter forwards to the
     * chain without calling {@code checkPermission} and never returns 403. This is the same
     * "authenticate, no ACL" category that the renamed provider collection belongs to.</p>
     *
     * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.5</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: infrastructure/utility paths skip ACL check (no checkPermission, no 403)")
    void infrastructurePathsRemainAclExempt(@ForAll("exemptResourcePaths") String resourcePath) {
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayMetricsService mockMetrics = mock(GatewayMetricsService.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        when(mockChain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            chainCalled.set(true);
            return Mono.empty();
        });

        AuthorizationFilter filter = new AuthorizationFilter(mockClient, objectMapper, mockMetrics);

        MockServerHttpRequest request = MockServerHttpRequest
                .get(PREFIX + resourcePath)
                .header(SifHeaders.X_CORRELATION_ID, UUID.randomUUID().toString())
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        // environmentId is set by AuthenticationFilter upstream; present here for realism.
        exchange.getAttributes().put("environmentId", UUID.randomUUID());
        exchange.getAttributes().put("defaultZoneId", "default");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        assertThat(chainCalled.get())
                .as("Preservation: %s is ACL-exempt — chain should be called", PREFIX + resourcePath)
                .isTrue();
        verify(mockClient, never()).checkPermission(any(PermissionCheckRequest.class));
        assertThat(exchange.getResponse().getStatusCode())
                .as("Preservation: %s should not be blocked with 403", PREFIX + resourcePath)
                .isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * Preservation: connector paths remain ACL-enforced.
     *
     * <p>For connector resource paths, the AuthorizationFilter calls {@code checkPermission}; when the
     * permission is granted the request proceeds. This confirms the fix does not accidentally move a
     * connector path into the no-ACL category.</p>
     *
     * <p><strong>Validates: Requirement 3.1</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: connector paths still trigger ACL check and proceed when granted")
    void connectorPathsRemainAclEnforced(@ForAll("connectorObjectNames") String objectName) {
        EnvironmentServiceClient mockClient = mock(EnvironmentServiceClient.class);
        GatewayMetricsService mockMetrics = mock(GatewayMetricsService.class);
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        when(mockChain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            chainCalled.set(true);
            return Mono.empty();
        });
        when(mockClient.checkPermission(any(PermissionCheckRequest.class)))
                .thenReturn(Mono.just(new PermissionCheckResponse(true)));

        AuthorizationFilter filter = new AuthorizationFilter(mockClient, objectMapper, mockMetrics);

        MockServerHttpRequest request = MockServerHttpRequest
                .get(PREFIX + "/requests/" + objectName)
                .header(SifHeaders.X_CORRELATION_ID, UUID.randomUUID().toString())
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put("environmentId", UUID.randomUUID());
        exchange.getAttributes().put("defaultZoneId", "default");

        StepVerifier.create(filter.filter(exchange, mockChain)).verifyComplete();

        verify(mockClient).checkPermission(any(PermissionCheckRequest.class));
        assertThat(chainCalled.get())
                .as("Preservation: granted connector request /requests/%s should proceed", objectName)
                .isTrue();
        assertThat(exchange.getResponse().getStatusCode())
                .as("Preservation: granted connector request /requests/%s should not be 403", objectName)
                .isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    // ===================== Arbitraries =====================

    /**
     * Stable infrastructure/utility resource paths that are ACL-exempt before and after the fix.
     * Provider ({@code /provider-registrations} → {@code /providers}) is intentionally excluded
     * because its literal path changes as part of the fix (validated by the bug-condition test).
     */
    @Provide
    Arbitrary<String> exemptResourcePaths() {
        return Arbitraries.of(
                "/environments",
                "/environments/" + UUID.randomUUID(),
                "/queues",
                "/zones",
                "/acl-entries",
                "/admin-users",
                "/subscriptions",
                "/jobs",
                "/tags",
                "/solution-services",
                "/context-services",
                "/alerts",
                "/codeSets",
                "/namespaces",
                "/xquerys");
    }

    @Provide
    Arbitrary<String> connectorObjectNames() {
        return Arbitraries.of("StudentPersonal", "SchoolInfo", "StaffPersonal", "TeachingGroup");
    }
}
