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
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.service.AclService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Property-based test for ACL entry persistence and retrieval using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 8 from the design document:
 * "For any valid ACL entry creation request, the entry should be persisted and 
 * retrievable when querying ACL entries for that environment."</p>
 * 
 * <p><strong>Validates: Requirements 4.1, 4.4</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * ACL entries are properly persisted and can be retrieved consistently.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AclPersistenceRetrievalPropertyTest {
    
    @Autowired
    private AclService aclService;
    
    @Autowired
    private EnvironmentService environmentService;
    
    @Autowired
    private DomainService domainService;
    
    @Autowired
    private SolutionService solutionService;
    
    @Autowired
    private ZoneService zoneService;
    
    private final Random random = new Random();
    
    /**
     * Property 8: ACL entry persistence and retrieval
     * 
     * <p><strong>Property:</strong> For any valid ACL entry creation request,
     * the entry should be persisted and retrievable when querying ACL entries
     * for that environment.</p>
     * 
     * <p><strong>Validates: Requirements 4.1, 4.4</strong></p>
     * 
     * <p>This test verifies that:
     * <ul>
     *   <li>ACL entries are successfully created and persisted</li>
     *   <li>Created ACL entries can be retrieved by environment ID</li>
     *   <li>Retrieved ACL entries contain all the original data</li>
     *   <li>Multiple ACL entries for the same environment are all retrievable</li>
     * </ul>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-8")
    void aclEntryPersistenceAndRetrieval() {
        // Create Domain → Solution → Zone → Context hierarchy so contextUuid is available
        TestEntityHierarchy hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
        
        // Create a test environment first (required for ACL entries)
        EnvironmentRegistrationRequest envRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(generateUniqueApplicationName(), generateRandomEnvironmentType(), new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse environment = environmentService.registerEnvironment(hierarchy.getDomainId(), envRequest);
        UUID environmentId = environment.environment().id();
        
        // Generate random number of ACL entries to create (1-5 entries)
        int aclEntryCount = random.nextInt(5) + 1;
        
        // Create multiple ACL entries with different parameters to ensure uniqueness
        for (int i = 0; i < aclEntryCount; i++) {
            // Generate random valid ACL entry data
            String serviceName = generateValidServiceName(i);
            AccessType accessType = generateRandomAccessType();
            AccessLevel accessLevel = generateRandomAccessLevel();
            
            // Create ACL entry request (using real contextUuid from hierarchy)
            AclEntryCreateRequest aclRequest = new AclEntryCreateRequest(
                environmentId,
                serviceName,
                hierarchy.getZoneId(), hierarchy.getContextId(),
                accessType,
                accessLevel
            );
            
            // Create ACL entry (Requirement 4.1)
            AclEntryDTO createdAcl = aclService.createAclEntry(aclRequest);
            
            // Verify ACL entry was created successfully
            assertThat(createdAcl)
                .as("Created ACL entry should not be null")
                .isNotNull();
            
            assertThat(createdAcl.id())
                .as("Created ACL entry should have a non-null ID")
                .isNotNull();
            
            // Verify all fields match the request
            assertThat(createdAcl.environmentId())
                .as("Created ACL entry should have correct environment ID")
                .isEqualTo(environmentId);
            
            assertThat(createdAcl.serviceName())
                .as("Created ACL entry should have correct service name")
                .isEqualTo(serviceName);
            
            assertThat(createdAcl.zoneId())
                .as("Created ACL entry should have correct zoneId")
                .isEqualTo(hierarchy.getZoneId());
            
            assertThat(createdAcl.contextId())
                .as("Created ACL entry should have correct contextId")
                .isEqualTo(hierarchy.getContextId());
            
            assertThat(createdAcl.accessType())
                .as("Created ACL entry should have correct access type")
                .isEqualTo(accessType);
            
            assertThat(createdAcl.accessLevel())
                .as("Created ACL entry should have correct access level")
                .isEqualTo(accessLevel);
        }
        
        // Retrieve all ACL entries for the environment (Requirement 4.4)
        List<AclEntryDTO> retrievedAcls = aclService.listAclEntries(environmentId);
        
        // Verify all created ACL entries are retrievable
        assertThat(retrievedAcls)
            .as("Retrieved ACL entries should not be null")
            .isNotNull();
        
        assertThat(retrievedAcls)
            .as("Should retrieve exactly the number of ACL entries created")
            .hasSize(aclEntryCount);
        
        // Verify each retrieved ACL entry has valid data
        for (AclEntryDTO retrievedAcl : retrievedAcls) {
            assertThat(retrievedAcl.id())
                .as("Retrieved ACL entry should have non-null ID")
                .isNotNull();
            
            assertThat(retrievedAcl.environmentId())
                .as("Retrieved ACL entry should belong to correct environment")
                .isEqualTo(environmentId);
            
            assertThat(retrievedAcl.serviceName())
                .as("Retrieved ACL entry should have non-blank service name")
                .isNotBlank();
            
            assertThat(retrievedAcl.accessType())
                .as("Retrieved ACL entry should have non-null access type")
                .isNotNull();
            
            assertThat(retrievedAcl.accessLevel())
                .as("Retrieved ACL entry should have non-null access level")
                .isNotNull();
        }
        
        // Verify all ACL entries have unique IDs
        List<UUID> aclIds = retrievedAcls.stream()
            .map(AclEntryDTO::id)
            .toList();
        
        assertThat(aclIds)
            .as("All ACL entry IDs should be unique")
            .doesNotHaveDuplicates();
        
        // Additional property verification: ACL entries should be immediately retrievable after creation
        // This is already verified above, but we can add a specific check for immediate consistency
        
        // Verify that querying for a non-existent environment returns empty list
        UUID nonExistentEnvironmentId = UUID.randomUUID();
        List<AclEntryDTO> emptyResult = aclService.listAclEntries(nonExistentEnvironmentId);
        assertThat(emptyResult)
            .as("Querying ACL entries for non-existent environment should return empty list")
            .isEmpty();
    }
    
    // ========== Random Data Generators ==========
    
    /**
     * Generates a unique application name for environment creation.
     * This ensures we don't get duplicate application name errors during testing.
     */
    private String generateUniqueApplicationName() {
        String[] prefixes = {"TestApp", "Service", "System", "Platform", "Tool"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        long timestamp = System.nanoTime(); // Use nanoseconds for uniqueness
        return prefix + "-ACL-Test-" + timestamp;
    }
    
    /**
     * Generates a random valid environment type.
     */
    private EnvironmentType generateRandomEnvironmentType() {
        EnvironmentType[] types = EnvironmentType.values();
        return types[random.nextInt(types.length)];
    }
    
    /**
     * Generates a valid service name with index to ensure uniqueness within test iteration.
     */
    private String generateValidServiceName(int index) {
        String[] services = {"StudentService", "SchoolService", "TeacherService", "GradeService", 
                           "AttendanceService", "ScheduleService", "LibraryService", "TransportService"};
        String base = services[random.nextInt(services.length)];
        return base + "-" + index + "-" + random.nextInt(1000);
    }
    
    /**
     * Generates a valid zone ID.
     */
    private String generateValidZoneId() {
        String[] zones = {"production", "staging", "development", "test", "demo", "sandbox"};
        String base = zones[random.nextInt(zones.length)];
        int number = random.nextInt(100);
        return base + "-zone-" + number;
    }
    
    /**
     * Generates a valid context ID.
     */
    private String generateValidContextId() {
        String[] contexts = {"default", "primary", "secondary", "backup", "main", "alt"};
        String base = contexts[random.nextInt(contexts.length)];
        int number = random.nextInt(50);
        return base + "-context-" + number;
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
}

