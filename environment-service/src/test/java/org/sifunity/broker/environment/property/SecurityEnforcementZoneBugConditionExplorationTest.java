// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.controller.ZoneController;
import org.sifunity.broker.environment.dto.ContextCreateBody;
import org.sifunity.broker.environment.dto.ContextCreateRequest;
import org.sifunity.broker.environment.dto.ContextCreateRequestWrapper;
import org.sifunity.broker.environment.dto.ContextDTO;
import org.sifunity.broker.environment.dto.ZoneCreateBody;
import org.sifunity.broker.environment.dto.ZoneCreateRequest;
import org.sifunity.broker.environment.dto.ZoneCreateRequestWrapper;
import org.sifunity.broker.environment.dto.ZoneDTO;
import org.sifunity.broker.environment.service.ZoneService;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug Condition Exploration Test for Security Enforcement Gaps 1 & 2.
 *
 * <p><strong>CRITICAL: These tests MUST FAIL on unfixed code — failure confirms bugs exist.</strong></p>
 *
 * <p>Gap 1: Non-admin environment POSTs to /{domainId}/{solutionId}/zones — should return 403
 * but currently returns 201 because ZoneController.createZone() has no authorization check.</p>
 *
 * <p>Gap 2: Non-admin environment POSTs to /{domainId}/{solutionId}/zones/{zoneId}/contexts —
 * should return 403 but currently returns 201 because ZoneController.createContext() has no
 * authorization check.</p>
 *
 * <p>These tests encode the EXPECTED behavior. When they pass after the fix, it confirms
 * the security gaps are closed.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2</strong></p>
 */
class SecurityEnforcementZoneBugConditionExplorationTest {

    // ========== Gap 1: Zone Creation by Non-Admin ==========

    /**
     * Property 1: Bug Condition — Non-admin zone creation should be rejected with 403.
     *
     * <p>For any non-admin, non-broker-services environment (type DIRECT or BROKERED),
     * a POST to /{domainId}/{solutionId}/zones SHALL return HTTP 403 Forbidden.</p>
     *
     * <p><strong>Expected on UNFIXED code:</strong> FAILS — returns 201 Created (no admin check).</p>
     * <p><strong>Expected on FIXED code:</strong> PASSES — returns 403 Forbidden.</p>
     *
     * <p><strong>Validates: Requirements 1.1</strong></p>
     */
    @Property(tries = 20)
    @Label("Gap 1: Non-admin POST /zones should return 403 (currently returns 201)")
    void nonAdminZoneCreationShouldReturn403(
            @ForAll("environmentTypes") String environmentType,
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("zoneIds") String zoneId) {

        // Arrange: ZoneController with mocked ZoneService that creates zones successfully
        ZoneService zoneService = mock(ZoneService.class);
        ZoneDTO createdZone = new ZoneDTO(
                UUID.randomUUID(), solutionId, zoneId, "Test zone", Instant.now());
        when(zoneService.createZone(eq(domainId), any(ZoneCreateRequest.class))).thenReturn(createdZone);

        ZoneController controller = new ZoneController(zoneService);

        // The request comes from a non-admin environment (DIRECT or BROKERED).
        // On FIXED code, the controller checks X-Environment-Fingerprint / admin status
        // and returns 403 for non-admin callers.
        ZoneCreateRequestWrapper request =
                new ZoneCreateRequestWrapper(new ZoneCreateBody(zoneId, "Test zone"));

        // Act: Call createZone (SIF singular subpath) as non-admin (null fingerprint = non-privileged caller).
        // Non-admin rejection is raised as a SifBrokerException (rendered as a SifErrorResponse
        // with HTTP 403 by the GlobalExceptionHandler), so we assert the thrown exception's status.
        SifBrokerException ex = catchThrowableOfType(
                () -> controller.createZone(domainId, solutionId, request, null),
                SifBrokerException.class);

        // Assert: Non-admin callers should be rejected with 403 Forbidden
        assertThat(ex)
                .as("Non-admin %s environment POST to /%s/%s/zones should be rejected with 403 Forbidden",
                        environmentType, domainId, solutionId)
                .isNotNull();
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ========== Gap 2: Context Creation by Non-Admin ==========

    /**
     * Property 1: Bug Condition — Non-admin context creation should be rejected with 403.
     *
     * <p>For any non-admin, non-broker-services environment (type DIRECT or BROKERED),
     * a POST to /{domainId}/{solutionId}/zones/{zoneId}/contexts SHALL return HTTP 403 Forbidden.</p>
     *
     * <p><strong>Expected on UNFIXED code:</strong> FAILS — returns 201 Created (no admin check).</p>
     * <p><strong>Expected on FIXED code:</strong> PASSES — returns 403 Forbidden.</p>
     *
     * <p><strong>Validates: Requirements 1.2</strong></p>
     */
    @Property(tries = 20)
    @Label("Gap 2: Non-admin POST /zones/{zoneId}/contexts should return 403 (currently returns 201)")
    void nonAdminContextCreationShouldReturn403(
            @ForAll("environmentTypes") String environmentType,
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("zoneIds") String zoneId,
            @ForAll("contextIds") String contextId) {

        // Arrange: ZoneController with mocked ZoneService that creates contexts successfully
        ZoneService zoneService = mock(ZoneService.class);
        ContextDTO createdContext = new ContextDTO(
                UUID.randomUUID(), zoneId, contextId, "Test context", Instant.now());
        when(zoneService.createContext(eq(domainId), eq(solutionId), eq(zoneId), any(ContextCreateRequest.class)))
                .thenReturn(createdContext);

        ZoneController controller = new ZoneController(zoneService);

        // The request comes from a non-admin environment (DIRECT or BROKERED).
        ContextCreateRequestWrapper request =
                new ContextCreateRequestWrapper(new ContextCreateBody(contextId, "Test context"));

        // Act: Call createContext (SIF singular subpath) as non-admin (null fingerprint = non-privileged caller).
        // Non-admin rejection is raised as a SifBrokerException (rendered as a SifErrorResponse
        // with HTTP 403 by the GlobalExceptionHandler), so we assert the thrown exception's status.
        SifBrokerException ex = catchThrowableOfType(
                () -> controller.createContext(domainId, solutionId, zoneId, request, null),
                SifBrokerException.class);

        // Assert: Non-admin callers should be rejected with 403 Forbidden
        assertThat(ex)
                .as("Non-admin %s environment POST to /%s/%s/zones/%s/contexts should be rejected with 403 Forbidden",
                        environmentType, domainId, solutionId, zoneId)
                .isNotNull();
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> environmentTypes() {
        return Arbitraries.of("DIRECT", "BROKERED");
    }

    @Provide
    Arbitrary<String> domainIds() {
        return Arbitraries.of("test-domain", "school-district-1", "state-edu");
    }

    @Provide
    Arbitrary<String> solutionIds() {
        return Arbitraries.of("prod-solution", "test-solution", "staging");
    }

    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.of("zone1", "default-zone", "test-zone");
    }

    @Provide
    Arbitrary<String> contextIds() {
        return Arbitraries.of("ctx-default", "ctx-test", "ctx-staging");
    }
}
