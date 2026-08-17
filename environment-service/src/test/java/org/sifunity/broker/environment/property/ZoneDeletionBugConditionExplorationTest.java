// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.environment.controller.GlobalExceptionHandler;
import org.sifunity.broker.environment.controller.ZoneController;
import org.sifunity.broker.environment.exception.ZoneNotFoundException;
import org.sifunity.broker.environment.service.ZoneService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * Bug Condition Exploration Test for Zone Deletion.
 *
 * <p><strong>CRITICAL: These tests MUST FAIL on unfixed code — failure confirms the bug exists.</strong></p>
 *
 * <p>Bug: {@code ZoneController} has no {@code @DeleteMapping("/{zoneId}")} endpoint.
 * Any DELETE request to {@code /{domainId}/{solutionId}/zones/{zoneId}} returns 405 Method Not Allowed
 * (or 500 via static-resource fallback in full Spring context) instead of the expected status code.</p>
 *
 * <p>Bug Condition: {@code isBugCondition(X) = X.method = "DELETE" AND X.path matches
 * "/{domainId}/{solutionId}/zones/{zoneId}"}</p>
 *
 * <p>On UNFIXED code: all DELETE requests return 405 (no handler mapped).</p>
 * <p>On FIXED code: admin+existing→204, admin+nonexistent→404, non-admin→403, protected→409.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 1.4</strong></p>
 */
class ZoneDeletionBugConditionExplorationTest {

    private static final Set<String> NON_EXISTENT_ZONE_IDS = Set.of(
            "nonexistent-zone-xyz", "deleted-zone-999", "fake-zone", "no-such-zone");

