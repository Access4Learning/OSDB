// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.unit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.exception.InvalidTokenException;
import org.sifunity.broker.common.util.SifAuthenticationUtils;
import org.sifunity.broker.environment.controller.InternalAuthController;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.TokenValidationRequest;
import org.sifunity.broker.environment.service.AuthenticationService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.UserAuthenticationService;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Log-assertion test for the authentication flow credential redaction.
 *
 * <p>Asserts that no raw Session_Token substring appears in any log output produced by
 * {@link AuthenticationService}, {@link UserAuthenticationService}, or
 * {@link InternalAuthController} during the token validation flow.</p>
 *
 * <p><b>Validates: Requirements 1.4, 4.2</b></p>
 */
@DisplayName("Auth Flow Credential Redaction - No Raw Token in Logs")
class AuthFlowCredentialRedactionTest {

    /** A recognizable raw token value that MUST NOT appear in any log output. */
    private static final String RAW_SESSION_TOKEN = "secret-session-token-abc123-xyz789";
    private static final String SHARED_SECRET = "test-shared-secret-value-for-hmac";
    private static final UUID ENV_ID = UUID.randomUUID();
    private static final UUID SOLUTION_UUID = UUID.randomUUID();

    private ListAppender<ILoggingEvent> authServiceAppender;
    private ListAppender<ILoggingEvent> userAuthServiceAppender;
    private ListAppender<ILoggingEvent> controllerAppender;

    private Logger authServiceLogger;
    private Logger userAuthServiceLogger;
    private Logger controllerLogger;

    private Level prevAuthLevel;
    private Level prevUserAuthLevel;
    private Level prevControllerLevel;

    @BeforeEach
    void attachAppenders() {
        authServiceLogger = (Logger) LoggerFactory.getLogger(AuthenticationService.class);
        userAuthServiceLogger = (Logger) LoggerFactory.getLogger(UserAuthenticationService.class);
        controllerLogger = (Logger) LoggerFactory.getLogger(InternalAuthController.class);

        prevAuthLevel = authServiceLogger.getLevel();
        prevUserAuthLevel = userAuthServiceLogger.getLevel();
        prevControllerLevel = controllerLogger.getLevel();

        // Set to DEBUG to capture all possible log statements
        authServiceLogger.setLevel(Level.DEBUG);
        userAuthServiceLogger.setLevel(Level.DEBUG);
        controllerLogger.setLevel(Level.DEBUG);

        authServiceAppender = new ListAppender<>();
        userAuthServiceAppender = new ListAppender<>();
        controllerAppender = new ListAppender<>();

        authServiceAppender.start();
        userAuthServiceAppender.start();
        controllerAppender.start();

        authServiceLogger.addAppender(authServiceAppender);
        userAuthServiceLogger.addAppender(userAuthServiceAppender);
        controllerLogger.addAppender(controllerAppender);
    }

    @AfterEach
    void detachAppenders() {
        authServiceLogger.detachAppender(authServiceAppender);
        userAuthServiceLogger.detachAppender(userAuthServiceAppender);
        controllerLogger.detachAppender(controllerAppender);

        authServiceAppender.stop();
        userAuthServiceAppender.stop();
        controllerAppender.stop();

        authServiceLogger.setLevel(prevAuthLevel);
        userAuthServiceLogger.setLevel(prevUserAuthLevel);
        controllerLogger.setLevel(prevControllerLevel);
    }

    @Test
    @DisplayName("Successful authentication flow logs do not contain raw session token")
    void successfulAuthFlow_noRawTokenInLogs() {
        // Arrange: build a valid auth request with a known session token
        String timestamp = Instant.now().toString();
        String hash = SifAuthenticationUtils.computeHmac(RAW_SESSION_TOKEN, timestamp, SHARED_SECRET);
        TokenValidationRequest request = new TokenValidationRequest(RAW_SESSION_TOKEN, timestamp, hash);

        Environment env = buildEnvironment();

        EnvironmentService environmentService = mock(EnvironmentService.class);
        SolutionService solutionService = mock(SolutionService.class);
        DomainService domainService = mock(DomainService.class);

        when(environmentService.findBySessionToken(RAW_SESSION_TOKEN)).thenReturn(env);
        when(environmentService.getSharedSecret(ENV_ID)).thenReturn(SHARED_SECRET);

        AuthenticationService authService = new AuthenticationService(
                environmentService, solutionService, domainService);

        // Act: invoke the authentication flow
        authService.validateToken(request);

        // Assert: no captured log from AuthenticationService contains the raw token
        assertNoRawTokenInLogs(authServiceAppender, "AuthenticationService");
    }

    @Test
    @DisplayName("Failed authentication (token not found) logs do not contain raw session token")
    void failedAuth_tokenNotFound_noRawTokenInLogs() {
        String timestamp = Instant.now().toString();
        String hash = SifAuthenticationUtils.computeHmac(RAW_SESSION_TOKEN, timestamp, SHARED_SECRET);
        TokenValidationRequest request = new TokenValidationRequest(RAW_SESSION_TOKEN, timestamp, hash);

        EnvironmentService environmentService = mock(EnvironmentService.class);
        SolutionService solutionService = mock(SolutionService.class);
        DomainService domainService = mock(DomainService.class);

        when(environmentService.findBySessionToken(RAW_SESSION_TOKEN)).thenReturn(null);

        AuthenticationService authService = new AuthenticationService(
                environmentService, solutionService, domainService);

        // Act
        try {
            authService.validateToken(request);
        } catch (InvalidTokenException ignored) {
            // Expected
        }

        // Assert
        assertNoRawTokenInLogs(authServiceAppender, "AuthenticationService");
    }

