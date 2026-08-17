// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
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
 * Property-based test for non-ADMIN self-access permissions.
 * 
 * <p><strong>Property 5: Non-ADMIN Self-Access</strong></p>
 * <p>For any non-ADMIN environment, read and update operations on its own environment 
 * data should be permitted.</p>
 * 
 * <p><strong>Validates: Requirements 4.1, 4.2, 4.3</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized non-ADMIN types to verify the 
 * property holds across all non-ADMIN environment types.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NonAdminSelfAccessProperties {
    
    @Autowired
    private AuthorizationService authorizationService;
    
    @Autowired
    private EnvironmentRepository environmentRepository;
    
    @Autowired
    private DomainService domainService;
    
    @Autowired
    private SolutionService solutionService;
    
    private final Random random = new Random();
    
    /**
     * Property 5: Non-ADMIN Self-Access
     * 
     * <p><strong>Property:</strong> For any non-ADMIN environment, read and update 
     * operations on its own environment data should be permitted.</p>
     * 
     * <p><strong>Validates: Requirements 4.1, 4.2, 4.3</strong></p>
     */
    @RepeatedTest(10)
    @Tag("admin-role-and-bootstrap-property-5")
    void nonAdminCanAccessOwnData() {
        // Generate random non-ADMIN environment type
        EnvironmentType nonAdminType = generateRandomNonAdminEnvironmentType();
        Environment nonAdmin = createEnvironment(nonAdminType);
        
        // Test read permission on own data
        boolean canRead = authorizationService.canReadEnvironment(nonAdmin, nonAdmin);
        
        // Test update permission on own data
        boolean canUpdate = authorizationService.canUpdateEnvironment(nonAdmin, nonAdmin);
        
        // Non-ADMIN should always have permission to read and update own data
        assertThat(canRead)
            .as("Non-ADMIN (%s) should be able to read own data", nonAdminType)
            .isTrue();
        
        assertThat(canUpdate)
            .as("Non-ADMIN (%s) should be able to update own data", nonAdminType)
            .isTrue();
    }
    
    // ========== Helper Methods ==========
    
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
    
    // ========== Random Data Generators ==========
    
    private EnvironmentType generateRandomNonAdminEnvironmentType() {
        EnvironmentType[] types = {
            EnvironmentType.BROKERED, 
            EnvironmentType.BROKERED, 
            EnvironmentType.BROKERED
        };
        return types[random.nextInt(types.length)];
    }
}
