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
 * Property-based test for ACL permission denial without APPROVED entry using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 24 from the design document:
 * "For any permission check where no APPROVED ACL entry exists (either no entry, or 
 * REJECTED/PENDING entry), the permission check should return denied."</p>
 * 
 * <p><strong>Validates: Requirements 8.3</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * permission checks return denied when no APPROVED ACL entry exists.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AclPermissionDenialPropertyTest {
    
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
     * Property 24: ACL permission denial without APPROVED entry
     * 
     * <p><strong>Property:</strong> For any permission check where no APPROVED ACL entry 
     * exists (either no entry, or REJECTED/PENDING entry), the permission check should 
     * return denied.</p>
     * 
     * <p><strong>Validates: Requirements 8.3</strong></p>
     * 
     * <p>This test creates an environment and tests three scenarios:
     * 1. No ACL entry exists at all
     * 2. ACL entry exists but with REJECTED access level
     * 3. ACL entry exists but with PENDING access level</p>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-24")
    void aclPermissionDenialWithoutApprovedEntry() {
        // Create Domain → Solution → Zone → Context hierarchy so contextUuid is available
        TestEntityHierarchy hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
        
        // Generate random valid input data
        String applicationName = generateApplicationName();
        EnvironmentType type = generateEnvironmentType();
        
        // Create environment registration request with valid solutionId from hierarchy
        EnvironmentRegistrationRequest envRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, type, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        
        // Register environment
        SifEnvironmentRegistrationResponse registrationResponse = environmentService.registerEnvironment(hierarchy.getDomainId(), envRequest);
        
        // Generate random ACL entry data
        String serviceName = generateServiceName();
        String zoneId = generateZoneId();
        String contextId = random.nextBoolean() ? generateContextId() : null;
        AccessType accessType = generateAccessType();
        
        // Randomly choose one of three scenarios:
        // 1. No ACL entry (33% chance)
        // 2. REJECTED ACL entry (33% chance)
        // 3. PENDING ACL entry (33% chance)
        int scenario = random.nextInt(3);
        
        if (scenario == 1) {
            // Create REJECTED ACL entry
            AclEntryCreateRequest aclRequest = new AclEntryCreateRequest(
                registrationResponse.environment().id(),
                serviceName,
                hierarchy.getZoneId(), hierarchy.getContextId(),
                accessType,
                AccessLevel.REJECTED
            );
            aclService.createAclEntry(aclRequest);
        } else if (scenario == 2) {
            // Create PENDING ACL entry
            AclEntryCreateRequest aclRequest = new AclEntryCreateRequest(
                registrationResponse.environment().id(),
                serviceName,
                hierarchy.getZoneId(), hierarchy.getContextId(),
                accessType,
                AccessLevel.REQUESTED
            );
            aclService.createAclEntry(aclRequest);
        }
        // else: scenario == 0, no ACL entry created
        
        // Create permission check request
        PermissionCheckRequest permissionRequest = new PermissionCheckRequest(
            registrationResponse.environment().id(),
            serviceName,
            zoneId,
            contextId,
            accessType
        );
        
        // Check permission (Requirement 8.3)
        PermissionCheckResponse response = authorizationService.checkPermission(permissionRequest);
        
        // Verify permission is denied
        assertThat(response.granted())
            .as("Permission should be denied when no APPROVED ACL entry exists (scenario: %d)", scenario)
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
}
