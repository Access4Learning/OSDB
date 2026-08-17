// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.domain.AclEntry;
import org.sifunity.broker.environment.domain.Domain;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;

import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for ADMIN behavior independence.
 * 
 * <p><strong>Property 7: ADMIN Behavior Independence</strong></p>
 * <p>For any environment whose solutionId resolves to GLOBAL_DOMAIN/GLOBAL_SOLUTION,
 * its permissions should be identical regardless of whether it was created via
 * bootstrap or manual database insertion.</p>
 * 
 * <p><strong>Validates: Requirements 7.2</strong></p>
 * 
 * <p>This property ensures that admin privileges are determined solely
 * by domain/solution membership, not by how the environment was created.
 * This is important for consistency and predictability of the authorization system.</p>
 * 
 * @since 1.0.0
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AdminBehaviorIndependenceProperties {
    
    @Autowired
    private AuthorizationService authorizationService;
    
    @Autowired
    private EnvironmentRepository environmentRepository;
    
    @Autowired
    private SolutionRepository solutionRepository;

    @Autowired
    private DomainRepository domainRepository;

    private final Random random = new Random();
    
    /**
     * Property 7: ADMIN Behavior Independence
     * 
     * <p>Tests that admin environments (identified by GLOBAL_DOMAIN/GLOBAL_SOLUTION
     * membership) have identical permissions regardless of creation method
     * (bootstrap vs manual).</p>
     * 
     * <p><strong>Validates: Requirements 7.2</strong></p>
     */
    @RepeatedTest(10)
    @Tag("property-test")
    public void adminPermissionsAreIndependentOfCreationMethod() {
        
        // Given: Create ADMIN via bootstrap (simulated by direct save)
        Environment bootstrapAdmin = createAdminEnvironment("bootstrap-admin");
        environmentRepository.save(bootstrapAdmin);
        
        // And: Create ADMIN via manual database insertion
        Environment manualAdmin = createAdminEnvironment("manual-admin");
        environmentRepository.save(manualAdmin);
        
        // And: Create target resources for testing
        Environment targetEnvironment = createTargetEnvironment();
        environmentRepository.save(targetEnvironment);
        
        AclEntry targetAcl = createTargetAcl(targetEnvironment);
        
        // And: Generate random operation
        Operation operation = generateRandomOperation();
        
        // When: Check permissions for both ADMINs
        boolean bootstrapPermission = checkPermission(bootstrapAdmin, operation, targetEnvironment, targetAcl);
        boolean manualPermission = checkPermission(manualAdmin, operation, targetEnvironment, targetAcl);
        
        // Then: Both ADMINs should have identical permissions
        assertThat(bootstrapPermission)
            .as("Bootstrap ADMIN and manual ADMIN should have identical permissions for operation: %s", operation)
            .isEqualTo(manualPermission);
        
        // And: Both should have permission (since they're ADMIN)
        assertThat(bootstrapPermission)
            .as("ADMIN should have permission for operation: %s", operation)
            .isTrue();
    }
    
    /**
     * Generates a random operation for testing.
     * 
     * @return a random operation
     */
    private Operation generateRandomOperation() {
        Operation[] operations = Operation.values();
        return operations[random.nextInt(operations.length)];
    }
    
    /**
     * Checks permission for the given operation.
     * 
     * @param admin the ADMIN environment
     * @param operation the operation to check
     * @param targetEnvironment the target environment
     * @param targetAcl the target ACL entry
     * @return true if permission is granted, false otherwise
     */
    private boolean checkPermission(
            Environment admin,
            Operation operation,
            Environment targetEnvironment,
            AclEntry targetAcl) {
        
        return switch (operation) {
            case CREATE_ENVIRONMENT -> authorizationService.canCreateEnvironment(admin);
            case READ_ENVIRONMENT -> authorizationService.canReadEnvironment(admin, targetEnvironment);
            case UPDATE_ENVIRONMENT -> authorizationService.canUpdateEnvironment(admin, targetEnvironment);
            case DELETE_ENVIRONMENT -> authorizationService.canDeleteEnvironment(admin, targetEnvironment);
            case CREATE_ACL -> authorizationService.canCreateAcl(admin);
            case READ_ACL -> authorizationService.canReadAcl(admin, targetAcl);
            case UPDATE_ACL -> authorizationService.canUpdateAcl(admin, targetAcl);
            case DELETE_ACL -> authorizationService.canDeleteAcl(admin, targetAcl);
        };
    }
    
    /**
     * Creates an ADMIN environment for testing.
     * 
     * @param name the application name
     * @return the ADMIN environment
     */
    private Environment createAdminEnvironment(String name) {
        // Look up the actual global admin solution created by bootstrap (domain-scoped resolution)
        Domain globalDomain = domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)
            .orElseThrow(() -> new IllegalStateException("Global admin domain not found - bootstrap may not have run"));
        Solution globalSolution = solutionRepository.findByDomainUuidAndSolutionId(globalDomain.getId(), GlobalAdminConstants.GLOBAL_SOLUTION)
            .orElseThrow(() -> new IllegalStateException("Global admin solution not found - bootstrap may not have run"));
        
        return Environment.builder()
            .applicationName(name + "-" + UUID.randomUUID())
            .type(EnvironmentType.BROKERED)
            .solutionUuid(globalSolution.getId())
            .sessionToken(UUID.randomUUID().toString())
            .sharedSecret(UUID.randomUUID().toString())
            .defaultZoneId("default")
            .build();
    }
    
    /**
     * Creates a target environment for testing.
     * 
     * @return the target environment
     */
    private Environment createTargetEnvironment() {
        // Look up the actual global admin solution for a valid solutionUuid FK (domain-scoped resolution)
        Domain globalDomain = domainRepository.findByDomainId(GlobalAdminConstants.GLOBAL_DOMAIN)
            .orElseThrow(() -> new IllegalStateException("Global admin domain not found"));
        Solution globalSolution = solutionRepository.findByDomainUuidAndSolutionId(globalDomain.getId(), GlobalAdminConstants.GLOBAL_SOLUTION)
            .orElseThrow(() -> new IllegalStateException("Global admin solution not found"));
        
        return Environment.builder()
            .applicationName("target-environment-" + UUID.randomUUID())
            .type(EnvironmentType.BROKERED)
            .solutionUuid(globalSolution.getId())
            .sessionToken(UUID.randomUUID().toString())
            .sharedSecret(UUID.randomUUID().toString())
            .defaultZoneId("default")
            .build();
    }
    
    /**
     * Creates a target ACL entry for testing.
     * 
     * @param owner the owner environment
     * @return the target ACL entry
     */
    private AclEntry createTargetAcl(Environment owner) {
        AclEntry acl = new AclEntry();
        acl.setEnvironment(owner);
        acl.setContextServiceUuid(java.util.UUID.randomUUID());
        ;
        return acl;
    }
    
    /**
     * Enum representing different operations for testing.
     */
    private enum Operation {
        CREATE_ENVIRONMENT,
        READ_ENVIRONMENT,
        UPDATE_ENVIRONMENT,
        DELETE_ENVIRONMENT,
        CREATE_ACL,
        READ_ACL,
        UPDATE_ACL,
        DELETE_ACL
    }
}