    private MockMvc buildMockMvc() {
        ZoneService zoneService = mock(ZoneService.class);
        // Configure mock to throw ZoneNotFoundException for non-existent zone IDs
        for (String zoneId : NON_EXISTENT_ZONE_IDS) {
            doThrow(new ZoneNotFoundException(zoneId))
                    .when(zoneService).deleteZone(anyString(), anyString(), org.mockito.Mockito.eq(zoneId));
        }
        ZoneController controller = new ZoneController(zoneService);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ========== Property 1: Admin + existing zone + non-protected → 204 ==========

    /**
     * Property 1: Admin caller deleting an existing, non-protected zone should return 204.
     *
     * <p>On UNFIXED code: returns 405 (no @DeleteMapping exists) — test FAILS.</p>
     * <p>On FIXED code: returns 204 — test PASSES.</p>
     *
     * <p><strong>Validates: Requirements 1.1</strong></p>
     */
    @Property(tries = 20)
    @Label("Admin DELETE existing non-protected zone should return 204 (currently returns 405)")
    void adminDeleteExistingNonProtectedZoneShouldReturn204(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("nonProtectedZoneIds") String zoneId) throws Exception {

        MockMvc mockMvc = buildMockMvc();

        MvcResult result = mockMvc.perform(delete("/{domainId}/{solutionId}/zones/{zoneId}",
                        domainId, solutionId, zoneId)
                        .header("X-Environment-Fingerprint", "broker-services"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("Admin DELETE /%s/%s/zones/%s should return 204, got %d",
                        domainId, solutionId, zoneId, result.getResponse().getStatus())
                .isEqualTo(204);
    }

    // ========== Property 2: Admin + non-existent zone → 404 ==========

    /**
     * Property 2: Admin caller deleting a non-existent zone should return 404.
     *
     * <p>On UNFIXED code: returns 405 (no @DeleteMapping exists) — test FAILS.</p>
     * <p>On FIXED code: returns 404 — test PASSES.</p>
     *
     * <p><strong>Validates: Requirements 1.2</strong></p>
     */
    @Property(tries = 20)
    @Label("Admin DELETE non-existent zone should return 404 (currently returns 405)")
    void adminDeleteNonExistentZoneShouldReturn404(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("nonExistentZoneIds") String zoneId) throws Exception {

        MockMvc mockMvc = buildMockMvc();

        MvcResult result = mockMvc.perform(delete("/{domainId}/{solutionId}/zones/{zoneId}",
                        domainId, solutionId, zoneId)
                        .header("X-Environment-Fingerprint", "broker-services"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("Admin DELETE /%s/%s/zones/%s (non-existent) should return 404, got %d",
                        domainId, solutionId, zoneId, result.getResponse().getStatus())
                .isEqualTo(404);
    }

    // ========== Property 3: Non-admin caller → 403 ==========

    /**
     * Property 3: Non-admin caller deleting any zone should return 403.
     *
     * <p>On UNFIXED code: returns 405 (no @DeleteMapping exists) — test FAILS.</p>
     * <p>On FIXED code: returns 403 — test PASSES.</p>
     *
     * <p><strong>Validates: Requirements 1.3</strong></p>
     */
    @Property(tries = 20)
    @Label("Non-admin DELETE zone should return 403 (currently returns 405)")
    void nonAdminDeleteZoneShouldReturn403(
            @ForAll("nonAdminDomainIds") String domainId,
            @ForAll("nonAdminSolutionIds") String solutionId,
            @ForAll("nonProtectedZoneIds") String zoneId) throws Exception {

        MockMvc mockMvc = buildMockMvc();

        // Non-admin: not GLOBAL_DOMAIN/GLOBAL_SOLUTION, and no broker-services fingerprint
        MvcResult result = mockMvc.perform(delete("/{domainId}/{solutionId}/zones/{zoneId}",
                        domainId, solutionId, zoneId)
                        .header("X-Environment-Fingerprint", "some-non-admin-env"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("Non-admin DELETE /%s/%s/zones/%s should return 403, got %d",
                        domainId, solutionId, zoneId, result.getResponse().getStatus())
                .isEqualTo(403);
    }

    // ========== Property 4: Protected zone (environment-global) → 409 ==========

    /**
     * Property 4: Any caller deleting the environment-global zone should return 409.
     *
     * <p>On UNFIXED code: returns 405 (no @DeleteMapping exists) — test FAILS.</p>
     * <p>On FIXED code: returns 409 — test PASSES.</p>
     *
     * <p><strong>Validates: Requirements 1.4</strong></p>
     */
    @Property(tries = 20)
    @Label("DELETE environment-global zone should return 409 (currently returns 405)")
    void deleteProtectedZoneShouldReturn409(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        MockMvc mockMvc = buildMockMvc();

        MvcResult result = mockMvc.perform(delete("/{domainId}/{solutionId}/zones/{zoneId}",
                        domainId, solutionId, "environment-global")
                        .header("X-Environment-Fingerprint", "broker-services"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("DELETE /%s/%s/zones/environment-global should return 409, got %d",
                        domainId, solutionId, result.getResponse().getStatus())
                .isEqualTo(409);
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> domainIds() {
        return Arbitraries.of("GLOBAL_DOMAIN", "catranscript", "test-domain", "edu-district-01");
    }

    @Provide
    Arbitrary<String> solutionIds() {
        return Arbitraries.of("GLOBAL_SOLUTION", "pilot", "production", "test-solution");
    }

    @Provide
    Arbitrary<String> nonAdminDomainIds() {
        return Arbitraries.of("catranscript", "test-domain", "edu-district-01", "some-tenant");
    }

    @Provide
    Arbitrary<String> nonAdminSolutionIds() {
        return Arbitraries.of("pilot", "production", "test-solution", "staging");
    }

    @Provide
    Arbitrary<String> nonProtectedZoneIds() {
        return Arbitraries.of("mockdxterazone", "school-zone-1", "district-zone", "test-zone-abc");
    }

    @Provide
    Arbitrary<String> nonExistentZoneIds() {
        return Arbitraries.of("nonexistent-zone-xyz", "deleted-zone-999", "fake-zone", "no-such-zone");
    }
}
