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
 * Preservation Property Test — Zone Auth Bypass Fix (Property 2).
 *
 * <p><strong>Goal:</strong> Verify that non-zone skip rules and normal API auth
 * requirements are unchanged by the fix. These tests capture the CURRENT baseline
 * behavior on UNFIXED code and MUST PASS both before and after the fix.</p>
 *
 * <p><strong>Methodology:</strong> Observation-first — we observed that on unfixed code:
 * <ul>
 *   <li>{@code /internal/health} is skipped (returns {@code true})</li>
 *   <li>{@code /actuator/health} is skipped (returns {@code true})</li>
 *   <li>{@code GET /domainId/solutionId/tags} is skipped (returns {@code true})</li>
 *   <li>{@code POST /admin-users/login} is skipped (returns {@code true})</li>
 *   <li>{@code /v3/api-docs} is skipped (returns {@code true})</li>
 *   <li>{@code /swagger-ui/index.html} is skipped (returns {@code true})</li>
 *   <li>{@code /environments} is NOT skipped (returns {@code false})</li>
 *   <li>{@code /acl-entries} is NOT skipped (returns {@code false})</li>
 * </ul>
 *
 * <p><strong>EXPECTED OUTCOME:</strong> All tests PASS on unfixed code (confirms baseline).</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6</strong></p>
 *
 * @see org.sifunity.broker.environment.filter.AuthorizationFilter
 */
@Tag("zone-auth-bypass-fix")
@Tag("preservation")
class ZoneAuthBypassPreservationTest {

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

    // ========== Property: Internal endpoints are no longer skipped (now token-verified) ==========

