// SPDX-License-Identifier: MIT
package org.sifunity.gateway.property;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
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

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bug Condition Exploration Test — Gateway permission cache (Property 1).
 *
 * <p><strong>Bug Description:</strong>
 * {@code EnvironmentServiceClient.checkPermission} caches {@link PermissionCheckResponse}
 * results in a manual Caffeine cache keyed only by
 * {@code environmentId : serviceName : accessType}. Because the key omits {@code zoneId}
 * and {@code contextId}, a decision cached for one zone/context is incorrectly served for a
 * differently-scoped request that shares the same environment/service/accessType. When the
 * first decision was {@code granted=true}, this is a cross-zone / cross-context authorization
 * bypass.</p>
 *
 * <p><strong>Expected Behavior (encoded here):</strong> after driving {@code checkPermission(A)}
 * to seed the cache, {@code checkPermission(B)} for a different zone/context SHALL reflect B's
 * OWN scope (a cache miss that consults the backend), i.e. {@code cacheKey'(A) <> cacheKey'(B)}.</p>
 *
 * <p><strong>CRITICAL:</strong> These tests MUST FAIL on unfixed code — the stale first-scope
 * decision is served for the second scope. Failure confirms the bug exists. They will PASS once
 * the cache key is widened to include {@code zoneId} and the normalized {@code contextId}.</p>
 *
 * <p><strong>Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5</strong></p>
 *
 * @see org.sifunity.gateway.client.EnvironmentServiceClient
 */
@Tag("acl-permission-cache-key-fix")
@Tag("bug-condition-exploration")
class AclPermissionCacheKeyBugConditionExplorationTest {

    private static final String ZONE_A = "district-1";
    private static final String ZONE_B = "district-2";
    private static final String CONTEXT_CURRENT = "CURRENT";

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
     * Bug Condition — cross-zone cache reuse.
     *
     * <p>The backend grants permission for {@code zone=district-1} and denies it for
     * {@code zone=district-2}. After seeding the cache with the district-1 (granted) decision,
     * a district-2 request must be evaluated on its own scope and return {@code granted=false}.</p>
     *
     * <p><strong>EXPECTED OUTCOME on unfixed code: FAILS</strong> — the cached district-1
     * {@code granted=true} is served for district-2.</p>
     *
     * <p><strong>Validates: Requirements 2.1, 2.2, 2.5</strong></p>
     */
    @Property(tries = 2)
    @Label("Bug Condition: gateway cross-zone request must not reuse another zone's cached decision")
    void crossZoneMustNotReuseCachedDecision(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("serviceNames") String serviceName,
            @ForAll("accessTypes") AccessType accessType) {

        WireMock.reset();
        // Backend: granted for district-1, denied for district-2 (differentiated by zoneId).
        wireMockServer.stubFor(post(urlEqualTo("/internal/check-permission"))
                .atPriority(1)
                .withRequestBody(matchingJsonPath("$.zoneId", equalTo(ZONE_A)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"granted\": true}")));
        wireMockServer.stubFor(post(urlEqualTo("/internal/check-permission"))
                .atPriority(2)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"granted\": false}")));

        // Fresh client => fresh cache for each generated example.
        EnvironmentServiceClient client =
                new EnvironmentServiceClient(WebClient.builder(), baseUrl, "test-internal-token", null, null, io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("test"));

        PermissionCheckRequest districtOne = new PermissionCheckRequest(
                environmentId, serviceName, ZONE_A, CONTEXT_CURRENT, accessType);
        PermissionCheckRequest districtTwo = new PermissionCheckRequest(
                environmentId, serviceName, ZONE_B, CONTEXT_CURRENT, accessType);

        // Seed the cache with the district-1 (granted) decision.
        PermissionCheckResponse seeded = client.checkPermission(districtOne).block();
        assertThat(seeded).isNotNull();
        assertThat(seeded.granted()).isTrue();

        // District-2 must be evaluated on its own scope: the backend denies it.
        PermissionCheckResponse districtTwoResult = client.checkPermission(districtTwo).block();

        assertThat(districtTwoResult).isNotNull();
        assertThat(districtTwoResult.granted())
                .as("checkPermission(env=%s, svc=%s, zone=%s, %s) must reflect district-2's own scope "
                                + "(granted=false), not the cached district-1 decision",
                        environmentId, serviceName, ZONE_B, accessType)
                .isFalse();
    }

    /**
     * Bug Condition — cross-context cache reuse (including null vs non-null context).
     *
     * <p>The backend grants permission for {@code contextId=CURRENT} and denies it for every other
     * context (including a null/absent context). After seeding the cache with the CURRENT (granted)
     * decision, a null-context request must be evaluated on its own scope and return
     * {@code granted=false}.</p>
     *
     * <p><strong>EXPECTED OUTCOME on unfixed code: FAILS</strong> — the cached CURRENT
     * {@code granted=true} is served for the null-context request.</p>
     *
     * <p><strong>Validates: Requirements 2.3, 2.4, 2.5</strong></p>
     */
    @Property(tries = 2)
    @Label("Bug Condition: gateway cross-context request (null vs CURRENT) must not reuse cached decision")
    void crossContextMustNotReuseCachedDecision(
            @ForAll("environmentIds") UUID environmentId,
            @ForAll("serviceNames") String serviceName,
            @ForAll("accessTypes") AccessType accessType) {

        WireMock.reset();
        // Backend: granted only for contextId=CURRENT, denied otherwise (incl. null context).
        wireMockServer.stubFor(post(urlEqualTo("/internal/check-permission"))
                .atPriority(1)
                .withRequestBody(matchingJsonPath("$.contextId", equalTo(CONTEXT_CURRENT)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"granted\": true}")));
        wireMockServer.stubFor(post(urlEqualTo("/internal/check-permission"))
                .atPriority(2)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"granted\": false}")));

        EnvironmentServiceClient client =
                new EnvironmentServiceClient(WebClient.builder(), baseUrl, "test-internal-token", null, null, io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("test"));

        PermissionCheckRequest currentContext = new PermissionCheckRequest(
                environmentId, serviceName, ZONE_A, CONTEXT_CURRENT, accessType);
        PermissionCheckRequest nullContext = new PermissionCheckRequest(
                environmentId, serviceName, ZONE_A, null, accessType);

        // Seed the cache with the CURRENT-context (granted) decision.
        PermissionCheckResponse seeded = client.checkPermission(currentContext).block();
        assertThat(seeded).isNotNull();
        assertThat(seeded.granted()).isTrue();

        // Null-context request must be evaluated on its own scope: the backend denies it.
        PermissionCheckResponse nullContextResult = client.checkPermission(nullContext).block();

        assertThat(nullContextResult).isNotNull();
        assertThat(nullContextResult.granted())
                .as("checkPermission(env=%s, svc=%s, zone=%s, ctx=null, %s) must reflect the null-context "
                                + "scope (granted=false), not the cached CURRENT-context decision",
                        environmentId, serviceName, ZONE_A, accessType)
                .isFalse();
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
    Arbitrary<AccessType> accessTypes() {
        return Arbitraries.of(AccessType.values());
    }
}
