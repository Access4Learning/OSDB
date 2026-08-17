// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.SifHeaders;
import org.sifunity.gateway.filter.MatrixParameterFilter;
import org.sifunity.gateway.filter.ParameterResolutionFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for default injection completeness (Property 6).
 *
 * <p>Feature: sif-matrix-param-compat, Property 6: Default injection completeness</p>
 *
 * <p>Validates that when zoneId is absent from both matrix params and headers,
 * and the environment has a configured defaultZoneId, the resolved zoneId equals
 * the environment's defaultZoneId. Also validates that when contextId is absent
 * from both sources, the resolved contextId is "DEFAULT".</p>
 *
 * <p><b>Validates: Requirements 3.1, 3.3, 3.4</b></p>
 *
 * @see ParameterResolutionFilter
 */
class DefaultInjectionPropertyTest {

    private final ParameterResolutionFilter filter = new ParameterResolutionFilter(new ObjectMapper());

    /**
     * Property 6a: When zoneId is absent and environment has defaultZoneId,
     * resolved zoneId equals the environment's defaultZoneId.
     *
     * <p><b>Validates: Requirements 3.1</b></p>
     */
    @Property(tries = 50)
    @Label("Feature: sif-matrix-param-compat, Property 6: Default injection completeness")
    void zoneIdDefaultsToEnvironmentDefault(
            @ForAll("servicePaths") String servicePath,
            @ForAll("defaultZoneIds") String defaultZoneId,
            @ForAll("environmentIds") UUID environmentId) {

        // Arrange: request with NO zoneId in headers, environment has defaultZoneId
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);

        MockServerHttpRequest request = MockServerHttpRequest
                .get(servicePath)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Set environment context (normally set by AuthenticationFilter/EnvironmentContextFilter)
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", "TestApp");
        exchange.getAttributes().put("defaultZoneId", defaultZoneId);
        // Empty matrix params — no zoneId from matrix source either
        exchange.getAttributes().put(MatrixParameterFilter.MATRIX_PARAMS_ATTR, Map.<String, String>of());

        when(mockChain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation -> {
                    ServerWebExchange mutatedExchange = invocation.getArgument(0);
                    ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();

                    // Property: resolved zoneId must equal environment's defaultZoneId
                    String resolvedZoneId = mutatedRequest.getHeaders().getFirst(SifHeaders.ZONE_ID);
                    assertThat(resolvedZoneId)
                            .as("When zoneId absent, resolved zoneId should equal environment defaultZoneId")
                            .isEqualTo(defaultZoneId);

                    return Mono.empty();
                });

        // Act & Assert
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();
    }

    /**
     * Property 6b: When contextId is absent from both sources,
     * resolved contextId is "DEFAULT".
     *
     * <p><b>Validates: Requirements 3.3</b></p>
     */
    @Property(tries = 50)
    @Label("Feature: sif-matrix-param-compat, Property 6: Default injection completeness")
    void contextIdDefaultsToDefault(
            @ForAll("servicePaths") String servicePath,
            @ForAll("defaultZoneIds") String defaultZoneId,
            @ForAll("environmentIds") UUID environmentId) {

        // Arrange: request with NO contextId in headers, environment has defaultZoneId
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);

        MockServerHttpRequest request = MockServerHttpRequest
                .get(servicePath)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Set environment context
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", "TestApp");
        exchange.getAttributes().put("defaultZoneId", defaultZoneId);
        // Empty matrix params — no contextId from matrix source either
        exchange.getAttributes().put(MatrixParameterFilter.MATRIX_PARAMS_ATTR, Map.<String, String>of());

        when(mockChain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation -> {
                    ServerWebExchange mutatedExchange = invocation.getArgument(0);
                    ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();

                    // Property: resolved contextId must be "DEFAULT"
                    String resolvedContextId = mutatedRequest.getHeaders().getFirst(SifHeaders.CONTEXT_ID);
                    assertThat(resolvedContextId)
                            .as("When contextId absent, resolved contextId should be 'DEFAULT'")
                            .isEqualTo("DEFAULT");

                    return Mono.empty();
                });

        // Act & Assert
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();
    }

    /**
     * Property 6c: Resolved zoneId and contextId are forwarded as canonical HTTP headers.
     *
     * <p><b>Validates: Requirements 3.4</b></p>
     */
    @Property(tries = 50)
    @Label("Feature: sif-matrix-param-compat, Property 6: Default injection completeness")
    void resolvedValuesForwardedAsCanonicalHeaders(
            @ForAll("servicePaths") String servicePath,
            @ForAll("defaultZoneIds") String defaultZoneId,
            @ForAll("environmentIds") UUID environmentId) {

        // Arrange: request with no zoneId/contextId — both should get defaults
        GatewayFilterChain mockChain = mock(GatewayFilterChain.class);

        MockServerHttpRequest request = MockServerHttpRequest
                .get(servicePath)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Set environment context
        exchange.getAttributes().put("environmentId", environmentId);
        exchange.getAttributes().put("environmentName", "TestApp");
        exchange.getAttributes().put("defaultZoneId", defaultZoneId);
        exchange.getAttributes().put(MatrixParameterFilter.MATRIX_PARAMS_ATTR, Map.<String, String>of());

        when(mockChain.filter(any(ServerWebExchange.class)))
                .thenAnswer(invocation -> {
                    ServerWebExchange mutatedExchange = invocation.getArgument(0);
                    ServerHttpRequest mutatedRequest = mutatedExchange.getRequest();

                    // Property: both zoneId and contextId headers must be present
                    String resolvedZoneId = mutatedRequest.getHeaders().getFirst(SifHeaders.ZONE_ID);
                    String resolvedContextId = mutatedRequest.getHeaders().getFirst(SifHeaders.CONTEXT_ID);

                    assertThat(resolvedZoneId)
                            .as("zoneId header must be present on forwarded request")
                            .isNotNull()
                            .isNotBlank();

                    assertThat(resolvedContextId)
                            .as("contextId header must be present on forwarded request")
                            .isNotNull()
                            .isNotBlank();

                    // Verify values are correct defaults
                    assertThat(resolvedZoneId).isEqualTo(defaultZoneId);
                    assertThat(resolvedContextId).isEqualTo("DEFAULT");

                    return Mono.empty();
                });

        // Act & Assert
        StepVerifier.create(filter.filter(exchange, mockChain))
                .verifyComplete();
    }

    // ===== Arbitraries =====

    @Provide
    Arbitrary<String> servicePaths() {
        return Arbitraries.of(
                "/StudentPersonals",
                "/SchoolInfos",
                "/StaffPersonals",
                "/TeachingGroups",
                "/TimeTableSubjects",
                "/GradingAssignments",
                "/StudentContactPersonals"
        );
    }

    @Provide
    Arbitrary<String> defaultZoneIds() {
        // Generate realistic zone IDs that environments might configure
        return Arbitraries.of(
                "DuncanHigh2014",
                "WilsonSchool",
                "StateEducation",
                "DistrictA",
                "MainCampus",
                "TestZone"
        ).flatMap(prefix ->
                Arbitraries.integers().between(1, 999).map(n -> prefix + n)
        );
    }

    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.create(UUID::randomUUID);
    }
}
