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
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.exception.DuplicateAclEntryException;
import org.sifunity.broker.environment.service.AclService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Property-based test for ACL entry uniqueness constraint using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 9 from the design document:
 * "For any environment, service name, zone, context, and access type combination,
 * only one ACL entry can exist (uniqueness constraint)."</p>
 * 
 * <p><strong>Validates: Requirements 4.7</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * attempting to create duplicate ACL entries throws DuplicateAclEntryException.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AclUniquenessConstraintPropertyTest {
    
    @Autowired
    private EnvironmentService environmentService;
    
    @Autowired
    private AclService aclService;
    
    @Autowired
    private DomainService domainService;
    
    @Autowired
    private SolutionService solutionService;
    
    @Autowired
    private ZoneService zoneService;
    
    private final Random random = new Random();
    
    /**
     * Property 9: ACL entry uniqueness constraint
     * 
     * <p><strong>Property:</strong> For any environment, service name, zone, context, 
     * and access type combination, only one ACL entry can exist. Attempting to create 
     * a duplicate ACL entry should throw DuplicateAclEntryException.</p>
     * 
     * <p><strong>Validates: Requirements 4.7</strong></p>
     * 
     * <p>This test creates an ACL entry with random parameters, then attempts to 
     * create another ACL entry with the same unique constraint fields but different 
     * access level, verifying that the duplicate is rejected.</p>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-9")
    void aclUniquenessConstraint() {
        // Create Domain → Solution → Zone → Context hierarchy so contextUuid is available
        TestEntityHierarchy hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
        
        // Create environment for testing
        SifEnvironmentRegistrationResponse environment = createTestEnvironment(hierarchy);
        
        // Generate random ACL entry parameters
        String serviceName = generateValidServiceName();
        AccessType accessType = generateRandomAccessType();
        AccessLevel firstAccessLevel = generateRandomAccessLevel();
        AccessLevel secondAccessLevel = generateDifferentAccessLevel(firstAccessLevel);
        
        // Create the first ACL entry (using zoneId/contextId from hierarchy)
        AclEntryCreateRequest firstRequest = new AclEntryCreateRequest(
            environment.environment().id(),
            serviceName,
            hierarchy.getZoneId(), hierarchy.getContextId(),
            accessType,
            firstAccessLevel
        );
        
        // Build a mock requester Environment for createAclEntry call
        Environment requester = new Environment();
        requester.setId(environment.environment().id());
        
        AclEntryDTO firstAclEntry = aclService.createAclEntry(hierarchy.getDomainId(), hierarchy.getSolutionId(), firstRequest, requester, true);
        
        // Verify first ACL entry was created successfully
        assertThat(firstAclEntry.id()).isNotNull();
        assertThat(firstAclEntry.environmentId()).isEqualTo(environment.environment().id());
        assertThat(firstAclEntry.serviceName()).isEqualTo(serviceName);
        assertThat(firstAclEntry.zoneId()).isEqualTo(hierarchy.getZoneId());
        assertThat(firstAclEntry.accessType()).isEqualTo(accessType);
        assertThat(firstAclEntry.accessLevel()).isEqualTo(firstAccessLevel);
        
        // Attempt to create a duplicate ACL entry with same unique constraint fields
        // but different access level (Requirement 4.7)
        AclEntryCreateRequest duplicateRequest = new AclEntryCreateRequest(
            environment.environment().id(),
            serviceName,
            hierarchy.getZoneId(), hierarchy.getContextId(),
            accessType,
            secondAccessLevel  // Different access level, but same unique constraint fields
        );
        
        // Verify that creating duplicate ACL entry throws DuplicateAclEntryException
        assertThatThrownBy(() -> aclService.createAclEntry(hierarchy.getDomainId(), hierarchy.getSolutionId(), duplicateRequest, requester, true))
            .as("Creating duplicate ACL entry should throw DuplicateAclEntryException")
            .isInstanceOf(DuplicateAclEntryException.class)
            .hasMessageContaining("ACL entry already exists")
            .hasMessageContaining(serviceName)
            .hasMessageContaining(accessType.name());
        
        // Verify that only the first ACL entry exists
        var aclEntries = aclService.listAclEntries(environment.environment().id());
        assertThat(aclEntries)
            .as("Only one ACL entry should exist after duplicate creation attempt")
            .hasSize(1);
        
        assertThat(aclEntries.get(0))
            .as("The existing ACL entry should be the first one created")
            .satisfies(acl -> {
                assertThat(acl.id()).isEqualTo(firstAclEntry.id());
                assertThat(acl.accessLevel()).isEqualTo(firstAccessLevel);
            });
        
        // Additional verification: Test that different combinations are allowed
        // Create ACL entry with different service name (should succeed)
        String differentServiceName = generateDifferentServiceName(serviceName);
        AclEntryCreateRequest differentServiceRequest = new AclEntryCreateRequest(
            environment.environment().id(),
            differentServiceName,  // Different service name
            hierarchy.getZoneId(), hierarchy.getContextId(),
            accessType,
            secondAccessLevel
        );
        
        AclEntryDTO differentServiceAcl = aclService.createAclEntry(hierarchy.getDomainId(), hierarchy.getSolutionId(), differentServiceRequest, requester, true);
        assertThat(differentServiceAcl.id())
            .as("ACL entry with different service name should be created successfully")
            .isNotNull();
        
        // Create ACL entry with different access type (should succeed)
        AccessType differentAccessType = generateDifferentAccessType(accessType);
        AclEntryCreateRequest differentAccessTypeRequest = new AclEntryCreateRequest(
            environment.environment().id(),
            serviceName,
            hierarchy.getZoneId(), hierarchy.getContextId(),
            differentAccessType,  // Different access type
            secondAccessLevel
        );
        
        AclEntryDTO differentAccessTypeAcl = aclService.createAclEntry(hierarchy.getDomainId(), hierarchy.getSolutionId(), differentAccessTypeRequest, requester, true);
        assertThat(differentAccessTypeAcl.id())
            .as("ACL entry with different access type should be created successfully")
            .isNotNull();
        
        // Verify final count
        var finalAclEntries = aclService.listAclEntries(environment.environment().id());
        assertThat(finalAclEntries)
            .as("Should have 3 ACL entries total (original + different service + different access type)")
            .hasSize(3);
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
        String[] prefixes = {"AclTest", "UniqueTest", "ConstraintTest", "PropTest"};
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
     * Generates a different service name from the given one.
     */
    private String generateDifferentServiceName(String existingServiceName) {
        String newServiceName;
        do {
            newServiceName = generateValidServiceName();
        } while (newServiceName.equals(existingServiceName));
        return newServiceName;
    }
    
    /**
     * Generates a random access type.
     */
    private AccessType generateRandomAccessType() {
        AccessType[] types = AccessType.values();
        return types[random.nextInt(types.length)];
    }
    
    /**
     * Generates a different access type from the given one.
     */
    private AccessType generateDifferentAccessType(AccessType existingAccessType) {
        AccessType[] types = AccessType.values();
        AccessType newAccessType;
        do {
            newAccessType = types[random.nextInt(types.length)];
        } while (newAccessType == existingAccessType);
        return newAccessType;
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



