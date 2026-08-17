// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.exception.InvalidTokenException;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.TokenValidationRequest;
import org.sifunity.broker.environment.dto.TokenValidationResponse;
import org.sifunity.broker.environment.service.AuthenticationService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;

/**
 * End-to-end integration test for authentication flow using H2 in-memory database.
 *
 * <p><strong>Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.6</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthenticationFlowH2IntegrationTest {

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    private TestEntityHierarchy hierarchy;

    @BeforeEach
    void setUp() {
        hierarchy = TestEntityHierarchy.create(domainService, solutionService);
    }

    @Test
    void testAuthenticationFlowWithCorrectHmac() {
        SifEnvironmentRegistrationResponse reg = registerEnvironment("H2AuthApp-");
        String sessionToken = reg.environment().sessionToken();
        String sharedSecret = reg.sharedSecret();

        String timestamp = Instant.now().toString();
        String hash = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);

        TokenValidationResponse response = authenticationService.validateToken(
                new TokenValidationRequest(sessionToken, timestamp, hash));

        assertThat(response).isNotNull();
        assertThat(response.environmentId()).isEqualTo(reg.environment().id());
    }

    @Test
    void testAuthenticationFlowWithIncorrectHmac() {
        SifEnvironmentRegistrationResponse reg = registerEnvironment("H2AuthApp-BadHmac-");
        String sessionToken = reg.environment().sessionToken();

        String timestamp = Instant.now().toString();
        TokenValidationRequest request = new TokenValidationRequest(
                sessionToken, timestamp, "InvalidHmacSignature123456789==");

        assertThatThrownBy(() -> authenticationService.validateToken(request))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("HMAC");
    }

    @Test
    void testAuthenticationFlowWithOldTimestamp() {
        SifEnvironmentRegistrationResponse reg = registerEnvironment("H2AuthApp-OldTs-");
        String sessionToken = reg.environment().sessionToken();
        String sharedSecret = reg.sharedSecret();

        String oldTimestamp = Instant.now().minus(10, ChronoUnit.MINUTES).toString();
        String hash = SifAuthenticationUtils.computeHmac(sessionToken, oldTimestamp, sharedSecret);

        TokenValidationRequest request = new TokenValidationRequest(sessionToken, oldTimestamp, hash);

        assertThatThrownBy(() -> authenticationService.validateToken(request))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("tolerance");
    }

    private SifEnvironmentRegistrationResponse registerEnvironment(String prefix) {
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(prefix + System.currentTimeMillis(), EnvironmentType.BROKERED, null, hierarchy.getSolutionId(), null));
        return environmentService.registerEnvironment(hierarchy.getDomainId(), request);
    }
}
