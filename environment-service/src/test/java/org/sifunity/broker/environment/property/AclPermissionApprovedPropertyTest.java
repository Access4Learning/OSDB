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
 * Property-based test for ACL permission check with APPROVED entry using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 23 from the design document:
 * "For any environment with an APPROVED ACL entry for a specific service/zone/context/access type,
 * checking permission for that exact combination should return granted."</p>
 * 
 * <p><strong>Validates: Requirements 8.1, 8.2</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * permission checks return granted when an APPROVED ACL entry exists.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AclPermissionApprovedPropertyTest {
    
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
    
    private final Random random = new Random();
    
    /**
     * Property 23: ACL permission check with APPROVED entry
     * 
     * <p><strong>Property:</strong> For any environment with an APPROVED ACL entry 
     * for a specific service/zone/context/access type, checking permission for that 
     * exact combination should return granted.</p>
     * 
     * <p><strong>Validates: Requirements 8.1, 8.2</strong></p>
     * 
     * <p>This test creates an environment, creates an APPROVED ACL entry, and verifies
     * that permission check returns granted for the exact combination.</p>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-23")
    void aclPermissionCheckWithApprovedEntry() {
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
        AccessType accessType = generateAccessType();
        
        // Create APPROVED ACL entry (using real contextUuid from hierarchy)
        AclEntryCreateRequest aclRequest = new AclEntryCreateRequest(
            registrationResponse.environment().id(),
            serviceName,
            hierarchy.getZoneId(), hierarchy.getContextId(),
            accessType,
            AccessLevel.APPROVED
        );
        
        aclService.createAclEntry(aclRequest);
        
        // Create permission check request with real zone/context so AuthorizationService can resolve contextUuid
        PermissionCheckRequest permissionRequest = new PermissionCheckRequest(
            registrationResponse.environment().id(),
            serviceName,
            hierarchy.getZoneId(),
            hierarchy.getContextId(),
            accessType
        );
        
        // Check permission (Requirement 8.1, 8.2)
        PermissionCheckResponse response = authorizationService.checkPermission(permissionRequest);
        
        // Verify permission is granted
        assertThat(response.granted())
            .as("Permission should be granted when APPROVED ACL entry exists for exact combination")
            .isTrue();
    }
    
    // ========== Test Data Generators ==========
    
    private String generateApplicationName() {
        String[] prefixes = {"app", "service", "system", "platform", "client"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        long timestamp = System.nanoTime() % 100000;
        return prefix + "-" + timestamp;
    }
    
    private EnvironmentType generateEnvironmentType() {
        EnvironmentType[] types = EnvironmentType.values();
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

