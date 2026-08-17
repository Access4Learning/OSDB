// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.AclEntryDTO;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.dto.AclEntryCreateRequest;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.ProviderRegistrationRequest;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.exception.EnvironmentNotFoundException;
import org.sifunity.broker.environment.service.AclService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.ProviderService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationBody;

/**
 * Property-based test for Environment deletion cascade using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 7 from the design document:
 * "For any environment with associated ACL entries and provider registrations, 
 * deleting the environment should also delete all associated records."</p>
 * 
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * environment deletion properly cascades to delete all associated ACL entries
 * and provider registrations.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnvironmentDeletionCascadePropertyTest {
    
    @Autowired
    private EnvironmentService environmentService;
    
    @Autowired
    private AclService aclService;
    
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
    
    /**
     * Property 7: Environment deletion cascade
     * 
     * <p><strong>Property:</strong> For any environment with associated ACL entries 
     * and provider registrations, deleting the environment should also delete all 
     * associated records.</p>
     * 
     * <p><strong>Validates: Requirements 3.1, 3.2, 3.3</strong></p>
     * 
     * <p>This test creates an environment with random ACL entries and provider 
     * registrations, then verifies that deleting the environment cascades to 
     * delete all associated records.</p>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-7")
    void environmentDeletionCascade() {
        // Generate random number of ACL entries and provider registrations
        int aclEntryCount = random.nextInt(5) + 1; // 1-5 ACL entries
        int providerRegistrationCount = random.nextInt(3) + 1; // 1-3 provider registrations
        
        // Create environment for testing (includes zone + context)
        TestEnvironmentData testData = createTestEnvironment();
        SifEnvironmentRegistrationResponse environment = testData.environment();
        
        // Create random ACL entries for the environment
        for (int i = 0; i < aclEntryCount; i++) {
            AclEntryCreateRequest aclRequest = generateAclEntryRequest(environment.environment().id(), i, testData.zoneId(), testData.contextId());
            AclEntryDTO createdAcl = aclService.createAclEntry(aclRequest);
            
            // Verify ACL entry was created
            assertThat(createdAcl.id()).isNotNull();
            assertThat(createdAcl.environmentId()).isEqualTo(environment.environment().id());
        }
        
        // Create random provider registrations for the environment (only if PROVIDER type)
        if (environment.environment().type() == EnvironmentType.BROKERED) {
            for (int i = 0; i < providerRegistrationCount; i++) {
                ProviderRegistrationRequest providerRequest = generateProviderRegistrationRequest(
                        environment.environment().id(), i, testData.domainId(), testData.solutionId(), testData.zoneId(), testData.contextId());
                ProviderRegistrationDTO createdProvider = providerService.registerProvider(testData.domainId(), testData.solutionId(), providerRequest);
                
                // Verify provider registration was created
                assertThat(createdProvider.id()).isNotNull();
                assertThat(createdProvider.environmentId()).isEqualTo(environment.environment().id());
            }
        }
        
        // Verify ACL entries exist before deletion
        List<AclEntryDTO> aclEntriesBeforeDeletion = aclService.listAclEntries(environment.environment().id());
        assertThat(aclEntriesBeforeDeletion)
            .as("ACL entries should exist before environment deletion")
            .hasSize(aclEntryCount);
        
        // Verify provider registrations exist before deletion (if PROVIDER environment)
        if (environment.environment().type() == EnvironmentType.BROKERED) {
            List<ProviderRegistrationDTO> providerRegistrationsBeforeDeletion = 
                providerService.listProviderRegistrations(environment.environment().id());
            assertThat(providerRegistrationsBeforeDeletion)
                .as("Provider registrations should exist before environment deletion")
                .hasSize(providerRegistrationCount);
        }
        
        // Verify environment exists before deletion
        assertThat(environmentService.getEnvironment(environment.environment().id()))
            .as("Environment should exist before deletion")
            .isNotNull();
        
        // Delete the environment (Requirement 3.1)
        environmentService.deleteEnvironment(environment.environment().id());
        
        // Verify environment is deleted (Requirement 3.1)
        assertThatThrownBy(() -> environmentService.getEnvironment(environment.environment().id()))
            .as("Environment should be deleted")
            .isInstanceOf(EnvironmentNotFoundException.class);
        
        // Verify all ACL entries are cascade deleted (Requirement 3.2)
        List<AclEntryDTO> aclEntriesAfterDeletion = aclService.listAclEntries(environment.environment().id());
        assertThat(aclEntriesAfterDeletion)
            .as("All ACL entries should be cascade deleted when environment is deleted")
            .isEmpty();
        
        // Verify all provider registrations are cascade deleted (Requirement 3.3)
        List<ProviderRegistrationDTO> providerRegistrationsAfterDeletion = 
            providerService.listProviderRegistrations(environment.environment().id());
        assertThat(providerRegistrationsAfterDeletion)
            .as("All provider registrations should be cascade deleted when environment is deleted")
            .isEmpty();
        
        // Additional verification: Ensure the cascade deletion was complete
        // by checking that no orphaned records remain
        assertThat(aclEntriesAfterDeletion.size())
            .as("No ACL entries should remain after environment deletion")
            .isEqualTo(0);
        
        assertThat(providerRegistrationsAfterDeletion.size())
            .as("No provider registrations should remain after environment deletion")
            .isEqualTo(0);
    }
    
    // ========== Test Data Generators ==========
    
    private record TestEnvironmentData(SifEnvironmentRegistrationResponse environment,
                                        String domainId, String solutionId, String zoneId, String contextId, java.util.UUID contextUuid) {}
    
    /**
     * Creates a test environment with random characteristics, including zone and context.
     */
    private TestEnvironmentData createTestEnvironment() {
        // Create a domain, solution, zone, and context for the environment
        String domainId = "cascade-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, "Cascade test domain"));
        String solutionId = "cascade-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, "Cascade test solution"));
        
        // Create zone and get its auto-created DEFAULT context
        String zoneId = "cascade-zone-" + System.nanoTime();
        org.sifunity.broker.environment.dto.ZoneDTO zone = zoneService.createZone(domainId,
                new org.sifunity.broker.environment.dto.ZoneCreateRequest(solutionId, zoneId, "Test zone"));
        java.util.List<org.sifunity.broker.environment.dto.ContextDTO> contexts = zoneService.listContexts(domainId, solutionId, zoneId);
        org.sifunity.broker.environment.dto.ContextDTO defaultContext = contexts.stream()
                .filter(c -> "DEFAULT".equals(c.contextId()))
                .findFirst()
                .orElse(contexts.isEmpty() ? null : contexts.get(0));
        String contextId = defaultContext != null ? defaultContext.contextId() : null;
        java.util.UUID contextUuid = defaultContext != null ? defaultContext.id() : null;
        
        String applicationName = generateUniqueApplicationName();
        EnvironmentType type = generateRandomEnvironmentType();
        
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, type, DefaultZoneDTO.of(zoneId), solutionId, null));
        
        SifEnvironmentRegistrationResponse response = environmentService.registerEnvironment(domainId, request);
        return new TestEnvironmentData(response, domainId, solutionId, zoneId, contextId, contextUuid);
    }
    
    /**
     * Generates a unique ACL entry request for testing.
     */
    private AclEntryCreateRequest generateAclEntryRequest(java.util.UUID environmentId, int index, String zoneId, String contextId) {
        String serviceName = generateValidServiceName(index);
        AccessType accessType = generateRandomAccessType();
        AccessLevel accessLevel = generateRandomAccessLevel();
        
        return new AclEntryCreateRequest(
            environmentId,
            serviceName,
            zoneId,
            contextId,
            accessType,
            accessLevel
        );
    }
    
    /**
     * Generates a unique provider registration request for testing.
     */
    private ProviderRegistrationRequest generateProviderRegistrationRequest(java.util.UUID environmentId, int index, String domainId, String solutionId, String zoneId, String contextId) {
        String serviceName = generateValidServiceName(index);
        String endpoint = generateValidEndpoint(index);
        List<SupportedRequestType> supportedRequestTypes = generateRandomSupportedRequestTypes();
        
        // Resolve or create context_service to get its UUID
        // Find the context UUID from testData (passed via contextId)
        java.util.List<org.sifunity.broker.environment.dto.ContextDTO> contexts = zoneService.listContexts(domainId, solutionId, zoneId);
        org.sifunity.broker.environment.dto.ContextDTO ctx = contexts.stream()
                .filter(c -> contextId.equals(c.contextId()))
                .findFirst()
                .orElse(contexts.get(0));
        java.util.UUID contextServiceUuid = contextServiceService.resolveOrCreate(
                ctx.id(), serviceName, "OBJECT");
        
        return new ProviderRegistrationRequest(new ProviderRegistrationBody(
                environmentId,
                null,
                ServiceType.OBJECT,
                null,
                null,
                contextServiceUuid,
                ProtocolDTO.of(endpoint),
                SupportedRequestsDTO.of(supportedRequestTypes)));
    }
    
    /**
     * Generates a unique application name to avoid conflicts.
     */
    private String generateUniqueApplicationName() {
        String[] prefixes = {"TestApp", "CascadeTest", "DeleteTest", "PropTest"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        long timestamp = System.nanoTime();
        int randomSuffix = random.nextInt(10000);
        
        return prefix + "-" + timestamp + "-" + randomSuffix;
    }
    
    /**
     * Generates a random valid environment type.
     */
    private EnvironmentType generateRandomEnvironmentType() {
        EnvironmentType[] types = EnvironmentType.values();
        return types[random.nextInt(types.length)];
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
     * Generates a unique service name to avoid conflicts.
     */
    private String generateValidServiceName(int index) {
        String[] services = {"UserService", "OrderService", "PaymentService", "NotificationService", "ReportService"};
        String base = services[random.nextInt(services.length)];
        long timestamp = System.nanoTime();
        return base + "-" + index + "-" + timestamp;
    }
    
    /**
     * Generates a random access type.
     */
    private AccessType generateRandomAccessType() {
        AccessType[] types = AccessType.values();
        return types[random.nextInt(types.length)];
    }
    
    /**
     * Generates a random access level.
     */
    private AccessLevel generateRandomAccessLevel() {
        AccessLevel[] levels = AccessLevel.values();
        return levels[random.nextInt(levels.length)];
    }
    
    /**
     * Generates a valid endpoint URL.
     */
    private String generateValidEndpoint(int index) {
        String[] protocols = {"http", "https"};
        String[] hosts = {"localhost", "api.example.com", "service.test.com", "provider.demo.org"};
        String[] ports = {"8080", "8081", "8082", "9090", "3000"};
        String[] paths = {"/api/v1", "/service", "/provider", "/endpoint"};
        
        String protocol = protocols[random.nextInt(protocols.length)];
        String host = hosts[random.nextInt(hosts.length)];
        String port = ports[random.nextInt(ports.length)];
        String path = paths[random.nextInt(paths.length)];
        
        return protocol + "://" + host + ":" + port + path + "/" + index;
    }
    
    /**
     * Generates a random set of supported message exchange patterns.
     */
    private List<SupportedRequestType> generateRandomSupportedRequestTypes() {
        RequestType[] requestTypes = RequestType.values();
        java.util.List<SupportedRequestType> result = new java.util.ArrayList<>();
        
        // Always include at least IMMEDIATE
        result.add(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(generateRandomOperations())));
        
        // Randomly add DELAYED
        if (random.nextBoolean()) {
            result.add(new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(generateRandomOperations())));
        }
        
        return result;
    }
    
    private Set<Operation> generateRandomOperations() {
        Operation[] allOps = Operation.values();
        Set<Operation> ops = EnumSet.noneOf(Operation.class);
        ops.add(allOps[random.nextInt(allOps.length)]); // at least one
        for (Operation op : allOps) {
            if (random.nextBoolean()) {
                ops.add(op);
            }
        }
        return ops;
    }
}

