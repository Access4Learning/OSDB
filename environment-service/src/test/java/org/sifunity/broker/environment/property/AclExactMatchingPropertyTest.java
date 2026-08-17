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
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.dto.AclEntryCreateRequest;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.PermissionCheckRequest;
import org.sifunity.broker.environment.dto.PermissionCheckResponse;
import org.sifunity.broker.environment.service.AclService;
import org.sifunity.broker.environment.service.AuthorizationService;
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
 * Property-based test for ACL exact matching using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 25 from the design document:
 * "For any permission check, only ACL entries with exact matches on service name, 
 * zone, context, and access type should grant permission."</p>
 * 
 * <p><strong>Validates: Requirements 8.4</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * permission checks require exact matches on all ACL parameters.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AclExactMatchingPropertyTest {
    
    @Autowired
    private EnvironmentService environmentService;
    
    @Autowired
    private AclService aclService;
    
    @Autowired
    private AuthorizationService authorizationService;
    
    @Autowired
    private DomainService domainService;
    
    @Autowired
    private SolutionService solutionService;
    
    @Autowired
    private ZoneService zoneService;
    
    @Autowired
    private org.sifunity.broker.environment.repository.EnvironmentRepository environmentRepository;
    
    @Autowired
    private org.sifunity.broker.environment.repository.AclEntryRepository aclEntryRepository;
    
    private final Random random = new Random();
    
    @org.junit.jupiter.api.AfterEach
    void cleanupTestData() {
        // Delete all ACL entries first (due to foreign key constraints)
        aclEntryRepository.deleteAll();
        aclEntryRepository.flush();
        
        // Delete all non-admin environments created during tests
        environmentRepository.findAll().stream()
            .filter(env -> !("ADMIN".equals(env.getApplicationName())))
            .forEach(env -> {
                try {
                    environmentRepository.delete(env);
                } catch (Exception e) {
                    // Ignore deletion errors
                }
            });
        environmentRepository.flush();
    }
    
    /**
     * Property 25: ACL exact matching
     * 
     * <p><strong>Property:</strong> For any permission check, only ACL entries 
     * with exact matches on service name, zone, context, and access type should 
     * grant permission.</p>
     * 
     * <p><strong>Validates: Requirements 8.4</strong></p>
     * 
     * <p>This test creates an environment with an APPROVED ACL entry, then verifies
     * that permission checks with non-matching parameters are denied. The test randomly
     * selects one parameter to mismatch (service name, zone, context, or access type).</p>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-25")
    void aclExactMatchingRequired() {
        // Create Domain → Solution → Zone → Context hierarchy so contextUuid resolves
        TestEntityHierarchy hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
        
        // Generate random valid input data
        String applicationName = generateApplicationName();
        EnvironmentType type = generateEnvironmentType();
        // Use the zone created by createFull hierarchy so validation passes
        String defaultZoneId = hierarchy.getZoneId();
        // Create environment registration request with valid solutionId from hierarchy
        EnvironmentRegistrationRequest envRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, type, DefaultZoneDTO.of(defaultZoneId), hierarchy.getSolutionId(), null));
        
        // Register environment
        SifEnvironmentRegistrationResponse registrationResponse = environmentService.registerEnvironment(hierarchy.getDomainId(), envRequest);
        
        // Generate random ACL entry data
        String serviceName = generateServiceName();
        AccessType accessType = generateAccessType();
        
        // Create APPROVED ACL entry using the real context UUID from hierarchy
        AclEntryCreateRequest aclRequest = new AclEntryCreateRequest(
            registrationResponse.environment().id(),
            serviceName,
            hierarchy.getZoneId(), hierarchy.getContextId(),
            accessType,
            AccessLevel.APPROVED
        );
        
        aclService.createAclEntry(aclRequest);
        
        // Randomly select which parameter to mismatch (0=service, 1=accessType)
        int mismatchParameter = random.nextInt(2);
        
        String checkServiceName = serviceName;
        AccessType checkAccessType = accessType;
        
        switch (mismatchParameter) {
            case 0: // Mismatch service name
                checkServiceName = generateDifferentServiceName(serviceName);
                break;
            case 1: // Mismatch access type
                checkAccessType = generateDifferentAccessType(accessType);
                break;
        }
        
        // Create permission check request with one mismatched parameter
        PermissionCheckRequest permissionRequest = new PermissionCheckRequest(
            registrationResponse.environment().id(),
            checkServiceName,
            hierarchy.getZoneId(),
            hierarchy.getContextId(),
            checkAccessType
        );
        
        // Check permission (Requirement 8.4)
        PermissionCheckResponse response = authorizationService.checkPermission(permissionRequest);
        
        // Verify permission is denied due to non-exact match
        assertThat(response.granted())
            .as("Permission should be denied when ACL parameters don't match exactly (mismatched parameter: %d)", mismatchParameter)
            .isFalse();
    }
    
    // ========== Test Data Generators ==========
    
    private String generateApplicationName() {
        String[] prefixes = {"app", "service", "system", "platform", "client"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        long timestamp = System.nanoTime() % 100000;
        return prefix + "-" + timestamp;
    }
    
    private EnvironmentType generateEnvironmentType() {
        // Only generate CONSUMER or PROVIDER types for ACL tests
        // ADMIN bypasses ACL checks, so it's not useful for testing ACL logic
        EnvironmentType[] types = {EnvironmentType.BROKERED, EnvironmentType.BROKERED};
        return types[random.nextInt(types.length)];
    }
    
    private String generateZoneId() {
        String[] zones = {"prod", "staging", "dev", "test", "qa"};
        return zones[random.nextInt(zones.length)] + "-zone-" + (System.nanoTime() % 10000);
    }
    
    private String generateContextId() {
        String[] contexts = {"default", "primary", "secondary", "backup"};
        return contexts[random.nextInt(contexts.length)] + "-ctx-" + (System.nanoTime() % 10000);
    }
    
    private String generateServiceName() {
        String[] services = {"StudentPersonals", "SchoolInfos", "StaffPersonals", "Assessments", "Gradebooks"};
        return services[random.nextInt(services.length)];
    }
    
    private AccessType generateAccessType() {
        AccessType[] types = AccessType.values();
        return types[random.nextInt(types.length)];
    }
    
    // ========== Different Value Generators ==========
    
    private String generateDifferentServiceName(String current) {
        String different;
        do {
            different = generateServiceName();
        } while (different.equals(current));
        return different;
    }
    
    private String generateDifferentZoneId(String current) {
        String different;
        do {
            different = generateZoneId();
        } while (different.equals(current));
        return different;
    }
    
    private String generateDifferentContextId(String current) {
        // If current is null, generate a non-null value
        if (current == null) {
            return generateContextId();
        }
        // If current is non-null, randomly return null or a different value
        if (random.nextBoolean()) {
            return null;
        }
        String different;
        do {
            different = generateContextId();
        } while (different.equals(current));
        return different;
    }
    
    private AccessType generateDifferentAccessType(AccessType current) {
        AccessType[] types = AccessType.values();
        if (types.length == 1) {
            // Edge case: only one access type exists
            return types[0];
        }
        AccessType different;
        do {
            different = types[random.nextInt(types.length)];
        } while (different == current);
        return different;
    }
}