    /**
     * Preservation — Internal endpoints ({@code /internal/**}) are NO LONGER skipped
     * by shouldNotFilter. They now go through doFilterInternal where the
     * X-Internal-Service-Token header is verified.
     *
     * <p><strong>Validates: Requirements 3.3</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: Internal endpoints (/internal/**) are NOT skipped (token-verified in doFilterInternal)")
    void internalEndpointsAreNotSkipped(
            @ForAll("internalPaths") String path,
            @ForAll("httpMethods") String method) {

        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);

        boolean shouldSkip = invokeShouldNotFilter(request);

        assertThat(shouldSkip)
                .as("shouldNotFilter() must return false for %s %s — internal endpoints now require token verification",
                        method, path)
                .isFalse();
    }

    // ========== Property: Actuator endpoints continue to be skipped ==========

    /**
     * Preservation — Actuator endpoints ({@code /actuator/**}) continue to bypass
     * the AuthorizationFilter regardless of HTTP method.
     *
     * <p><strong>Validates: Requirements 3.6</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: Actuator endpoints (/actuator/**) continue to be skipped")
    void actuatorEndpointsContinueToBeSkipped(
            @ForAll("actuatorPaths") String path,
            @ForAll("httpMethods") String method) {

        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);

        boolean shouldSkip = invokeShouldNotFilter(request);

        assertThat(shouldSkip)
                .as("shouldNotFilter() must return true for %s %s — actuator endpoints are always skipped",
                        method, path)
                .isTrue();
    }

    // ========== Property: Tag listing endpoints continue to be skipped ==========

    /**
     * Preservation — Tag listing endpoints ({@code GET .+/tags$}) continue to bypass
     * the AuthorizationFilter.
     *
     * <p><strong>Validates: Requirements 3.4</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: Tag listing endpoints (GET .+/tags) continue to be skipped")
    void tagListingEndpointsContinueToBeSkipped(
            @ForAll("tagPaths") String path) {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);

        boolean shouldSkip = invokeShouldNotFilter(request);

        assertThat(shouldSkip)
                .as("shouldNotFilter() must return true for GET %s — tag listing is always skipped",
                        path)
                .isTrue();
    }

    // ========== Property: Admin endpoints continue to be skipped ==========

    /**
     * Preservation — Admin endpoints (login, session, proxy) continue to bypass
     * the AuthorizationFilter.
     *
     * <p><strong>Validates: Requirements 3.6</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: Admin endpoints (login, session, proxy) continue to be skipped")
    void adminEndpointsContinueToBeSkipped(
            @ForAll("adminEndpoints") MockHttpServletRequest request) {

        boolean shouldSkip = invokeShouldNotFilter(request);

        assertThat(shouldSkip)
                .as("shouldNotFilter() must return true for %s %s — admin endpoints are always skipped",
                        request.getMethod(), request.getRequestURI())
                .isTrue();
    }

    // ========== Property: Swagger/OpenAPI endpoints continue to be skipped ==========

    /**
     * Preservation — Swagger and OpenAPI documentation endpoints continue to bypass
     * the AuthorizationFilter.
     *
     * <p><strong>Validates: Requirements 3.6</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: Swagger/OpenAPI endpoints continue to be skipped")
    void swaggerEndpointsContinueToBeSkipped(
            @ForAll("swaggerPaths") String path,
            @ForAll("httpMethods") String method) {

        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);

        boolean shouldSkip = invokeShouldNotFilter(request);

        assertThat(shouldSkip)
                .as("shouldNotFilter() must return true for %s %s — Swagger/docs endpoints are always skipped",
                        method, path)
                .isTrue();
    }

    // ========== Property: Normal API endpoints continue to require auth ==========

    /**
     * Preservation — Normal API endpoints ({@code /environments}, {@code /acl-entries},
     * {@code /domains}) continue to require authentication (are NOT skipped).
     *
     * <p><strong>Validates: Requirements 3.1, 3.2</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: Normal API endpoints (/environments, /acl-entries, /domains) continue to require auth")
    void normalApiEndpointsContinueToRequireAuth(
            @ForAll("normalApiPaths") String path,
            @ForAll("httpMethods") String method) {

        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);

        boolean shouldSkip = invokeShouldNotFilter(request);

        assertThat(shouldSkip)
                .as("shouldNotFilter() must return false for %s %s — normal API endpoints require auth",
                        method, path)
                .isFalse();
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> internalPaths() {
        return Arbitraries.of(
                "/internal/health",
                "/internal/status",
                "/internal/metrics",
                "/internal/ready",
                "/internal/info");
    }

    @Provide
    Arbitrary<String> actuatorPaths() {
        return Arbitraries.of(
                "/actuator/health",
                "/actuator/info",
                "/actuator/metrics",
                "/actuator/prometheus",
                "/actuator/env");
    }

    @Provide
    Arbitrary<String> tagPaths() {
        return Arbitraries.of(
                "/domainId/solutionId/tags",
                "/school-district/prod-solution/tags",
                "/myDomain/mySolution/tags",
                "/district42/integration/tags",
                "/org-abc/staging/tags");
    }

    @Provide
    Arbitrary<MockHttpServletRequest> adminEndpoints() {
        return Arbitraries.of(
                createRequest("POST", "/admin-users/login"),
                createRequest("GET", "/admin-users/sessions/abc123"),
                createRequest("DELETE", "/admin-users/sessions/session-xyz"),
                createRequest("GET", "/admin-proxy/environments"),
                createRequest("POST", "/admin-proxy/domains"));
    }

    @Provide
    Arbitrary<String> swaggerPaths() {
        return Arbitraries.of(
                "/v3/api-docs",
                "/v3/api-docs/swagger-config",
                "/swagger-ui/index.html",
                "/swagger-ui/swagger-ui.css",
                "/webjars/swagger-ui/index.html");
    }

    @Provide
    Arbitrary<String> normalApiPaths() {
        return Arbitraries.of(
                "/environments",
                "/acl-entries",
                "/domains",
                "/domains/test-domain/solutions",
                "/domains/test-domain/solutions/test-solution/environments");
    }

    @Provide
    Arbitrary<String> httpMethods() {
        return Arbitraries.of("GET", "POST", "PUT", "DELETE", "PATCH");
    }

    /**
     * Helper to create a MockHttpServletRequest with both method/URI set properly.
     */
    private static MockHttpServletRequest createRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        return request;
    }
}
