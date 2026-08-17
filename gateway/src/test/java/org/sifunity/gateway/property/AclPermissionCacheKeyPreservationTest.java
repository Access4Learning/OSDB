// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.dto.PermissionCheckRequest;
import org.sifunity.broker.common.dto.PermissionCheckResponse;
import org.sifunity.gateway.client.EnvironmentServiceClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preservation Property Test — Gateway permission cache (Property 2).
 *
 * <p><strong>Property 2 — Preservation: Same-Scope Sharing and Unchanged Decisions.</strong>
 * For any pair of permission-check requests where the bug condition does NOT hold
 * (identical {@code environmentId}, {@code serviceName}, {@code accessType}, {@code zoneId}, and
 * normalized {@code contextId}), the gateway Caffeine cache SHALL share a single cache entry
 * (one backend call, the second served from cache) and return the same granted/denied decision
 * the original code returns for that scope. TTL and eviction settings
 * ({@code expireAfterWrite=30s}, {@code maximumSize=10_000}) are unchanged.</p>
 *
 * <p><strong>Observation-first methodology:</strong> these behaviors were observed on the UNFIXED
 * code (same-scope requests already share one entry because identical scopes produce an identical
 * key even under the narrow key), then encoded here. They MUST PASS on unfixed code and MUST
 * continue to pass after the cache key is widened.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5</strong></p>
 *
 * @see org.sifunity.gateway.client.EnvironmentServiceClient
 */
@Tag("acl-permission-cache-key-fix")
@Tag("preservation")
class AclPermissionCacheKeyPreservationTest {

    private static final String CHECK_PERMISSION_URL = "/internal/check-permission";

    private WireMockServer wireMockServer;
    private String baseUrl;

    @BeforeProperty
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
        baseUrl = "http://localhost:" + wireMockServer.port();
    }

    @AfterProperty
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    /**
     * Preservation — same-scope requests share one cache entry and return the same decision.
     *
     * <p>Two requests identical across all five key dimensions must result in exactly ONE backend
     * call (the second served from cache) and both must reflect the backend decision.</p>
     *
     * <p><strong>EXPECTED OUTCOME on unfixed code: PASSES.</strong></p>
     *
     * <p><strong>Validates: Requirements 3.1, 3.2</strong></p>
     */
    @Property(tries = 2)
    @Label("Preservation: gateway same-scope requests share one cache entry and same decision")
    void sameScopeSharesOneCacheEntryAndSameDecision(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("serviceNames") String serviceName,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId,
            @ForAll("accessTypes") AccessType accessType,
            @ForAll boolean granted) {

        WireMock.reset();
        wireMockServer.stubFor(post(urlEqualTo(CHECK_PERMISSION_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"granted\": " + granted + "}")));

        EnvironmentServiceClient client =
                new EnvironmentServiceClient(WebClient.builder(), baseUrl, "test-internal-token", null, null, io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("test"));

        PermissionCheckRequest requestA = new PermissionCheckRequest(
                environmentId, serviceName, zoneId, contextId, accessType);
        // Distinct-but-scope-identical instance: NOT the same object reference.
        PermissionCheckRequest requestB = new PermissionCheckRequest(
                environmentId, serviceName, zoneId, contextId, accessType);

        PermissionCheckResponse first = client.checkPermission(requestA).block();
        PermissionCheckResponse second = client.checkPermission(requestB).block();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();

        // Same-scope pair shares one entry: exactly one backend call, second served from cache.
        wireMockServer.verify(1, postRequestedFor(urlEqualTo(CHECK_PERMISSION_URL)));

        // Decision unchanged and consistent across the shared entry.
        assertThat(first.granted()).isEqualTo(granted);
        assertThat(second.granted()).isEqualTo(granted);
        assertThat(second).isEqualTo(first);
    }

    /**
     * Preservation — TTL and eviction settings unchanged.
     *
     * <p>The gateway permission cache keeps {@code expireAfterWrite=30s} and
     * {@code maximumSize=10_000}. This is orthogonal to the cache key shape and must not change.</p>
     *
     * <p><strong>Validates: Requirement 3.3</strong></p>
     */
    @Example
    @Label("Preservation: gateway cache TTL (30s) and max-size (10_000) unchanged")
    void gatewayCacheTtlAndMaxSizeUnchanged() throws Exception {
        EnvironmentServiceClient client =
                new EnvironmentServiceClient(WebClient.builder(), baseUrl, "test-internal-token", null, null, io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("test"));

        Field cacheField = EnvironmentServiceClient.class.getDeclaredField("permissionCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<String, PermissionCheckResponse> cache =
                (Cache<String, PermissionCheckResponse>) cacheField.get(client);

        assertThat(cache.policy().expireAfterWrite())
                .as("gateway permission cache must configure expire-after-write")
                .isPresent();
        assertThat(cache.policy().expireAfterWrite().get().getExpiresAfter(TimeUnit.SECONDS))
                .as("gateway permission cache TTL must remain 30s")
                .isEqualTo(30);

        assertThat(cache.policy().eviction())
                .as("gateway permission cache must configure size-based eviction")
                .isPresent();
        assertThat(cache.policy().eviction().get().getMaximum())
                .as("gateway permission cache max-size must remain 10_000")
                .isEqualTo(10_000L);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<UUID> environmentIds() {
        return Arbitraries.randomValue(random -> UUID.randomUUID());
    }

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.of(
                "StudentPersonal", "DataExport", "DataImport", "StudentLocator",
                "GradeSync", "AttendanceReporter", "RosterSync", "AssessmentResults");
    }

    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.of(
                "district-1", "district-2", "district-3", "school-a", "school-b", "state-zone");
    }

    @Provide
    Arbitrary<String> contextIds() {
        // Includes null to exercise the wildcard/null-context scope (must be preserved).
        return Arbitraries.of("CURRENT", "DEFAULT", "2024").injectNull(0.3);
    }

    @Provide
    Arbitrary<AccessType> accessTypes() {
        return Arbitraries.of(AccessType.values());
    }
}
