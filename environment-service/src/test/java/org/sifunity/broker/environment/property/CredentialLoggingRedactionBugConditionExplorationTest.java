// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.config.BootstrapProperties;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.ProviderRegistration;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.domain.Zone;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.ProviderRegistrationRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.service.BootstrapComponent;
import org.sifunity.broker.environment.service.ContextServiceService;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug-condition exploration test for credential logging redaction (Property 1).
 *
 * <p><b>This test is EXPECTED TO FAIL on the UNFIXED code.</b> Its failure documents the
 * counterexamples that prove admin credentials (sessionToken, sharedSecret) and the admin
 * password leak in recoverable form. It will pass once the fix removes/masks the values.</p>
 *
 * <p>Bug condition (from design):
 * {@code isBugCondition(X) = X.emits_output AND X.output_contains_credential_value
 * AND X.credential_value_is_recoverable} — a log/print statement emits a credential in full
 * recoverable form at any level.</p>
 *
 * <p>This class covers the {@code environment-service} defect location plus the shell-script
 * edge case (the gateway defect locations are covered by the same-named class in the gateway
 * module, since {@code AuthenticationFilter}/{@code EnvironmentServiceClient} live in a
 * different Maven module and cannot be referenced from here).</p>
 *
 * <ul>
 *   <li>{@code BootstrapComponent} lines 172-173 (INFO): admin sessionToken + sharedSecret</li>
 *   <li>{@code start-broker.sh} / {@code start-broker.ps1}: plaintext {@code admin123} password</li>
 * </ul>
 *
 * <b>Validates: Requirements 1.1, 1.2, 1.5, 1.6, 2.1, 2.2, 2.5, 2.6</b>
 */
class CredentialLoggingRedactionBugConditionExplorationTest {

    private static final String SESSION_TOKEN_LABEL = "Admin sessionToken: ";
    private static final String SHARED_SECRET_LABEL = "Admin sharedSecret: ";

    /**
     * Credential value shapes: empty, short (<=4), long alphanumeric, UUID-shaped, base64-shaped.
     */
    @Provide
    Arbitrary<String> credentials() {
        Arbitrary<String> empty = Arbitraries.just("");
        Arbitrary<String> shortValue = Arbitraries.strings()
                .withCharRange('a', 'z').ofMinLength(1).ofMaxLength(4);
        Arbitrary<String> longValue = Arbitraries.strings()
                .alpha().numeric().ofMinLength(12).ofMaxLength(40);
        Arbitrary<String> uuidShaped = Arbitraries.randomValue(r -> UUID.randomUUID().toString());
        Arbitrary<String> base64Shaped = Arbitraries.strings()
                .withCharRange('A', 'Z').withCharRange('a', 'z').withCharRange('0', '9')
                .ofMinLength(20).ofMaxLength(44)
                .map(s -> s + "==");
        return Arbitraries.oneOf(empty, shortValue, longValue, uuidShaped, base64Shaped);
    }

    // --- Property 1: BootstrapComponent admin sessionToken must not be recoverable in logs ---

    @Property(tries = 5)
    void bootstrapAdminSessionTokenIsNotEmittedInRecoverableForm(
            @ForAll("credentials") String sessionToken) {
        // Empty/blank tokens cause bootstrap to skip before logging — not a recoverable leak.
        Assume.that(sessionToken != null && !sessionToken.isBlank());

        List<String> messages = runBootstrapAndCaptureLogs(sessionToken, "fixed-shared-secret-value");

        boolean leaked = valueAppearsAfterLabel(messages, SESSION_TOKEN_LABEL, sessionToken);
        assertThat(leaked)
                .as("Bootstrap must NOT log the admin sessionToken in recoverable form. "
                        + "Captured messages: %s", messages)
                .isFalse();
    }

    // --- Property 1: BootstrapComponent admin sharedSecret must not be recoverable in logs ---

    @Property(tries = 5)
    void bootstrapAdminSharedSecretIsNotEmittedInRecoverableForm(
            @ForAll("credentials") String sharedSecret) {
        Assume.that(sharedSecret != null && !sharedSecret.isBlank());

        List<String> messages = runBootstrapAndCaptureLogs("fixed-session-token-value", sharedSecret);

        boolean leaked = valueAppearsAfterLabel(messages, SHARED_SECRET_LABEL, sharedSecret);
        assertThat(leaked)
                .as("Bootstrap must NOT log the admin sharedSecret in recoverable form. "
                        + "Captured messages: %s", messages)
                .isFalse();
    }

    // --- Property 1 (edge case): start scripts must not print the plaintext admin password ---

    @Test
    void startBrokerShellScriptDoesNotContainPlaintextAdminPassword() throws IOException {
        String source = readRepoFile("start-broker.sh");
        assertThat(source)
                .as("start-broker.sh must NOT print a plaintext 'admin123' password line")
                .doesNotContain("admin123");
    }

