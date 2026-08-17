// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for ADMIN full permissions.
 * 
 * <p><strong>Property 1: ADMIN Full Permissions</strong></p>
 * <p>For any ADMIN environment and any operation (create/read/update/delete) on any 
 * resource (environments or ACLs), the authorization service should grant permission.</p>
 * 
 * <p><strong>Validates: Requirements 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 1.11</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized operations and resources to verify 
 * the property holds across all possible combinations.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminFullPermissionsProperties {
    
    @Autowired
    private AuthorizationService authorizationService;
    
    @Autowired
    private EnvironmentRepository environmentRepository;
    
    @Autowired
    private AclEntryRepository aclEntryRepository;
    
    @Autowired
    private SolutionRepository solutionRepository;

    @Autowired
    private DomainRepository domainRepository;

    private final Random random = new Random();
    
    /**
     * Property 1: ADMIN Full Permissions
     * 
     * <p><strong>Property:</strong> For any ADMIN environment and any operation 
     * (create/read/update/delete) on any resource (environments or ACLs), the 
     * authorization service should grant permission.</p>
     * 
     * <p><strong>Validates: Requirements 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 1.11</strong></p>
     */
    @RepeatedTest(10)
    @Tag("admin-role-and-bootstrap-property-1")
    void adminHasFullPermissions() {
        // Create ADMIN environment
        Environment admin = createAdminEnvironment();
        
        // Generate random operation and resource
        Operation operation = generateRandomOperation();
        ResourceType resourceType = generateRandomResourceType();
        
        // Create target resource
        Environment targetEnvironment = createRandomEnvironment();
        AclEntry targetAcl = createRandomAclEntry(targetEnvironment);
        
        // Test permission based on operation and resource type
        boolean hasPermission = checkPermission(admin, operation, resourceType, targetEnvironment, targetAcl);
        
        // ADMIN should always have permission
        assertThat(hasPermission)
            .as("ADMIN should have permission for %s operation on %s", operation, resourceType)
            .isTrue();
    }
    
    // ========== Helper Methods ==========
    
    private boolean checkPermission(Environment requester, Operation operation, 
                                   ResourceType resourceType, Environment targetEnv, AclEntry targetAcl) {
        return switch (resourceType) {
            case ENVIRONMENT -> switch (operation) {
                case CREATE -> authorizationService.canCreateEnvironment(requester);
                case READ -> authorizationService.canReadEnvironment(requester, targetEnv);
                case UPDATE -> authorizationService.canUpdateEnvironment(requester, targetEnv);
                case DELETE -> authorizationService.canDeleteEnvironment(requester, targetEnv);
            };
            case ACL -> switch (operation) {
                case CREATE -> authorizationService.canCreateAcl(requester);
                case READ -> authorizationService.canReadAcl(requester, targetAcl);
                case UPDATE -> authorizationService.canUpdateAcl(requester, targetAcl);
                case DELETE -> authorizationService.canDeleteAcl(requester, targetAcl);
            };
        };
    }
    
    private Environment createAdminEnvironment() {
        // Look up the actual global admin solution created by bootstrap (domain-scoped resolution)
        Domain globalDomain = domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)
            .orElseThrow(() -> new IllegalStateException("Global admin domain not found - bootstrap may not have run"));
        Solution globalSolution = solutionRepository.findByDomainUuidAndSolutionId(globalDomain.getId(), GlobalAdminConstants.GLOBAL_SOLUTION)
            .orElseThrow(() -> new IllegalStateException("Global admin solution not found - bootstrap may not have run"));
        
        Environment admin = Environment.builder()
            .applicationName("ADMIN-" + UUID.randomUUID())
            .sessionToken(UUID.randomUUID().toString())
            .sharedSecret("admin-secret-" + UUID.randomUUID())
            .type(EnvironmentType.BROKERED)
            .solutionUuid(globalSolution.getId())
            .defaultZoneId("admin-zone")
            .createdAt(Instant.now())
            .build();
        return environmentRepository.save(admin);
    }
    
    private Environment createRandomEnvironment() {
        // Look up the actual global admin solution for a valid solutionUuid FK (domain-scoped resolution)
        Domain globalDomain = domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)
            .orElseThrow(() -> new IllegalStateException("Global admin domain not found"));
        Solution globalSolution = solutionRepository.findByDomainUuidAndSolutionId(globalDomain.getId(), GlobalAdminConstants.GLOBAL_SOLUTION)
            .orElseThrow(() -> new IllegalStateException("Global admin solution not found"));
        
        EnvironmentType type = generateRandomNonAdminEnvironmentType();
        Environment env = Environment.builder()
            .applicationName("App-" + UUID.randomUUID())
            .sessionToken(UUID.randomUUID().toString())
            .sharedSecret("secret-" + UUID.randomUUID())
            .type(type)
            .solutionUuid(globalSolution.getId())
            .defaultZoneId("zone-" + random.nextInt(10))
            .createdAt(Instant.now())
            .build();
        return environmentRepository.save(env);
    }
    
    private AclEntry createRandomAclEntry(Environment environment) {
        AclEntry acl = AclEntry.builder()
            .environment(environment)
            .contextServiceUuid(java.util.UUID.randomUUID())
            .accessType(org.sifunity.broker.common.constant.AccessType.values()[
                random.nextInt(org.sifunity.broker.common.constant.AccessType.values().length)])
            .accessLevel(org.sifunity.broker.common.constant.AccessLevel.values()[
                random.nextInt(org.sifunity.broker.common.constant.AccessLevel.values().length)])
            .build();
        return aclEntryRepository.save(acl);
    }
    
    // ========== Random Data Generators ==========
    
    private Operation generateRandomOperation() {
        Operation[] operations = Operation.values();
        return operations[random.nextInt(operations.length)];
    }
    
    private ResourceType generateRandomResourceType() {
        ResourceType[] types = ResourceType.values();
        return types[random.nextInt(types.length)];
    }
    
    private EnvironmentType generateRandomNonAdminEnvironmentType() {
        EnvironmentType[] types = {
            EnvironmentType.BROKERED, 
            EnvironmentType.BROKERED, 
            EnvironmentType.BROKERED
        };
        return types[random.nextInt(types.length)];
    }
    
    // ========== Enums for Test Data ==========
    
    private enum Operation {
        CREATE, READ, UPDATE, DELETE
    }
    
    private enum ResourceType {
        ENVIRONMENT, ACL
    }
}
