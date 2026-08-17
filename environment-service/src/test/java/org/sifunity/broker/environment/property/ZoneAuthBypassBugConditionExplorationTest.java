// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import jakarta.servlet.http.HttpServletRequest;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.sifunity.broker.environment.filter.AuthorizationFilter;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Bug Condition Exploration Test — Zone Auth Bypass (Property 1).
 *
 * <p><strong>Bug Description:</strong>
 * {@code AuthorizationFilter.shouldNotFilter()} uses the overly broad regex
 * {@code path.matches(".*zones.*")} to decide which requests bypass authentication.
 * This matches ANY URL containing the substring "zones" anywhere in the path,
 * regardless of HTTP method. All zone-related endpoints (including mutation operations
 * like POST and DELETE) are completely unauthenticated.</p>
 *
 * <p><strong>Expected Behavior (encoded here):</strong> {@code shouldNotFilter()} SHALL
 * return {@code false} for:
 * <ul>
 *   <li>Non-GET methods (POST, PUT, DELETE, PATCH) to zone paths</li>
 *   <li>GET requests to paths containing "zones" as substring but not matching exact
 *       connector patterns (e.g., /foozones, /zones-admin/settings)</li>
 *   <li>GET requests to exact connector patterns without valid X-Internal-Service-Token</li>
 * </ul>
 *
 * <p><strong>CRITICAL:</strong> This test MUST FAIL on unfixed code — the broad regex
 * returns {@code true} (skip auth) for ALL requests containing "zones" in the path.
 * Failure confirms the bug exists.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 2.2, 2.4</strong></p>
 *
 * @see org.sifunity.broker.environment.filter.AuthorizationFilter
 */
@Tag("zone-auth-bypass-fix")
@Tag("bug-condition-exploration")
class ZoneAuthBypassBugConditionExplorationTest {

    private AuthorizationFilter filter;
    private Method shouldNotFilterMethod;

    @BeforeProperty
    void setUp() throws Exception {
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
        AclEntryRepository aclEntryRepository = mock(AclEntryRepository.class);
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        DomainRepository domainRepository = mock(DomainRepository.class);

        filter = new AuthorizationFilter(
                authorizationService,
                environmentRepository,
                aclEntryRepository,
                solutionRepository,
                domainRepository,
                "test-internal-token");

        // Access protected shouldNotFilter method via reflection
        shouldNotFilterMethod = AuthorizationFilter.class
                .getDeclaredMethod("shouldNotFilter", HttpServletRequest.class);
        shouldNotFilterMethod.setAccessible(true);
    }

    /**
     * Invokes the protected shouldNotFilter method via reflection.
     */
    private boolean invokeShouldNotFilter(MockHttpServletRequest request) {
        try {
            return (boolean) shouldNotFilterMethod.invoke(filter, request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke shouldNotFilter", e);
        }
    }

    /**
     * Bug Condition — Non-GET methods to zone paths should NOT skip auth.
     *
     * <p>For any non-GET HTTP method (POST, PUT, DELETE, PATCH) to a path matching
     * {@code /{domainId}/{solutionId}/zones}, {@code shouldNotFilter()} SHALL return
     * {@code false} — these mutation operations require full HMAC authentication and
     * admin authorization.</p>
     *
     * <p><strong>EXPECTED OUTCOME on unfixed code: FAILS</strong> — the broad regex
     * {@code .*zones.*} matches the path and returns {@code true} (skips auth) regardless
     * of HTTP method.</p>
     *
     * <p><strong>Validates: Requirements 1.1, 1.2, 2.2</strong></p>
     */
    @Property(tries = 20)
    @Label("Bug Condition: Non-GET zone requests must not skip auth (shouldNotFilter returns false)")
    void nonGetZoneRequestsMustNotSkipAuth(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("mutationMethods") String method) {

        String path = "/" + domainId + "/" + solutionId + "/zones";

        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);

        boolean shouldSkip = invokeShouldNotFilter(request);

        assertThat(shouldSkip)
                .as("shouldNotFilter() must return false for %s %s — mutation operations require auth",
                        method, path)
                .isFalse();
    }

    /**
     * Bug Condition — GET requests to non-matching zone-substring paths should NOT skip auth.
     *
     * <p>For any GET request where the path contains "zones" as a substring but does NOT match
     * the exact connector patterns ({@code /{domainId}/{solutionId}/zones} or
     * {@code /{domainId}/{solutionId}/zones/{zoneId}/contexts}), {@code shouldNotFilter()}
     * SHALL return {@code false}.</p>
     *
     * <p><strong>EXPECTED OUTCOME on unfixed code: FAILS</strong> — the broad regex matches
     * any path containing "zones" anywhere.</p>
     *
     * <p><strong>Validates: Requirements 1.3, 2.4</strong></p>
     */
    @Property(tries = 20)
    @Label("Bug Condition: GET to non-matching zone-substring paths must not skip auth")
    void getNonMatchingZoneSubstringPathsMustNotSkipAuth(
            @ForAll("nonMatchingZonePaths") String path) {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);

        boolean shouldSkip = invokeShouldNotFilter(request);

        assertThat(shouldSkip)
                .as("shouldNotFilter() must return false for GET %s — path contains 'zones' but "
                        + "does not match exact connector patterns", path)
                .isFalse();
    }

    /**
     * Bug Condition — DELETE/PUT/PATCH to zone sub-resource paths should NOT skip auth.
     *
     * <p>For any non-GET method to zone sub-resource paths like
     * {@code /{domainId}/{solutionId}/zones/{zoneId}}, {@code shouldNotFilter()} SHALL
     * return {@code false}.</p>
     *
     * <p><strong>EXPECTED OUTCOME on unfixed code: FAILS</strong> — the broad regex matches
     * all paths containing "zones".</p>
     *
     * <p><strong>Validates: Requirements 1.1, 1.2, 2.2</strong></p>
     */
    @Property(tries = 20)
    @Label("Bug Condition: Non-GET to zone sub-resource paths must not skip auth")
    void nonGetToZoneSubResourcePathsMustNotSkipAuth(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("zoneIds") String zoneId,
            @ForAll("mutationMethods") String method) {

        String path = "/" + domainId + "/" + solutionId + "/zones/" + zoneId;

        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);

        boolean shouldSkip = invokeShouldNotFilter(request);

        assertThat(shouldSkip)
                .as("shouldNotFilter() must return false for %s %s — zone sub-resource mutations require auth",
                        method, path)
                .isFalse();
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> domainIds() {
        return Arbitraries.of(
                "testDomain", "school-district-1", "state-edu", "my-org", "district42");
    }

    @Provide
    Arbitrary<String> solutionIds() {
        return Arbitraries.of(
                "testSolution", "prod-solution", "staging", "dev-env", "integration");
    }

    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.of(
                "zone1", "default-zone", "test-zone", "district-zone", "school-a");
    }

    @Provide
    Arbitrary<String> mutationMethods() {
        return Arbitraries.of("POST", "PUT", "DELETE", "PATCH");
    }

    @Provide
    Arbitrary<String> nonMatchingZonePaths() {
        return Arbitraries.of(
                // "zones" as substring in non-matching positions
                "/foozones/bar",
                "/zones-admin/settings",
                "/api/v1/freezones/list",
                "/admin/zones-config",
                "/testDomain/testSolution/zones/zone1/invalid-sub",
                "/testDomain/testSolution/zonesExtra",
                "/x/y/zones/z/notcontexts",
                "/singlepath/zones-embedded"
        );
    }
}
