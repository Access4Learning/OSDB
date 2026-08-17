// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.sifunity.broker.environment.dto.PermissionCheckRequest;
import org.sifunity.broker.environment.dto.PermissionCheckResponse;
import org.sifunity.broker.environment.service.AclService;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * End-to-end integration test for authorization flow using H2 in-memory database.
 * 
 * <p>This test validates the complete authorization flow:
 * <ol>
 *   <li>Create environment → Create APPROVED ACL entry → Check permission → Verify granted</li>
 *   <li>Check permission without ACL entry → Verify denied</li>
 *   <li>Create REJECTED ACL entry → Check permission → Verify denied</li>
 * </ol>
 * 
 * <p><strong>Validates: Requirements 8.1, 8.2, 8.3</strong></p>
 * 
 * <p>This test uses H2 in-memory database for fast execution without Docker.
 * For production-like testing with actual PostgreSQL, see {@link AuthorizationFlowIntegrationTest}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthorizationFlowH2IntegrationTest {
    
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

    private TestEntityHierarchy hierarchy;

    @BeforeEach
    void setUp() {
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);
    }
    
    /**
     * Test authorization flow with APPROVED ACL entry.
     * 
     * <p>This test validates:
     * <ul>
     *   <li>Requirement 8.1: Permission check queries ACL table</li>
     *   <li>Requirement 8.2: APPROVED ACL entry grants permission</li>
     * </ul>
     */
    @Test
    void testAuthorizationFlowWithApprovedAclEntry() {
        // ========== Step 1: Register Environment ==========
        
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("H2AuthzTestApp-Approved-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse registrationResponse = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), registrationRequest);
        
        UUID environmentId = registrationResponse.environment().id();
        
        // ========== Step 2: Create APPROVED ACL Entry ==========
        
        AclEntryCreateRequest aclRequest = new AclEntryCreateRequest(
            environmentId,
            "StudentService",
            hierarchy.getZoneId(), hierarchy.getContextId(),
            AccessType.QUERY,
            AccessLevel.APPROVED
        );
        
        AclEntryDTO aclEntry = aclService.createAclEntry(aclRequest);
        
        // Verify ACL entry was created
        assertThat(aclEntry).isNotNull();
        assertThat(aclEntry.id()).isNotNull();
        assertThat(aclEntry.environmentId()).isEqualTo(environmentId);
        assertThat(aclEntry.serviceName()).isEqualTo("StudentService");
        assertThat(aclEntry.zoneId()).isEqualTo(hierarchy.getZoneId());
        assertThat(aclEntry.accessType()).isEqualTo(AccessType.QUERY);
        assertThat(aclEntry.accessLevel()).isEqualTo(AccessLevel.APPROVED);
        
        // ========== Step 3: Check Permission ==========
        
        PermissionCheckRequest permissionRequest = new PermissionCheckRequest(
            environmentId,
            "StudentService",
            hierarchy.getZoneId(),
            hierarchy.getContextId(),
            AccessType.QUERY
        );
        
        PermissionCheckResponse permissionResponse = 
            authorizationService.checkPermission(permissionRequest);
        
        // ========== Step 4: Verify Permission Granted ==========
        
        // Verify permission is granted (Requirement 8.2)
        assertThat(permissionResponse).isNotNull();
        assertThat(permissionResponse.granted())
            .as("Permission should be granted for APPROVED ACL entry")
            .isTrue();
    }
    
    /**
     * Test authorization flow without ACL entry.
     * 
     * <p>This test validates:
     * <ul>
     *   <li>Requirement 8.3: Permission denied when no ACL entry exists</li>
     * </ul>
     */
    @Test
    void testAuthorizationFlowWithoutAclEntry() {
        // ========== Step 1: Register Environment ==========
        
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("H2AuthzTestApp-NoAcl-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse registrationResponse = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), registrationRequest);
        
        UUID environmentId = registrationResponse.environment().id();
        
        // ========== Step 2: Check Permission Without Creating ACL Entry ==========
        
        PermissionCheckRequest permissionRequest = new PermissionCheckRequest(
            environmentId,
            "TeacherService",
            hierarchy.getZoneId(),
            hierarchy.getContextId(),
            AccessType.PROVIDE
        );
        
        PermissionCheckResponse permissionResponse = 
            authorizationService.checkPermission(permissionRequest);
        
        // ========== Step 3: Verify Permission Denied ==========
        
        // Verify permission is denied when no ACL entry exists (Requirement 8.3)
        assertThat(permissionResponse).isNotNull();
        assertThat(permissionResponse.granted())
            .as("Permission should be denied when no ACL entry exists")
            .isFalse();
    }
    
    /**
     * Test authorization flow with REJECTED ACL entry.
     * 
     * <p>This test validates:
     * <ul>
     *   <li>Requirement 8.3: Permission denied when ACL entry is REJECTED</li>
     * </ul>
     */
    @Test
    void testAuthorizationFlowWithRejectedAclEntry() {
        // ========== Step 1: Register Environment ==========
        
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("H2AuthzTestApp-Rejected-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse registrationResponse = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), registrationRequest);
        
        UUID environmentId = registrationResponse.environment().id();
        
        // ========== Step 2: Create REJECTED ACL Entry ==========
        
        AclEntryCreateRequest aclRequest = new AclEntryCreateRequest(
            environmentId,
            "GradeService",
            hierarchy.getZoneId(), hierarchy.getContextId(),
            AccessType.SUBSCRIBE,
            AccessLevel.REJECTED
        );
        
        AclEntryDTO aclEntry = aclService.createAclEntry(aclRequest);
        
        // Verify ACL entry was created with REJECTED status
        assertThat(aclEntry).isNotNull();
        assertThat(aclEntry.accessLevel()).isEqualTo(AccessLevel.REJECTED);
        
        // ========== Step 3: Check Permission ==========
        
        PermissionCheckRequest permissionRequest = new PermissionCheckRequest(
            environmentId,
            "GradeService",
            hierarchy.getZoneId(),
            hierarchy.getContextId(),
            AccessType.SUBSCRIBE
        );
        
        PermissionCheckResponse permissionResponse = 
            authorizationService.checkPermission(permissionRequest);
        
        // ========== Step 4: Verify Permission Denied ==========
        
        // Verify permission is denied for REJECTED ACL entry (Requirement 8.3)
        assertThat(permissionResponse).isNotNull();
        assertThat(permissionResponse.granted())
            .as("Permission should be denied for REJECTED ACL entry")
            .isFalse();
    }
}

