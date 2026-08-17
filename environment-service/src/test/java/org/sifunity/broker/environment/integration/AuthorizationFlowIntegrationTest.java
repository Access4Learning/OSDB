// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
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
 * End-to-end integration test for authorization flow using Testcontainers.
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
 * <p>This test uses Testcontainers to spin up a real PostgreSQL database,
 * ensuring that authorization works correctly in a production-like environment.</p>
 */
@SpringBootTest
@Testcontainers
class AuthorizationFlowIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
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
     * Configure Spring Boot to use the Testcontainers PostgreSQL instance.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        
        // Configure JPA for PostgreSQL
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        
        // Enable Flyway for schema migrations
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        
        // Use plaintext secrets backend for tests
        registry.add("secrets.backend.type", () -> "plaintext");
    }
    
    @BeforeAll
    static void beforeAll() {
        // Ensure the container is started
        assertThat(postgres.isRunning()).isTrue();
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
        
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AuthzTestApp-Approved-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        
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
        
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AuthzTestApp-NoAcl-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        
        SifEnvironmentRegistrationResponse registrationResponse = 
            environmentService.registerEnvironment(hierarchy.getDomainId(), registrationRequest);
        
        UUID environmentId = registrationResponse.environment().id();
        
        // ========== Step 2: Check Permission Without Creating ACL Entry ==========
        
        PermissionCheckRequest permissionRequest = new PermissionCheckRequest(
            environmentId,
            "TeacherService",
            hierarchy.getZoneId(),
            null,
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
        
        EnvironmentRegistrationRequest registrationRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AuthzTestApp-Rejected-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null));
        
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


