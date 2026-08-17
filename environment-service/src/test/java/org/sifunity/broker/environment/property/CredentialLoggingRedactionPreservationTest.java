// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.config.BootstrapProperties;
import org.sifunity.broker.environment.domain.Context;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Preservation property tests for credential logging redaction (Property 2), environment-service.
 *
 * <p><b>Observation-first methodology.</b> These tests capture the CURRENT (unfixed) behavior of
 * the non-credential-emitting code paths so that the same assertions continue to hold after the
 * fix. They are EXPECTED TO PASS on the unfixed code, establishing the baseline that the fix must
 * preserve. The fix only removes/masks credential values in log statements; it must not change
 * the stored credential values nor the non-sensitive confirmation message, and it must not change
 * the non-sensitive output of the start scripts.</p>
 *
 * <p>Non-bug condition (from design): {@code isBugCondition(X)} returns false — statements that
 * emit no recoverable credential value, plus all functional bootstrap logic.</p>
 *
 * <p>Covers the {@code environment-service} preservation concerns plus the shell-script
 * non-sensitive output:</p>
 * <ul>
 *   <li>Bootstrap functional: the admin environment is created/persisted with the correct stored
 *       {@code sessionToken}/{@code sharedSecret}, and the "Bootstrap admin environment created
 *       successfully" confirmation message is emitted.</li>
 *   <li>Script output: URLs, health endpoints, {@code Username: admin}, and stop instructions are
 *       printed.</li>
 * </ul>
 *
 * <p>Uses {@code @Property(tries = 5)} to keep the run fast per the speed requirement for these
 * preservation properties.</p>
 *
 * <b>Validates: Requirements 3.1, 3.2, 3.5</b>
 */
class CredentialLoggingRedactionPreservationTest {

    private static final String CONFIRMATION_MESSAGE = "Bootstrap admin environment created successfully";

    /**
     * Credential value shapes: short (<=4), long alphanumeric, UUID-shaped, base64-shaped.
     * Non-blank so bootstrap proceeds to admin creation.
     */
    @Provide
    Arbitrary<String> credentials() {
        Arbitrary<String> shortValue = Arbitraries.strings()
                .withCharRange('a', 'z').ofMinLength(1).ofMaxLength(4);
        Arbitrary<String> longValue = Arbitraries.strings()
                .alpha().numeric().ofMinLength(12).ofMaxLength(40);
        Arbitrary<String> uuidShaped = Arbitraries.randomValue(r -> UUID.randomUUID().toString());
        Arbitrary<String> base64Shaped = Arbitraries.strings()
                .withCharRange('A', 'Z').withCharRange('a', 'z').withCharRange('0', '9')
                .ofMinLength(20).ofMaxLength(44)
                .map(s -> s + "==");
        return Arbitraries.oneOf(shortValue, longValue, uuidShaped, base64Shaped);
    }

    // --- Property 2: Bootstrap stores the correct credentials and emits the confirmation ---

    @Property(tries = 5)
    void bootstrapStoresCredentialsAndEmitsConfirmation(
            @ForAll("credentials") String sessionToken,
            @ForAll("credentials") String sharedSecret) {

        BootstrapResult result = runBootstrap(sessionToken, sharedSecret);

        // Functional preservation: the persisted admin environment retains the exact credentials.
        Environment admin = result.savedAdminEnvironment;
        assertThat(admin)
                .as("Bootstrap must create and persist the ADMIN environment")
                .isNotNull();
        assertThat(admin.getSessionToken())
                .as("Persisted admin sessionToken must equal the configured value")
                .isEqualTo(sessionToken);
        assertThat(admin.getSharedSecret())
                .as("Persisted admin sharedSecret must equal the configured value")
                .isEqualTo(sharedSecret);

        // Non-sensitive log preservation: the confirmation message is emitted unchanged.
        assertThat(result.messages)
                .as("Bootstrap must emit the non-sensitive confirmation message verbatim. "
                        + "Captured messages: %s", result.messages)
                .contains(CONFIRMATION_MESSAGE);
    }

    // --- Property 2 (edge cases): start scripts still print non-sensitive operator info ---

    @Test
    void startBrokerShellScriptRetainsNonSensitiveOutput() throws IOException {
        String source = readRepoFile("start-broker.sh");
        assertThat(source)
                .as("start-broker.sh must retain the non-sensitive HMAC auth guidance line")
                .contains("Auth is HMAC-signed (no username/password)");
        assertThat(source)
                .as("start-broker.sh must retain the gateway health endpoint")
                .contains("http://localhost:8080/actuator/health");
        assertThat(source)
                .as("start-broker.sh must retain the stop instructions")
                .contains("./stop-broker.sh");
    }

    @Test
    void startBrokerPowerShellScriptRetainsNonSensitiveOutput() throws IOException {
        String source = readRepoFile("start-broker.ps1");
        assertThat(source)
                .as("start-broker.ps1 must retain the non-sensitive HMAC auth guidance line")
                .contains("Auth is HMAC-signed (no username/password)");
        assertThat(source)
                .as("start-broker.ps1 must retain the gateway health endpoint")
                .contains("http://localhost:8080/actuator/health");
        assertThat(source)
                .as("start-broker.ps1 must retain the stop instructions")
                .contains("Press Ctrl+C in each service window to stop services");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String readRepoFile(String fileName) throws IOException {
        for (String candidate : new String[] {fileName, ".." + java.io.File.separator + fileName}) {
            Path path = Paths.get(candidate);
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        }
        throw new IOException("Could not locate " + fileName + " relative to "
                + Paths.get(".").toAbsolutePath());
    }

    private static final class BootstrapResult {
        private final Environment savedAdminEnvironment;
        private final List<String> messages;

        private BootstrapResult(Environment savedAdminEnvironment, List<String> messages) {
            this.savedAdminEnvironment = savedAdminEnvironment;
            this.messages = messages;
        }
    }

    /**
     * Drives {@link BootstrapComponent#bootstrapAdmin()} to first-boot admin creation with mocked
     * repositories (mirrors the proven setup in BootstrapComponentTest), captures the persisted
     * ADMIN environment and the formatted log messages.
     */
    private BootstrapResult runBootstrap(String sessionToken, String sharedSecret) {
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

            ArgumentCaptor<Environment> captor = ArgumentCaptor.forClass(Environment.class);
            verify(environmentRepository, atLeastOnce()).save(captor.capture());
            Environment adminEnvironment = null;
            for (Environment saved : captor.getAllValues()) {
                if ("ADMIN".equals(saved.getApplicationName())) {
                    adminEnvironment = saved;
                }
            }

            List<String> messages = new ArrayList<>();
            for (ILoggingEvent event : appender.list) {
                messages.add(event.getFormattedMessage());
            }
            return new BootstrapResult(adminEnvironment, messages);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }
}
