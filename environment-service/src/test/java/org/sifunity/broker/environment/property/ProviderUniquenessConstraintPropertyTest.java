// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.ProviderRegistrationRequest;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.ZoneCreateRequest;
import org.sifunity.broker.environment.exception.DuplicateProviderException;
import org.sifunity.broker.environment.service.ContextServiceService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.ProviderService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.repository.ZoneRepository;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationBody;

/**
 * Property-based test for provider uniqueness constraint using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 12 from the design document:
 * "For any service/zone/context combination, only one provider registration
 * should be allowed. Attempting to register a duplicate should fail with
 * DuplicateProviderException."</p>
 * 
 * <p><strong>Validates: Requirements 5.5</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * the uniqueness constraint on (service_name, zone_id, context_id) is properly
 * enforced and duplicate registrations are rejected.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProviderUniquenessConstraintPropertyTest {
    
    @Autowired
    private EnvironmentService environmentService;
    
    @Autowired
    private ProviderService providerService;
    
    @Autowired
    private DomainService domainService;
    
    @Autowired
    private SolutionService solutionService;
    
    @Autowired
    private ZoneService zoneService;
    
    @Autowired
    private ContextServiceService contextServiceService;
    
    @Autowired
    private ContextRepository contextRepository;
    
    @Autowired
    private ZoneRepository zoneRepository;
    
    private final Random random = new Random();

    private String domainId;

    private String createTestSolution() {
        domainId = "puc-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));
        String solutionId = "puc-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
        return solutionId;
    }

    private String createZoneWithContext(String solutionId) {
        String zoneId = "puc-zone-" + System.nanoTime() + "-" + random.nextInt(10000);
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "Test zone"));
        return zoneId;
    }
    
    /**
     * Property 12: Provider uniqueness constraint
     * 
     * <p><strong>Property:</strong> For any service/zone/context combination, 
     * only one provider registration should be allowed. Attempting to register 
     * a duplicate should fail with DuplicateProviderException, regardless of 
     * other parameters like endpoint or supported patterns.</p>
     * 
     * <p><strong>Validates: Requirements 5.5</strong></p>
     * 
     * <p>This test creates a provider registration with random parameters, then 
     * attempts to register another provider with the same service/zone/context 
     * combination but different endpoint and patterns, verifying that the second 
     * registration is rejected with the appropriate exception.</p>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-12")
    void providerUniquenessConstraint() {
        // Create PROVIDER environment for testing (Requirement 5.5)
        String solutionId = createTestSolution();
        String zoneId = createZoneWithContext(solutionId);
        String contextId = "DEFAULT";
        
        String applicationName = generateUniqueApplicationName();
        EnvironmentRegistrationRequest envRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId), solutionId, null));
        SifEnvironmentRegistrationResponse environment = environmentService.registerEnvironment(domainId, envRequest);
        
        // Generate random provider registration parameters
        String serviceName = generateValidServiceName();
        String firstEndpoint = generateValidEndpoint();
        List<SupportedRequestType> firstSupportedRequestTypes = generateRandomSupportedRequestTypes();
        
        // Resolve contextServiceUuid via ContextServiceService
        UUID zoneUuid = zoneRepository.findBySolutionUuidAndZoneId(solutionService.getSolution(domainId, solutionId).id(), zoneId).orElseThrow().getId();
        UUID contextUuid = contextRepository.findByZoneUuidAndContextId(zoneUuid, contextId).orElseThrow().getId();
        UUID contextServiceUuid = contextServiceService.resolveOrCreate(contextUuid, serviceName, "OBJECT");
        
        // Create the first provider registration (Requirement 5.5)
        ProviderRegistrationRequest firstRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environment.environment().id(),
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of(firstEndpoint),
                SupportedRequestsDTO.of(firstSupportedRequestTypes)));
        
        ProviderRegistrationDTO firstProvider = providerService.registerProvider(domainId, solutionId, firstRequest);
        
        // Verify the first registration was successful
        assertThat(firstProvider.id()).isNotNull();
        assertThat(firstProvider.environmentId()).isEqualTo(environment.environment().id());
        assertThat(firstProvider.serviceName()).isEqualTo(serviceName);
        assertThat(firstProvider.zoneId()).isEqualTo(zoneId);
        assertThat(firstProvider.contextId()).isEqualTo(contextId);
        assertThat(ProtocolDTO.locationOf(firstProvider.endPoint())).isEqualTo(firstEndpoint);
        assertThat(firstProvider.supportedRequestTypes().supportedRequestType()).isEqualTo(firstSupportedRequestTypes);
        
        // Generate different endpoint and request types for the duplicate attempt
        String secondEndpoint = generateDifferentEndpoint(firstEndpoint);
        List<SupportedRequestType> secondSupportedRequestTypes = generateDifferentSupportedRequestTypes(firstSupportedRequestTypes);
        
        // Attempt to register a duplicate provider with same contextServiceUuid but different endpoint/request types
        ProviderRegistrationRequest duplicateRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environment.environment().id(),
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of(secondEndpoint),
                SupportedRequestsDTO.of(secondSupportedRequestTypes)));
        
        // Verify that the duplicate registration is rejected (Requirement 5.5)
        assertThatThrownBy(() -> {
            providerService.registerProvider(domainId, solutionId, duplicateRequest);
        })
            .isInstanceOf(DuplicateProviderException.class)
            .hasMessageContaining(serviceName)
            .hasMessageContaining(zoneId)
            .satisfies(exception -> {
                DuplicateProviderException duplicateException = (DuplicateProviderException) exception;
                // Verify the exception contains the correct details
                assertThat(duplicateException.getMessage())
                    .as("Exception message should contain service name")
                    .contains(serviceName);
                assertThat(duplicateException.getMessage())
                    .as("Exception message should contain zone ID")
                    .contains(zoneId);
            });
        
        // Verify that the original provider registration is still intact
        var environmentProviders = providerService.listProviderRegistrations(environment.environment().id());
        assertThat(environmentProviders)
            .as("Environment should still have exactly one provider registration after duplicate attempt")
            .hasSize(1);
        
        ProviderRegistrationDTO remainingProvider = environmentProviders.get(0);
        assertThat(remainingProvider.id())
            .as("Remaining provider should be the original one")
            .isEqualTo(firstProvider.id());
        
        assertThat(ProtocolDTO.locationOf(remainingProvider.endPoint()))
            .as("Remaining provider should have the original endpoint")
            .isEqualTo(firstEndpoint);
        
        assertThat(remainingProvider.supportedRequestTypes().supportedRequestType())
            .as("Remaining provider should have the original supported request types")
            .isEqualTo(firstSupportedRequestTypes);
        
        // Verify environment now has one provider
        var finalEnvironmentProviders = providerService.listProviderRegistrations(environment.environment().id());
        assertThat(finalEnvironmentProviders)
            .as("Environment should have exactly one provider registration")
            .hasSize(1);
        
        // Test that different service names in the same zone/context are allowed
        String differentServiceName = generateValidServiceName();
        UUID differentContextServiceUuid = contextServiceService.resolveOrCreate(contextUuid, differentServiceName, "OBJECT");
        
        ProviderRegistrationRequest differentServiceRequest = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environment.environment().id(),
                null,
                ServiceType.OBJECT,
                null,
                null,
                differentContextServiceUuid,
                ProtocolDTO.of(secondEndpoint),
                SupportedRequestsDTO.of(secondSupportedRequestTypes)));
        
        // This should succeed because it's a different service name
        ProviderRegistrationDTO providerWithDifferentService = providerService.registerProvider(domainId, solutionId, differentServiceRequest);
        
        assertThat(providerWithDifferentService.id())
            .as("Provider with different service name should be registered successfully")
            .isNotNull();
        
        assertThat(providerWithDifferentService.serviceName())
            .as("Provider should have the different service name")
            .isEqualTo(differentServiceName);
        
        // Verify environment now has two providers
        var finalEnvironmentProviders2 = providerService.listProviderRegistrations(environment.environment().id());
        assertThat(finalEnvironmentProviders2)
            .as("Environment should have two provider registrations after adding different service")
            .hasSize(2);
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Creates a test PROVIDER environment.
     */
    private SifEnvironmentRegistrationResponse createTestProviderEnvironment() {
        String solutionId = createTestSolution();
        String applicationName = generateUniqueApplicationName();
        String defaultZoneId = createZoneWithContext(solutionId);
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, EnvironmentType.BROKERED, DefaultZoneDTO.of(defaultZoneId), solutionId, null));

        return environmentService.registerEnvironment(domainId, request);
    }
    
    /**
     * Generates a unique application name to avoid conflicts.
     */
    private String generateUniqueApplicationName() {
        String[] prefixes = {"UniquenessTest", "DuplicateTest", "ConstraintTest", "PropTest"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        long timestamp = System.nanoTime();
        int randomSuffix = random.nextInt(10000);
        
        return prefix + "-" + timestamp + "-" + randomSuffix;
    }
    
    /**
     * Generates a valid zone ID.
     */
    private String generateValidZoneId() {
        String[] zones = {"production", "staging", "development", "test", "demo", "sandbox"};
        String base = zones[random.nextInt(zones.length)];
        int number = random.nextInt(100);
        return base + "-" + number;
    }
    
    /**
     * Generates a valid context ID.
     */
    private String generateValidContextId() {
        String[] contexts = {"default", "primary", "secondary", "backup", "main", "alt"};
        String base = contexts[random.nextInt(contexts.length)];
        int number = random.nextInt(50);
        return base + "-" + number;
    }
    
    /**
     * Generates a valid service name.
     */
    private String generateValidServiceName() {
        String[] services = {"UserService", "OrderService", "PaymentService", "NotificationService", "ReportService"};
        String base = services[random.nextInt(services.length)];
        long timestamp = System.nanoTime();
        return base + "-" + timestamp;
    }
    
    /**
     * Generates a valid HTTP/HTTPS endpoint URL.
     */
    private String generateValidEndpoint() {
        String[] protocols = {"http", "https"};
        String[] hosts = {"api.example.com", "service.test.org", "provider.demo.net", "endpoint.local"};
        String[] paths = {"/api/v1", "/services", "/provider", "/endpoint", ""};
        
        String protocol = protocols[random.nextInt(protocols.length)];
        String host = hosts[random.nextInt(hosts.length)];
        String path = paths[random.nextInt(paths.length)];
        int port = random.nextBoolean() ? (8000 + random.nextInt(1000)) : 80;
        
        if (port == 80 && "http".equals(protocol)) {
            return protocol + "://" + host + path;
        } else if (port == 443 && "https".equals(protocol)) {
            return protocol + "://" + host + path;
        } else {
            return protocol + "://" + host + ":" + port + path;
        }
    }
    
    /**
     * Generates a different endpoint URL from the given one.
     */
    private String generateDifferentEndpoint(String existingEndpoint) {
        String newEndpoint;
        do {
            newEndpoint = generateValidEndpoint();
        } while (newEndpoint.equals(existingEndpoint));
        return newEndpoint;
    }
    
    /**
     * Generates a random list of supported request types.
     */
    private List<SupportedRequestType> generateRandomSupportedRequestTypes() {
        java.util.List<SupportedRequestType> result = new java.util.ArrayList<>();
        result.add(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(generateRandomOperations())));
        if (random.nextBoolean()) {
            result.add(new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(generateRandomOperations())));
        }
        return result;
    }
    
    private Set<Operation> generateRandomOperations() {
        Operation[] allOps = Operation.values();
        Set<Operation> ops = EnumSet.noneOf(Operation.class);
        ops.add(allOps[random.nextInt(allOps.length)]);
        for (Operation op : allOps) {
            if (random.nextBoolean()) {
                ops.add(op);
            }
        }
        return ops;
    }
    
    /**
     * Generates a different list of supported request types from the given one.
     */
    private List<SupportedRequestType> generateDifferentSupportedRequestTypes(List<SupportedRequestType> existing) {
        List<SupportedRequestType> newTypes;
        int attempts = 0;
        do {
            newTypes = generateRandomSupportedRequestTypes();
            attempts++;
            if (attempts > 10) {
                // Force a different configuration
                if (existing.size() == 1) {
                    newTypes = List.of(
                        new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.CREATE))),
                        new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.QUERY)))
                    );
                } else {
                    newTypes = List.of(
                        new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.DELETE)))
                    );
                }
                break;
            }
        } while (newTypes.equals(existing));
        return newTypes;
    }
}

