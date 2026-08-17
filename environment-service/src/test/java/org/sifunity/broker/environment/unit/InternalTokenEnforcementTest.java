// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.sifunity.broker.environment.filter.AuthorizationFilter;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link AuthorizationFilter} internal token enforcement.
 *
 * <p>Validates that:
 * <ul>
 *   <li>Requests to /internal/ paths without a token are rejected with 401</li>
 *   <li>Requests to /internal/ paths with an incorrect token are rejected with 401</li>
 *   <li>Requests to /internal/ paths with a valid token pass through to the filter chain</li>
 *   <li>Requests to non-internal paths are not rejected by internal token logic</li>
 * </ul>
 *
 * <p><b>Validates: Requirements 1.1, 1.5, 5.3</b></p>
 */
@DisplayName("AuthorizationFilter - Internal Token Enforcement")
class InternalTokenEnforcementTest {

    private static final String VALID_TOKEN = "test-internal-service-token-12345";
    private static final String WRONG_TOKEN = "wrong-token-value";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private AuthorizationFilter filter;

    @BeforeEach
    void setUp() {
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
                VALID_TOKEN);
    }

    @Test
    @DisplayName("No token on internal path → 401 with SIF error (AUTH_010)")
    void internalPath_noToken_returns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/validate-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull(); // chain was NOT invoked

        SifErrorResponse error = OBJECT_MAPPER.readValue(
                response.getContentAsString(), SifErrorResponseEnvelope.class).error();
        assertThat(error.subCode()).isEqualTo("AUTH_010");
        assertThat(error.message()).isNotBlank();
    }

    @Test
    @DisplayName("Wrong token on internal path → 401 with SIF error (AUTH_010)")
    void internalPath_wrongToken_returns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/validate-token");
        request.addHeader("X-Internal-Service-Token", WRONG_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull(); // chain was NOT invoked

        SifErrorResponse error = OBJECT_MAPPER.readValue(
                response.getContentAsString(), SifErrorResponseEnvelope.class).error();
        assertThat(error.subCode()).isEqualTo("AUTH_010");
    }

    @Test
    @DisplayName("Valid token on internal path → chain continues (no 401)")
    void internalPath_validToken_chainContinues() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/validate-token");
        request.addHeader("X-Internal-Service-Token", VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // Chain was invoked — filter did not short-circuit
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isNotEqualTo(401);
    }

    @Test
    @DisplayName("Direct access to /internal/environments/{id}/shared-secret without token → 401 (credential exfiltration blocked)")
    void sharedSecretEndpoint_noToken_returns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/internal/environments/550e8400-e29b-41d4-a716-446655440000/shared-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull(); // chain was NOT invoked

        SifErrorResponse error = OBJECT_MAPPER.readValue(
                response.getContentAsString(), SifErrorResponseEnvelope.class).error();
        assertThat(error.subCode()).isEqualTo("AUTH_010");
        assertThat(error.message()).isNotBlank();
    }

    @Test
    @DisplayName("Direct access to /internal/environments/{id}/session-token without token → 401 (credential exfiltration blocked)")
    void sessionTokenEndpoint_noToken_returns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/internal/environments/550e8400-e29b-41d4-a716-446655440000/session-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull(); // chain was NOT invoked

        SifErrorResponse error = OBJECT_MAPPER.readValue(
                response.getContentAsString(), SifErrorResponseEnvelope.class).error();
        assertThat(error.subCode()).isEqualTo("AUTH_010");
        assertThat(error.message()).isNotBlank();
    }

    @Test
    @DisplayName("Non-internal path without internal token → NOT rejected with AUTH_010")
    void nonInternalPath_noInternalToken_notRejectedByInternalLogic() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/some/other/path");
        // No X-Internal-Service-Token, no Authorization header either
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // The filter will reject for missing Authorization (AUTH_001), NOT AUTH_010
        // This proves the internal token enforcement only fires on /internal/ paths
        if (response.getStatus() == 401) {
            SifErrorResponse error = OBJECT_MAPPER.readValue(
                    response.getContentAsString(), SifErrorResponseEnvelope.class).error();
            assertThat(error.subCode())
                    .as("Non-internal path should fail with AUTH_001 (missing Authorization), not AUTH_010")
                    .isNotEqualTo("AUTH_010");
        }
        // If status is not 401, the filter passed it through (e.g., shouldNotFilter returned true)
        // Either way, AUTH_010 is never the reason for rejection on non-internal paths
    }
}
