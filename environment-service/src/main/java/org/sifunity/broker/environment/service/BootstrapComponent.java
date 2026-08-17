// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.SupportedRequestType;
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

import java.util.List;
import java.util.Set;
import org.sifunity.broker.common.dto.OperationsDTO;

/**
 * Component responsible for bootstrapping the admin environment on application startup.
 *
 * <p>This component ensures the global domain, global solution, and admin environment
 * exist on startup. Admin environments are identified by their membership in the
 * well-known global domain ({@link GlobalAdminConstants#GLOBAL_DOMAIN}) and solution
 * ({@link GlobalAdminConstants#GLOBAL_SOLUTION}), rather than by a special environment type.</p>
 *
 * <p>Bootstrap behavior:</p>
 * <ul>
 *   <li>Runs during application startup via @PostConstruct</li>
 *   <li>Checks if bootstrap is enabled in configuration</li>
 *   <li>Validates that credentials are provided</li>
 *   <li>Ensures global domain exists (creates if absent)</li>
 *   <li>Ensures global solution exists under global domain (creates if absent)</li>
 *   <li>Checks if admin environment exists by applicationName and solutionId</li>
 *   <li>Creates admin environment with type=BROKERED if none exists</li>
 *   <li>Logs credentials only on first creation</li>
 *   <li>Does not throw exceptions (allows service to start even if bootstrap fails)</li>
 * </ul>
 *
 * <p>The bootstrap process is idempotent - running it multiple times will not create
 * duplicate domains, solutions, or admin environments.</p>
 *
 * @see BootstrapProperties
 * @see GlobalAdminConstants
 */
@Component
public class BootstrapComponent {

    private static final Logger log = LoggerFactory.getLogger(BootstrapComponent.class);

    private final EnvironmentRepository environmentRepository;
    private final DomainRepository domainRepository;
    private final SolutionRepository solutionRepository;
    private final ZoneRepository zoneRepository;
    private final ContextRepository contextRepository;
    private final ProviderRegistrationRepository providerRegistrationRepository;
    private final ContextServiceService contextServiceService;
    private final BootstrapProperties bootstrapProperties;

    public BootstrapComponent(EnvironmentRepository environmentRepository,
                              DomainRepository domainRepository,
                              SolutionRepository solutionRepository,
                              ZoneRepository zoneRepository,
                              ContextRepository contextRepository,
                              ProviderRegistrationRepository providerRegistrationRepository,
                              ContextServiceService contextServiceService,
                              BootstrapProperties bootstrapProperties) {
        this.environmentRepository = environmentRepository;
        this.domainRepository = domainRepository;
        this.solutionRepository = solutionRepository;
        this.zoneRepository = zoneRepository;
        this.contextRepository = contextRepository;
        this.providerRegistrationRepository = providerRegistrationRepository;
        this.contextServiceService = contextServiceService;
        this.bootstrapProperties = bootstrapProperties;
    }

