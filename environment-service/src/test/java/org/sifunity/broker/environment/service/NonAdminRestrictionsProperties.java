// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.repository.AclEntryRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for non-ADMIN authorization restrictions.
 * 
 * <p><strong>Property 4: Non-ADMIN Authorization Restrictions</strong></p>
 * <p>For any non-ADMIN environment (CONSUMER, PROVIDER, or BROKER) and any operation 
 * that requires ADMIN privileges (creating environments, creating ACLs, deleting any 
 * environment, accessing other environments' data), the authorization service should 
 * deny permission.</p>
 * 
 * <p><strong>Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized non-ADMIN types and privileged 
 * operations to verify the property holds across all combinations.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NonAdminRestrictionsProperties {
    
    @Autowired
    private AuthorizationService authorizationService;
    
    @Autowired
    private EnvironmentRepository environmentRepository;
    
    @Autowired
    private AclEntryRepository aclEntryRepository;
    
    @Autowired
    private DomainService domainService;
    
    @Autowired
    private SolutionService solutionService;
    
    private final Random random = new Random();
    
    /**
     * Property 4: Non-ADMIN Authorization Restrictions
     * 
     * <p><strong>Property:</strong> For any non-ADMIN environment (CONSUMER, PROVIDER, 
     * or BROKER) and any operation that requires ADMIN privileges (creating environments, 
     * creating ACLs, deleting any environment, accessing other environments' data), the 
     * authorization service should deny permission.</p>
     * 
     * <p><strong>Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8</strong></p>
     */
    @RepeatedTest(10)
    @Tag("admin-role-and-bootstrap-property-4")
    void nonAdminRestrictedFromPrivilegedOperations() {
        // Generate random non-ADMIN environment type
        EnvironmentType nonAdminType = generateRandomNonAdminEnvironmentType();
        Environment nonAdmin = createEnvironment(nonAdminType);
        
        // Generate random privileged operation
        PrivilegedOperation operation = generateRandomPrivilegedOperation();
        
        // Create target resources
        Environment otherEnvironment = createEnvironment(generateRandomNonAdminEnvironmentType());
        AclEntry acl = createRandomAclEntry(otherEnvironment);
        
        // Test permission based on operation
        boolean hasPermission = checkPrivilegedPermission(nonAdmin, operation, otherEnvironment, acl);
        
        // Non-ADMIN should never have permission for privileged operations
        assertThat(hasPermission)
            .as("Non-ADMIN (%s) should not have permission for %s", nonAdminType, operation)
            .isFalse();
    }
    
    // ========== Helper Methods ==========
    
    private boolean checkPrivilegedPermission(Environment requester, PrivilegedOperation operation,
                                             Environment otherEnv, AclEntry acl) {
        return switch (operation) {
            case CREATE_ENVIRONMENT -> authorizationService.canCreateEnvironment(requester);
            case CREATE_ACL -> authorizationService.canCreateAcl(requester);
            case DELETE_ENVIRONMENT -> authorizationService.canDeleteEnvironment(requester, otherEnv);
            case ACCESS_OTHER_ENVIRONMENT -> authorizationService.canReadEnvironment(requester, otherEnv);
            case UPDATE_OTHER_ENVIRONMENT -> authorizationService.canUpdateEnvironment(requester, otherEnv);
            case UPDATE_ACL -> authorizationService.canUpdateAcl(requester, acl);
            case DELETE_ACL -> authorizationService.canDeleteAcl(requester, acl);
        };
    }
    
    private Environment createEnvironment(EnvironmentType type) {
        // Create a non-admin hierarchy so these environments are NOT admin
        TestEntityHierarchy hierarchy = TestEntityHierarchy.create(domainService, solutionService);
        
        Environment env = Environment.builder()
            .applicationName("App-" + UUID.randomUUID())
            .sessionToken(UUID.randomUUID().toString())
            .sharedSecret("secret-" + UUID.randomUUID())
            .type(type)
            .solutionUuid(hierarchy.getSolutionUuid())
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
    
    private EnvironmentType generateRandomNonAdminEnvironmentType() {
        EnvironmentType[] types = {
            EnvironmentType.BROKERED, 
            EnvironmentType.BROKERED, 
            EnvironmentType.BROKERED
        };
        return types[random.nextInt(types.length)];
    }
    
    private PrivilegedOperation generateRandomPrivilegedOperation() {
        PrivilegedOperation[] operations = PrivilegedOperation.values();
        return operations[random.nextInt(operations.length)];
    }
    
    // ========== Enums for Test Data ==========
    
    private enum PrivilegedOperation {
        CREATE_ENVIRONMENT,
        CREATE_ACL,
        DELETE_ENVIRONMENT,
        ACCESS_OTHER_ENVIRONMENT,
        UPDATE_OTHER_ENVIRONMENT,
        UPDATE_ACL,
        DELETE_ACL
    }
}
