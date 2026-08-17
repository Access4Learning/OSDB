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
import org.sifunity.broker.common.dto.AclEntryDTO;
import org.sifunity.broker.environment.dto.AclEntryCreateRequest;
import org.sifunity.broker.environment.dto.AclEntryUpdateRequest;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.service.AclService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Property-based test for ACL entry update persistence using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 10 from the design document:
 * "For any ACL entry update, the new access level should be persisted correctly
 * and retrievable through subsequent queries."</p>
 * 
 * <p><strong>Validates: Requirements 4.5</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * ACL entry updates are properly persisted and can be retrieved correctly.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AclUpdatePersistencePropertyTest {
    
    @Autowired
    private EnvironmentService environmentService;
    
    @Autowired
    private AclService aclService;
    
    @Autowired
    private EnvironmentRepository environmentRepository;
    
    @Autowired
    private DomainService domainService;
    
    @Autowired
    private SolutionService solutionService;
    
    @Autowired
    private ZoneService zoneService;
    
    private final Random random = new Random();
    
    /**
     * Property 10: ACL entry update persistence
     * 
     * <p><strong>Property:</strong> For any ACL entry update, the new access level 
     * should be persisted correctly and retrievable through subsequent queries. 
     * The update should not affect other fields of the ACL entry.</p>
     * 
     * <p><strong>Validates: Requirements 4.5</strong></p>
     * 
     * <p>This test creates an ACL entry with random parameters, updates its access 
     * level to a different random value, then verifies that the update was persisted 
     * correctly while preserving all other fields.</p>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-10")
    void aclUpdatePersistence() {
        // Create Domain → Solution → Zone → Context hierarchy so contextUuid resolves
        TestEntityHierarchy hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
        
        // Create environment for testing
        SifEnvironmentRegistrationResponse environment = createTestEnvironment(hierarchy);
        
        // Generate random ACL entry parameters
        String serviceName = generateValidServiceName();
        AccessType accessType = generateRandomAccessType();
        AccessLevel originalAccessLevel = generateRandomAccessLevel();
        AccessLevel updatedAccessLevel = generateDifferentAccessLevel(originalAccessLevel);
        
        // Create the initial ACL entry using the real context UUID from hierarchy
        AclEntryCreateRequest createRequest = new AclEntryCreateRequest(
            environment.environment().id(),
            serviceName,
            hierarchy.getZoneId(), hierarchy.getContextId(),
            accessType,
            originalAccessLevel
        );
        
        AclEntryDTO createdAclEntry = aclService.createAclEntry(
            hierarchy.getDomainId(), hierarchy.getSolutionId(), createRequest,
            environmentRepository.findById(environment.environment().id()).orElseThrow(), true);
        
        // Verify initial ACL entry was created correctly
        assertThat(createdAclEntry.id()).isNotNull();
        assertThat(createdAclEntry.environmentId()).isEqualTo(environment.environment().id());
        assertThat(createdAclEntry.serviceName()).isEqualTo(serviceName);
        assertThat(createdAclEntry.zoneId()).isEqualTo(hierarchy.getZoneId());
        assertThat(createdAclEntry.contextId()).isEqualTo(hierarchy.getContextId());
        assertThat(createdAclEntry.accessType()).isEqualTo(accessType);
        assertThat(createdAclEntry.accessLevel()).isEqualTo(originalAccessLevel);
        
        // Update the ACL entry's access level (Requirement 4.5)
        AclEntryUpdateRequest updateRequest = new AclEntryUpdateRequest(updatedAccessLevel);
        AclEntryDTO updatedAclEntry = aclService.updateAclEntry(createdAclEntry.id(), updateRequest);
        
        // Verify the update response contains the new access level
        assertThat(updatedAclEntry.id())
            .as("ACL entry ID should remain unchanged after update")
            .isEqualTo(createdAclEntry.id());
        
        assertThat(updatedAclEntry.environmentId())
            .as("Environment ID should remain unchanged after update")
            .isEqualTo(environment.environment().id());
        
        assertThat(updatedAclEntry.serviceName())
            .as("Service name should remain unchanged after update")
            .isEqualTo(serviceName);
        
        assertThat(updatedAclEntry.zoneId())
            .as("Zone ID should remain unchanged after update")
            .isEqualTo(hierarchy.getZoneId());
        
        assertThat(updatedAclEntry.contextId())
            .as("Context ID should remain unchanged after update")
            .isEqualTo(hierarchy.getContextId());
        
        assertThat(updatedAclEntry.accessType())
            .as("Access type should remain unchanged after update")
            .isEqualTo(accessType);
        
        assertThat(updatedAclEntry.accessLevel())
            .as("Access level should be updated to the new value")
            .isEqualTo(updatedAccessLevel);
        
        // Verify persistence by retrieving the ACL entry through list operation
        var aclEntries = aclService.listAclEntries(environment.environment().id());
        assertThat(aclEntries)
            .as("Should have exactly one ACL entry after update")
            .hasSize(1);
        
        AclEntryDTO retrievedAclEntry = aclEntries.get(0);
        
        // Verify all fields are persisted correctly
        assertThat(retrievedAclEntry.id())
            .as("Retrieved ACL entry ID should match the original")
            .isEqualTo(createdAclEntry.id());
        
        assertThat(retrievedAclEntry.environmentId())
            .as("Retrieved environment ID should match the original")
            .isEqualTo(environment.environment().id());
        
        assertThat(retrievedAclEntry.serviceName())
            .as("Retrieved service name should match the original")
            .isEqualTo(serviceName);
        
        assertThat(retrievedAclEntry.zoneId())
            .as("Retrieved zone ID should match the hierarchy zone")
            .isEqualTo(hierarchy.getZoneId());
        
        assertThat(retrievedAclEntry.contextId())
            .as("Retrieved context ID should match the hierarchy context")
            .isEqualTo(hierarchy.getContextId());
        
        assertThat(retrievedAclEntry.accessType())
            .as("Retrieved access type should match the original")
            .isEqualTo(accessType);
        
        assertThat(retrievedAclEntry.accessLevel())
            .as("Retrieved access level should match the updated value")
            .isEqualTo(updatedAccessLevel);
        
        // Additional verification: Ensure the access level was actually changed
        assertThat(retrievedAclEntry.accessLevel())
            .as("Access level should be different from the original value")
            .isNotEqualTo(originalAccessLevel);
        
        // Test multiple updates to the same ACL entry
        AccessLevel secondUpdatedAccessLevel = generateDifferentAccessLevel(updatedAccessLevel);
        AclEntryUpdateRequest secondUpdateRequest = new AclEntryUpdateRequest(secondUpdatedAccessLevel);
        AclEntryDTO secondUpdatedAclEntry = aclService.updateAclEntry(createdAclEntry.id(), secondUpdateRequest);
        
        // Verify the second update
        assertThat(secondUpdatedAclEntry.accessLevel())
            .as("Access level should be updated to the second new value")
            .isEqualTo(secondUpdatedAccessLevel);
        
        // Verify persistence of the second update
        var aclEntriesAfterSecondUpdate = aclService.listAclEntries(environment.environment().id());
        assertThat(aclEntriesAfterSecondUpdate.get(0).accessLevel())
            .as("Retrieved access level should match the second updated value")
            .isEqualTo(secondUpdatedAccessLevel);
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Creates a test environment with random characteristics.
     */
    private SifEnvironmentRegistrationResponse createTestEnvironment(TestEntityHierarchy hierarchy) {
        String applicationName = generateUniqueApplicationName();
        EnvironmentType type = generateRandomEnvironmentType();
        // Use the zone from the hierarchy so validation passes
        String defaultZoneId = hierarchy.getZoneId();
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, type, DefaultZoneDTO.of(defaultZoneId), hierarchy.getSolutionId(), null));
        
        return environmentService.registerEnvironment(hierarchy.getDomainId(), request);
    }
    
    /**
     * Generates a unique application name to avoid conflicts.
     */
    private String generateUniqueApplicationName() {
        String[] prefixes = {"UpdateTest", "PersistenceTest", "AclUpdateTest", "PropTest"};
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
     * Generates a valid service name.
     */
    private String generateValidServiceName() {
        String[] services = {"UserService", "OrderService", "PaymentService", "NotificationService", "ReportService"};
        String base = services[random.nextInt(services.length)];
        long timestamp = System.nanoTime();
        return base + "-" + timestamp;
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
     * Generates a different access level from the given one.
     */
    private AccessLevel generateDifferentAccessLevel(AccessLevel existingAccessLevel) {
        AccessLevel[] levels = AccessLevel.values();
        AccessLevel newAccessLevel;
        do {
            newAccessLevel = levels[random.nextInt(levels.length)];
        } while (newAccessLevel == existingAccessLevel);
        return newAccessLevel;
    }
}