    @Test
    @DisplayName("Failed authentication (HMAC mismatch) logs do not contain raw session token")
    void failedAuth_hmacMismatch_noRawTokenInLogs() {
        String timestamp = Instant.now().toString();
        String wrongHash = "wrong-hash-value";
        TokenValidationRequest request = new TokenValidationRequest(RAW_SESSION_TOKEN, timestamp, wrongHash);

        Environment env = buildEnvironment();

        EnvironmentService environmentService = mock(EnvironmentService.class);
        SolutionService solutionService = mock(SolutionService.class);
        DomainService domainService = mock(DomainService.class);

        when(environmentService.findBySessionToken(RAW_SESSION_TOKEN)).thenReturn(env);
        when(environmentService.getSharedSecret(ENV_ID)).thenReturn(SHARED_SECRET);

        AuthenticationService authService = new AuthenticationService(
                environmentService, solutionService, domainService);

        // Act
        try {
            authService.validateToken(request);
        } catch (InvalidTokenException ignored) {
            // Expected
        }

        // Assert
        assertNoRawTokenInLogs(authServiceAppender, "AuthenticationService");
    }

    @Test
    @DisplayName("Failed authentication (internal env, not-used secret) logs do not contain raw session token")
    void failedAuth_internalEnv_noRawTokenInLogs() {
        String timestamp = Instant.now().toString();
        String hash = SifAuthenticationUtils.computeHmac(RAW_SESSION_TOKEN, timestamp, "not-used");
        TokenValidationRequest request = new TokenValidationRequest(RAW_SESSION_TOKEN, timestamp, hash);

        Environment env = buildEnvironment();

        EnvironmentService environmentService = mock(EnvironmentService.class);
        SolutionService solutionService = mock(SolutionService.class);
        DomainService domainService = mock(DomainService.class);

        when(environmentService.findBySessionToken(RAW_SESSION_TOKEN)).thenReturn(env);
        when(environmentService.getSharedSecret(ENV_ID)).thenReturn("not-used");

        AuthenticationService authService = new AuthenticationService(
                environmentService, solutionService, domainService);

        // Act
        try {
            authService.validateToken(request);
        } catch (InvalidTokenException ignored) {
            // Expected
        }

        // Assert
        assertNoRawTokenInLogs(authServiceAppender, "AuthenticationService");
    }

    @Test
    @DisplayName("InternalAuthController validate-token endpoint logs do not contain raw session token")
    void internalAuthController_validateToken_noRawTokenInLogs() {
        // Arrange
        String timestamp = Instant.now().toString();
        String hash = SifAuthenticationUtils.computeHmac(RAW_SESSION_TOKEN, timestamp, SHARED_SECRET);
        TokenValidationRequest request = new TokenValidationRequest(RAW_SESSION_TOKEN, timestamp, hash);

        Environment env = buildEnvironment();

        EnvironmentService environmentService = mock(EnvironmentService.class);
        SolutionService solutionService = mock(SolutionService.class);
        DomainService domainService = mock(DomainService.class);

        when(environmentService.findBySessionToken(RAW_SESSION_TOKEN)).thenReturn(env);
        when(environmentService.getSharedSecret(ENV_ID)).thenReturn(SHARED_SECRET);

        AuthenticationService authService = new AuthenticationService(
                environmentService, solutionService, domainService);

        // Build controller with mocked dependencies (only authService matters for validate-token)
        InternalAuthController controller = new InternalAuthController(
                authService, null, null, null, null, environmentService, null, null, null, null, null);

        // Act
        controller.validateToken(request);

        // Assert: both controller and auth-service logs are clean
        assertNoRawTokenInLogs(controllerAppender, "InternalAuthController");
        assertNoRawTokenInLogs(authServiceAppender, "AuthenticationService");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Environment buildEnvironment() {
        Environment env = new Environment();
        env.setId(ENV_ID);
        env.setApplicationName("TestApp");
        env.setSessionToken(RAW_SESSION_TOKEN);
        env.setSharedSecret(SHARED_SECRET);
        env.setType(EnvironmentType.DIRECT);
        env.setDefaultZoneId("test-zone");
        env.setFingerprint("test-fingerprint");
        env.setSolutionUuid(SOLUTION_UUID);
        return env;
    }

    private void assertNoRawTokenInLogs(ListAppender<ILoggingEvent> appender, String loggerName) {
        List<String> allMessages = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            allMessages.add(event.getFormattedMessage());
        }

        assertThat(allMessages)
                .as("Logs from %s must not be empty (auth flow should produce log output)", loggerName)
                .isNotEmpty();

        for (String message : allMessages) {
            assertThat(message)
                    .as("Log message from %s must NOT contain the raw session token. Message: %s",
                            loggerName, message)
                    .doesNotContain(RAW_SESSION_TOKEN);
        }
    }
}