    /**
     * Bootstraps the admin environment on application startup.
     *
     * <p>This method is called automatically after dependency injection is complete.
     * It performs the following steps:</p>
     * <ol>
     *   <li>Check if bootstrap is enabled in configuration</li>
     *   <li>Validate that admin credentials are provided</li>
     *   <li>Ensure global domain exists (create if absent)</li>
     *   <li>Ensure global solution exists under global domain (create if absent)</li>
     *   <li>Check if admin environment exists (by applicationName and solutionId)</li>
     *   <li>If absent, create with type=BROKERED and solutionId=GLOBAL_SOLUTION</li>
     *   <li>Log credentials once on creation only</li>
     * </ol>
     *
     * <p>The method handles all errors gracefully and does not throw exceptions,
     * allowing the service to start even if bootstrap fails.</p>
     */
    @PostConstruct
    @Transactional
    public void bootstrapAdmin() {
        // Check if bootstrap is enabled
        if (!bootstrapProperties.isEnabled()) {
            log.info("Bootstrap disabled, skipping admin creation");
            return;
        }

        // Validate that credentials are provided
        if (bootstrapProperties.getAdmin() == null ||
            bootstrapProperties.getAdmin().getSessionToken() == null ||
            bootstrapProperties.getAdmin().getSessionToken().trim().isEmpty()) {
            log.warn("Bootstrap admin credentials not configured, skipping");
            return;
        }

        if (bootstrapProperties.getAdmin().getSharedSecret() == null ||
            bootstrapProperties.getAdmin().getSharedSecret().trim().isEmpty()) {
            log.warn("Bootstrap admin sharedSecret not configured, skipping");
            return;
        }

        try {
            // 1. Ensure GLOBAL_DOMAIN exists (create if absent)
            ensureGlobalDomainExists();

            // 2. Ensure GLOBAL_SOLUTION exists under GLOBAL_DOMAIN (create if absent)
            ensureGlobalSolutionExists();

            // 2b. Ensure environment-global zone + DEFAULT context exist in GLOBAL_SOLUTION
            ensureEnvironmentGlobalZone(GlobalAdminConstants.GLOBAL_SOLUTION);

            // 3. Check if admin environment exists (by applicationName and solutionUuid)
            Domain globalDomain = domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)
                .orElse(null);
            Solution globalSolution = globalDomain == null ? null
                : solutionRepository.findByDomainUuidAndSolutionId(
                        globalDomain.getId(), GlobalAdminConstants.GLOBAL_SOLUTION)
                    .orElse(null);
            if (globalSolution == null) {
                log.error("Global solution not found after ensuring it exists");
                return;
            }
            var existingAdmin = environmentRepository.findByApplicationNameAndSolutionUuid(
                "ADMIN", globalSolution.getId());

            if (existingAdmin.isPresent()) {
                log.info("Admin environment already exists, skipping bootstrap");
                return;
            }

            // 4. Create admin environment with type=BROKERED, solutionUuid=global solution UUID
            Environment admin = Environment.builder()
                .applicationName("ADMIN")
                .consumerName("ADMIN")
                .type(EnvironmentType.BROKERED)
                .solutionUuid(globalSolution.getId())
                .defaultZoneId("environment-global")
                .sessionToken(bootstrapProperties.getAdmin().getSessionToken())
                .sharedSecret(bootstrapProperties.getAdmin().getSharedSecret())
                .build();

            environmentRepository.save(admin);

            log.info("Bootstrap admin environment created successfully");

        } catch (Exception e) {
            // Log error but don't throw - allow service to start
            log.error("Failed to bootstrap admin environment", e);
        }
    }

    private void ensureGlobalDomainExists() {
        if (!domainRepository.existsByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)) {
            Domain domain = new Domain();
            domain.setDomainId(GlobalAdminConstants.GLOBAL_DOMAIN);
            domain.setDescription("Global admin domain");
            domainRepository.save(domain);
            log.info("Created global admin domain: {}", GlobalAdminConstants.GLOBAL_DOMAIN);
        }
    }

    private void ensureGlobalSolutionExists() {
        Domain domain = domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)
            .orElse(null);
        if (domain == null) {
            log.error("Global domain not found, cannot create global solution");
            return;
        }
        if (!solutionRepository.existsByDomainUuidAndSolutionId(
                domain.getId(), GlobalAdminConstants.GLOBAL_SOLUTION)) {
            Solution solution = new Solution();
            solution.setDomainUuid(domain.getId());
            solution.setSolutionId(GlobalAdminConstants.GLOBAL_SOLUTION);
            solution.setDescription("Global admin solution");
            solutionRepository.save(solution);
            log.info("Created global admin solution: {}", GlobalAdminConstants.GLOBAL_SOLUTION);
        }
    }

    private void ensureEnvironmentGlobalZone(String solutionId) {
        // Reserved GLOBAL_SOLUTION lives under the reserved GLOBAL_DOMAIN; resolve domain-scoped.
        Domain globalDomain = domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)
            .orElse(null);
        Solution solution = globalDomain == null ? null
            : solutionRepository.findByDomainUuidAndSolutionId(globalDomain.getId(), solutionId)
                .orElse(null);
        if (solution == null) {
            log.error("Solution {} not found, cannot create environment-global zone", solutionId);
            return;
        }
        if (!zoneRepository.existsBySolutionUuidAndZoneId(solution.getId(), "environment-global")) {
            Zone zone = new Zone();
            zone.setSolutionUuid(solution.getId());
            zone.setZoneId("environment-global");
            zone.setDescription("Global zone for utility services (auto-created per SIF spec)");
            zoneRepository.save(zone);
            log.info("Created environment-global zone for solution: {}", solutionId);
        }
        Zone envGlobalZone = zoneRepository.findBySolutionUuidAndZoneId(solution.getId(), "environment-global").orElse(null);
        if (envGlobalZone != null && !contextRepository.existsByZoneUuidAndContextId(envGlobalZone.getId(), "DEFAULT")) {
            Context context = new Context();
            context.setZoneUuid(envGlobalZone.getId());
            context.setContextId("DEFAULT");
            context.setDescription("Default context (auto-created per SIF spec)");
            contextRepository.save(context);
            log.info("Created DEFAULT context in environment-global zone");
        }
        
        // Create BROKER_SERVICES environment if not already present
        String brokerAppName = "BROKER_SERVICES_" + solution.getId();
        var existingBrokerEnv = environmentRepository.findByApplicationNameAndSolutionUuid(
                brokerAppName, solution.getId());
        Environment brokerEnv;
        if (existingBrokerEnv.isEmpty()) {
            brokerEnv = Environment.builder()
                    .applicationName(brokerAppName)
                    .consumerName("INTERNAL_CONSUMER_SERVICES")
                    .type(EnvironmentType.BROKERED)
                    .solutionUuid(solution.getId())
                    .sessionToken("internal-broker-services-" + solution.getId())
                    .sharedSecret(java.util.UUID.randomUUID().toString())
                    .fingerprint("broker-services")
                    .build();
            brokerEnv = environmentRepository.save(brokerEnv);
            log.info("Created BROKER_SERVICES environment for solution: {}", solutionId);
        } else {
            brokerEnv = existingBrokerEnv.get();
        }
        
        // Register alerts provider in environment-global zone if not already registered
        var defaultContext = contextRepository.findByZoneUuidAndContextId(envGlobalZone.getId(), "DEFAULT")
                .orElseThrow();
        java.util.UUID alertsContextServiceUuid = contextServiceService.resolveOrCreate(
                defaultContext.getId(), "alerts", "UTILITY");
        if (providerRegistrationRepository.findByContextServiceUuid(alertsContextServiceUuid).isEmpty()) {
            ProviderRegistration alertsProvider = ProviderRegistration.builder()
                    .environment(brokerEnv)
                    .contextServiceUuid(alertsContextServiceUuid)
                    .endpoint("internal://broker/alerts")
                    .serviceType(ServiceType.UTILITY)
                    .supportedRequestTypes(List.of(
                            new SupportedRequestType(RequestType.IMMEDIATE,
                                    OperationsDTO.of(Set.of(Operation.CREATE, Operation.QUERY)))
                    ))
                    .build();
            providerRegistrationRepository.save(alertsProvider);
            log.info("Registered alerts provider in environment-global zone for solution: {}", solutionId);
        }
    }
}