    @Test
    void startBrokerPowerShellScriptDoesNotContainPlaintextAdminPassword() throws IOException {
        String source = readRepoFile("start-broker.ps1");
        assertThat(source)
                .as("start-broker.ps1 must NOT print a plaintext 'admin123' password line")
                .doesNotContain("admin123");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Returns true if any captured log message contains {@code label} immediately followed by
     * the raw credential value. Only the portion after the label is inspected, so unrelated
     * message text cannot cause a false positive (robust for short values too).
     */
    private boolean valueAppearsAfterLabel(List<String> messages, String label, String value) {
        for (String message : messages) {
            int idx = message.indexOf(label);
            if (idx >= 0) {
                String suffix = message.substring(idx + label.length());
                if (suffix.contains(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String readRepoFile(String fileName) throws IOException {
        for (String candidate : new String[] {fileName, ".." + java.io.File.separator + fileName}) {
            Path path = Paths.get(candidate);
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        }
        throw new IOException("Could not locate " + fileName + " relative to " + Paths.get(".").toAbsolutePath());
    }

    /**
     * Drives {@link BootstrapComponent#bootstrapAdmin()} to first-boot admin creation with
     * mocked repositories (mirrors the proven setup in BootstrapComponentTest) and returns the
     * formatted messages logged by {@code BootstrapComponent}.
     */
    private List<String> runBootstrapAndCaptureLogs(String sessionToken, String sharedSecret) {
        EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
        DomainRepository domainRepository = mock(DomainRepository.class);
        SolutionRepository solutionRepository = mock(SolutionRepository.class);
        ZoneRepository zoneRepository = mock(ZoneRepository.class);
        ContextRepository contextRepository = mock(ContextRepository.class);
        ProviderRegistrationRepository providerRegistrationRepository =
                mock(ProviderRegistrationRepository.class);
        ContextServiceService contextServiceService = mock(ContextServiceService.class);
        BootstrapProperties bootstrapProperties = mock(BootstrapProperties.class);
        BootstrapProperties.AdminCredentials adminCredentials =
                mock(BootstrapProperties.AdminCredentials.class);

        when(bootstrapProperties.isEnabled()).thenReturn(true);
        when(bootstrapProperties.getAdmin()).thenReturn(adminCredentials);
        when(adminCredentials.getSessionToken()).thenReturn(sessionToken);
        when(adminCredentials.getSharedSecret()).thenReturn(sharedSecret);

        UUID domainUuid = UUID.randomUUID();
        when(domainRepository.existsByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)).thenReturn(false);
        when(domainRepository.save(any(Domain.class))).thenAnswer(inv -> {
            Domain d = inv.getArgument(0);
            d.setId(domainUuid);
            return d;
        });
        Domain savedDomain = new Domain();
        savedDomain.setId(domainUuid);
        savedDomain.setDomainId(GlobalAdminConstants.GLOBAL_DOMAIN);
        when(domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN))
                .thenReturn(Optional.of(savedDomain));

        UUID solUuid = UUID.randomUUID();
        when(solutionRepository.existsByDomainUuidAndSolutionId(
                any(UUID.class), eq(GlobalAdminConstants.GLOBAL_SOLUTION))).thenReturn(false);
        when(solutionRepository.save(any(Solution.class))).thenAnswer(inv -> {
            Solution s = inv.getArgument(0);
            s.setId(solUuid);
            return s;
        });
        Solution globalSol = new Solution();
        globalSol.setId(solUuid);
        globalSol.setSolutionId(GlobalAdminConstants.GLOBAL_SOLUTION);
        when(solutionRepository.findByDomainUuidAndSolutionId(domainUuid, GlobalAdminConstants.GLOBAL_SOLUTION))
                .thenReturn(Optional.of(globalSol));

        UUID zoneUuid = UUID.randomUUID();
        when(zoneRepository.existsBySolutionUuidAndZoneId(any(UUID.class), eq("environment-global")))
                .thenReturn(false);
        Zone envGlobalZone = new Zone();
        envGlobalZone.setId(zoneUuid);
        envGlobalZone.setZoneId("environment-global");
        when(zoneRepository.save(any(Zone.class))).thenReturn(envGlobalZone);
        when(zoneRepository.findBySolutionUuidAndZoneId(any(UUID.class), eq("environment-global")))
                .thenReturn(Optional.of(envGlobalZone));
        when(contextRepository.existsByZoneUuidAndContextId(zoneUuid, "DEFAULT")).thenReturn(false);
        Context savedContext = new Context();
        savedContext.setId(UUID.randomUUID());
        savedContext.setZoneUuid(zoneUuid);
        savedContext.setContextId("DEFAULT");
        when(contextRepository.save(any(Context.class))).thenReturn(savedContext);
        when(contextRepository.findByZoneUuidAndContextId(zoneUuid, "DEFAULT"))
                .thenReturn(Optional.of(savedContext));

        when(environmentRepository.findByApplicationNameAndSolutionUuid("ADMIN", solUuid))
                .thenReturn(Optional.empty());
        when(environmentRepository.findByApplicationNameAndSolutionUuid(
                eq("BROKER_SERVICES_" + solUuid), eq(solUuid))).thenReturn(Optional.empty());
        when(environmentRepository.save(any(Environment.class))).thenAnswer(inv -> {
            Environment e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            return e;
        });
        when(providerRegistrationRepository.findByContextServiceUuid(any())).thenReturn(Optional.empty());
        when(providerRegistrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contextServiceService.resolveOrCreate(any(), any(), any())).thenReturn(UUID.randomUUID());

        BootstrapComponent bootstrapComponent = new BootstrapComponent(
                environmentRepository, domainRepository, solutionRepository, zoneRepository,
                contextRepository, providerRegistrationRepository, contextServiceService,
                bootstrapProperties);

        Logger logger = (Logger) LoggerFactory.getLogger(BootstrapComponent.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            bootstrapComponent.bootstrapAdmin();
            List<String> messages = new ArrayList<>();
            for (ILoggingEvent event : appender.list) {
                messages.add(event.getFormattedMessage());
            }
            return messages;
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }
}
