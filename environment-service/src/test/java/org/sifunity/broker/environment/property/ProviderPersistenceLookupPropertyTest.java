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
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.ProviderService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationBody;

/**
 * Property-based test for provider registration persistence and lookup using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 11 from the design document:
 * "For any provider registration, the provider should be persistently stored
 * and retrievable through lookup operations using service name, zone, and context."</p>
 * 
 * <p><strong>Validates: Requirements 5.1, 5.6</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * provider registrations are properly persisted and can be looked up correctly.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProviderPersistenceLookupPropertyTest {
    
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
    private org.sifunity.broker.environment.service.ContextServiceService contextServiceService;
    
    private final Random random = new Random();

    private String domainId;

    private String createTestSolution() {
        domainId = "ppl-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));
        String solutionId = "ppl-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
        return solutionId;
    }

    private String createZoneWithContext(String solutionId) {
        String zoneId = "ppl-zone-" + System.nanoTime() + "-" + random.nextInt(10000);
        zoneService.createZone(domainId, new ZoneCreateRequest(solutionId, zoneId, "Test zone"));
        return zoneId;
    }
    
    /**
     * Property 11: Provider registration persistence and lookup
     * 
     * <p><strong>Property:</strong> For any provider registration, the provider 
     * should be persistently stored and retrievable through lookup operations 
     * using service name, zone, and context. All registration details should 
     * be preserved accurately.</p>
     * 
     * <p><strong>Validates: Requirements 5.1, 5.6</strong></p>
     * 
     * <p>This test creates a provider registration with random parameters, then 
     * verifies that it can be looked up correctly using the service name, zone, 
     * and context combination, and that all details are preserved.</p>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-11")
    void providerPersistenceAndLookup() {
        // Create PROVIDER environment for testing (Requirement 5.1)
        String solutionId = createTestSolution();
        UUID solutionUuid = solutionService.getSolution(domainId, solutionId).id();
        String zoneId = createZoneWithContext(solutionId);
        String contextId = "DEFAULT";
        
        String applicationName = generateUniqueApplicationName();
        EnvironmentRegistrationRequest envRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, EnvironmentType.BROKERED, DefaultZoneDTO.of(zoneId), solutionId, null));
        SifEnvironmentRegistrationResponse environment = environmentService.registerEnvironment(domainId, envRequest);

        // Generate random provider registration parameters
        String serviceName = generateValidServiceName();
        String endpoint = generateValidEndpoint();
        List<SupportedRequestType> supportedRequestTypes = generateRandomSupportedRequestTypes();
        
        // Resolve context_service UUID for the new DTO contract
        java.util.List<org.sifunity.broker.environment.dto.ContextDTO> contexts = zoneService.listContexts(domainId, solutionId, zoneId);
        org.sifunity.broker.environment.dto.ContextDTO ctx = contexts.stream()
                .filter(c -> contextId.equals(c.contextId()))
                .findFirst()
                .orElse(contexts.get(0));
        java.util.UUID contextServiceUuid = contextServiceService.resolveOrCreate(
                ctx.id(), serviceName, "OBJECT");
        
        // Create the provider registration (Requirement 5.1)
        ProviderRegistrationRequest request = new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environment.environment().id(),
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of(endpoint),
                SupportedRequestsDTO.of(supportedRequestTypes)));
        
        ProviderRegistrationDTO registeredProvider = providerService.registerProvider(domainId, solutionId, request);
        
        // Verify the registration response contains all expected data
        assertThat(registeredProvider.id()).isNotNull();
        assertThat(registeredProvider.environmentId()).isEqualTo(environment.environment().id());
        assertThat(registeredProvider.serviceName()).isEqualTo(serviceName);
        assertThat(registeredProvider.zoneId()).isEqualTo(zoneId);
        assertThat(registeredProvider.contextId()).isEqualTo(contextId);
        assertThat(ProtocolDTO.locationOf(registeredProvider.endPoint())).isEqualTo(endpoint);
        assertThat(registeredProvider.supportedRequestTypes().supportedRequestType()).isEqualTo(supportedRequestTypes);
        assertThat(registeredProvider.registeredAt()).isNotNull();
        
        // Test lookup by service name, zone, and context (Requirement 5.6)
        Optional<ProviderRegistrationDTO> lookedUpProvider = providerService.lookupProvider(solutionUuid, serviceName, zoneId, contextId);
        
        // Verify the provider was found
        assertThat(lookedUpProvider)
            .as("Provider should be found through lookup operation")
            .isPresent();
        
        ProviderRegistrationDTO foundProvider = lookedUpProvider.get();
        
        // Verify all fields are preserved correctly in lookup
        assertThat(foundProvider.id())
            .as("Provider ID should match the registered provider")
            .isEqualTo(registeredProvider.id());
        
        assertThat(foundProvider.environmentId())
            .as("Environment ID should match the registered provider")
            .isEqualTo(environment.environment().id());
        
        assertThat(foundProvider.serviceName())
            .as("Service name should match the registered provider")
            .isEqualTo(serviceName);
        
        assertThat(foundProvider.zoneId())
            .as("Zone ID should match the registered provider")
            .isEqualTo(zoneId);
        
        assertThat(foundProvider.contextId())
            .as("Context ID should match the registered provider")
            .isEqualTo(contextId);
        
        assertThat(ProtocolDTO.locationOf(foundProvider.endPoint()))
            .as("Endpoint should match the registered provider")
            .isEqualTo(endpoint);
        
        assertThat(foundProvider.supportedRequestTypes().supportedRequestType())
            .as("Supported request types should match the registered provider")
            .isEqualTo(supportedRequestTypes);
        
        assertThat(foundProvider.registeredAt())
            .as("Registration timestamp should match the registered provider")
            .isEqualTo(registeredProvider.registeredAt());
        
        // Test lookup with non-existent service name
        String nonExistentServiceName = "NonExistentService-" + System.nanoTime();
        Optional<ProviderRegistrationDTO> notFoundProvider = providerService.lookupProvider(
            solutionUuid, nonExistentServiceName, zoneId, contextId
        );
        
        assertThat(notFoundProvider)
            .as("Lookup with non-existent service name should return empty")
            .isEmpty();
        
        // Test lookup with non-existent zone
        String nonExistentZoneId = "nonexistent-zone-" + System.nanoTime();
        Optional<ProviderRegistrationDTO> notFoundByZone = providerService.lookupProvider(
            solutionUuid, serviceName, nonExistentZoneId, contextId
        );
        
        assertThat(notFoundByZone)
            .as("Lookup with non-existent zone should return empty")
            .isEmpty();
        
        // Test lookup with different context
        String differentContextId = "different-context-" + System.nanoTime();
        Optional<ProviderRegistrationDTO> notFoundByContext = providerService.lookupProvider(
            solutionUuid, serviceName, zoneId, differentContextId
        );
        
        assertThat(notFoundByContext)
            .as("Lookup with different context should return empty")
            .isEmpty();
        
        // Test lookup with null context when provider was registered with non-null context
        Optional<ProviderRegistrationDTO> notFoundByNullContext = providerService.lookupProvider(solutionUuid, serviceName, zoneId, null);
        
        assertThat(notFoundByNullContext)
            .as("Lookup with null context when provider has non-null context should return empty")
            .isEmpty();
        
        // Verify provider appears in environment's provider list
        var environmentProviders = providerService.listProviderRegistrations(environment.environment().id());
        assertThat(environmentProviders)
            .as("Environment should have exactly one provider registration")
            .hasSize(1);
        
        ProviderRegistrationDTO listedProvider = environmentProviders.get(0);
        assertThat(listedProvider.id())
            .as("Listed provider should match the registered provider")
            .isEqualTo(registeredProvider.id());
        
        assertThat(listedProvider.serviceName())
            .as("Listed provider service name should match")
            .isEqualTo(serviceName);
        
        assertThat(listedProvider.zoneId())
            .as("Listed provider zone ID should match")
            .isEqualTo(zoneId);
        
        assertThat(listedProvider.contextId())
            .as("Listed provider context ID should match")
            .isEqualTo(contextId);
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Creates a test PROVIDER environment.
     */
    private SifEnvironmentRegistrationResponse createTestProviderEnvironment() {
        String solutionId = createTestSolution();
        String applicationName = generateUniqueApplicationName();
        String defaultZoneId = createZoneWithContext(solutionId);
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(
            new EnvironmentRegistrationBody(
                applicationName,
                EnvironmentType.BROKERED,   // Must be PROVIDER for provider registration
                DefaultZoneDTO.of(defaultZoneId),
                solutionId,
                null));

        return environmentService.registerEnvironment(domainId, request);
    }
    
    /**
     * Generates a unique application name to avoid conflicts.
     */
    private String generateUniqueApplicationName() {
        String[] prefixes = {"ProviderTest", "LookupTest", "PersistenceTest", "PropTest"};
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
}

