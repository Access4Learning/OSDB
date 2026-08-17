// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.environment.controller.AlertsController;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.AlertCreateRequest;
import org.sifunity.broker.environment.service.AlertsService;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug Condition Exploration Test for Alerts Service 500 Error.
 *
 * <p><strong>CRITICAL: These tests MUST FAIL on unfixed code — failure confirms the NPE bug exists.</strong></p>
 *
 * <p>Bug: POST to /{domainId}/{solutionId}/alerts/alert with a flat JSON body (missing "alert"
 * wrapper key) causes NullPointerException at request.alert().reporter() because Jackson
 * silently sets alert=null when no top-level "alert" key exists.</p>
 *
 * <p>On UNFIXED code: controller throws NullPointerException (caught by global handler as 500 INTERNAL_001).</p>
 * <p>On FIXED code: controller throws SifBrokerException with 400 ALERT_001 (null guard rejects missing wrapper).</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 2.1, 2.2</strong></p>
 */
class AlertsBugConditionExplorationTest {

    // ========== Property 1: Missing wrapper returns 400 ALERT_001 ==========

    /**
     * Property 1: Bug Condition — Missing Alert Wrapper causes SifBrokerException (400 ALERT_001).
     *
     * <p>For any AlertCreateRequest where alert() is null (simulating flat body deserialization),
     * the controller SHALL throw SifBrokerException with code ALERT_001 and HTTP 400.</p>
     *
     * <p><strong>Expected on UNFIXED code:</strong> FAILS — throws NullPointerException instead.</p>
     * <p><strong>Expected on FIXED code:</strong> PASSES — throws SifBrokerException with 400 ALERT_001.</p>
     *
     * <p><strong>Validates: Requirements 2.1</strong></p>
     */
    @Property(tries = 20)
    @Label("Missing wrapper: POST /alerts/alert with null alert() should throw SifBrokerException 400 ALERT_001 (currently NPE)")
    void missingWrapperShouldThrowSifBrokerException(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) {

        // Arrange: Controller with mocked dependencies
        AlertsService alertsService = mock(AlertsService.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        when(authorizationService.isAdmin(any())).thenReturn(false);

        AlertsController controller = new AlertsController(alertsService, authorizationService);

        // Simulate flat body: Jackson produces AlertCreateRequest with alert=null
        AlertCreateRequest requestWithNullAlert = new AlertCreateRequest(null);

        // Mock HttpServletRequest with requester attribute and no serviceType header
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        Environment mockEnvironment = mock(Environment.class);
        when(mockEnvironment.getId()).thenReturn(UUID.randomUUID());
        when(httpRequest.getAttribute("requester")).thenReturn(mockEnvironment);
        when(httpRequest.getHeader("serviceType")).thenReturn(null);

        // Act: Call createAlert with null-alert wrapper
        Throwable thrown = catchThrowable(() ->
            controller.createAlert(domainId, solutionId, requestWithNullAlert, httpRequest));

        // Assert: Should throw SifBrokerException with 400 BAD_REQUEST and ALERT_001
        // On UNFIXED code: throws NullPointerException at request.alert().reporter()
        // On FIXED code: throws SifBrokerException("ALERT_001", 400, ...)
        assertThat(thrown)
                .as("POST /%s/%s/alerts/alert with null alert() should throw SifBrokerException, not NPE",
                        domainId, solutionId)
                .isInstanceOf(org.sifunity.broker.common.exception.SifBrokerException.class);

        org.sifunity.broker.common.exception.SifBrokerException sifEx =
                (org.sifunity.broker.common.exception.SifBrokerException) thrown;

        assertThat(sifEx.getErrorCode())
                .as("Error code should be ALERT_001")
                .isEqualTo("ALERT_001");

        assertThat(sifEx.getHttpStatus())
                .as("HTTP status should be 400 Bad Request")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Explicit null wrapper test ({"alert":null} deserialization).
     *
     * <p>Same bug condition — even when the "alert" key exists but has null value,
     * the controller should detect null and return 400 ALERT_001.</p>
     *
     * <p><strong>Validates: Requirements 2.1</strong></p>
     */
    @Property(tries = 20)
    @Label("Explicit null wrapper: POST /alerts/alert with {\"alert\":null} should throw SifBrokerException 400")
    void explicitNullWrapperShouldThrowSifBrokerException(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) {

        // Arrange
        AlertsService alertsService = mock(AlertsService.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        when(authorizationService.isAdmin(any())).thenReturn(false);

        AlertsController controller = new AlertsController(alertsService, authorizationService);

        // {"alert":null} — Jackson sets alert field to null explicitly
        AlertCreateRequest requestWithExplicitNull = new AlertCreateRequest(null);

        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        Environment mockEnvironment = mock(Environment.class);
        when(mockEnvironment.getId()).thenReturn(UUID.randomUUID());
        when(httpRequest.getAttribute("requester")).thenReturn(mockEnvironment);
        when(httpRequest.getHeader("serviceType")).thenReturn(null);

        // Act
        Throwable thrown = catchThrowable(() ->
            controller.createAlert(domainId, solutionId, requestWithExplicitNull, httpRequest));

        // Assert: SifBrokerException with ALERT_001, not NPE
        assertThat(thrown)
                .as("POST /%s/%s/alerts/alert with explicit null alert should throw SifBrokerException",
                        domainId, solutionId)
                .isInstanceOf(org.sifunity.broker.common.exception.SifBrokerException.class);

        org.sifunity.broker.common.exception.SifBrokerException sifEx =
                (org.sifunity.broker.common.exception.SifBrokerException) thrown;

        assertThat(sifEx.getErrorCode()).isEqualTo("ALERT_001");
        assertThat(sifEx.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> domainIds() {
        return Arbitraries.of("test-domain", "school-district-1", "state-edu", "prod-domain");
    }

    @Provide
    Arbitrary<String> solutionIds() {
        return Arbitraries.of("prod-solution", "test-solution", "staging", "dev-env");
    }
}
