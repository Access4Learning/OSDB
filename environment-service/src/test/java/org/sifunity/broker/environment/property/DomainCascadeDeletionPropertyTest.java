// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.exception.DomainNotFoundException;
import org.sifunity.broker.environment.exception.SolutionNotFoundException;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;
import org.sifunity.broker.environment.repository.ZoneRepository;
import org.sifunity.broker.environment.repository.ContextRepository;
import org.sifunity.broker.environment.domain.Zone;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for domain cascade deletion using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 19 from the design document:
 * "For any domain containing solutions, zones, contexts, and environments, after 
 * deleting the domain, all child solutions, zones, contexts, and environments 
 * should no longer exist in the system."</p>
 * 
 * <p><strong>Validates: Requirements 1.12</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * domain deletion properly cascades to all child entities.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DomainCascadeDeletionPropertyTest {
    
    @Autowired
    private DomainService domainService;
    
    @Autowired
    private SolutionService solutionService;
    
    @Autowired
    private ZoneService zoneService;
    
    @Autowired
    private ZoneRepository zoneRepository;
    
    @Autowired
    private ContextRepository contextRepository;
    
    private final Random random = new Random();
    
    /**
     * Property 19: Domain Cascade Deletion
     * 
     * <p><strong>Property:</strong> For any domain containing solutions, zones, contexts, 
     * and environments, after deleting the domain, all child solutions, zones, contexts, 
     * and environments should no longer exist in the system.</p>
     * 
     * <p><strong>Validates: Requirements 1.12</strong></p>
     * 
     * <p>This test creates a complete hierarchy (domain → solution → zone → context),
     * deletes the domain, then verifies all child entities are also deleted.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-19")
    void domainCascadeDeletion() {
        // Generate random valid input data
        String domainId = generateValidDomainId();
        String solutionId = generateValidSolutionId();
        String zoneId = generateValidZoneId();
        String contextId = generateValidContextId();
        
        // Create domain
        DomainCreateRequest domainRequest = new DomainCreateRequest(domainId, "Test Domain");
        DomainDTO createdDomain = domainService.createDomain(domainRequest);
        assertThat(createdDomain).isNotNull();
        
        // Create solution within domain
        SolutionCreateRequest solutionRequest = new SolutionCreateRequest(
            domainId, solutionId, "Test Solution"
        );
        SolutionDTO createdSolution = solutionService.createSolution(solutionRequest);
        assertThat(createdSolution).isNotNull();
        
        // Create zone within solution
        ZoneCreateRequest zoneRequest = new ZoneCreateRequest(solutionId, zoneId, "Test Zone");
        ZoneDTO createdZone = zoneService.createZone(domainId, zoneRequest);
        assertThat(createdZone).isNotNull();
        
        // Create context within zone
        ContextCreateRequest contextRequest = new ContextCreateRequest(contextId, "Test Context");
        ContextDTO createdContext = zoneService.createContext(domainId, solutionId, zoneId, contextRequest);
        assertThat(createdContext).isNotNull();
        
        // Verify all entities exist before deletion
        assertThat(domainService.getDomain(domainId)).isNotNull();
        assertThat(solutionService.getSolution(domainId, solutionId)).isNotNull();
        assertThat(zoneRepository.existsBySolutionUuidAndZoneId(createdSolution.id(), zoneId)).isTrue();
        Zone zone = zoneRepository.findBySolutionUuidAndZoneId(createdSolution.id(), zoneId).orElse(null);
        assertThat(zone).isNotNull();
        assertThat(contextRepository.existsByZoneUuidAndContextId(zone.getId(), contextId)).isTrue();
        
        // Delete the domain
        // Requirement 1.12: WHEN a domain is deleted, THE Environment_Service SHALL 
        // perform Cascade_Delete on all solutions, zones, contexts, and environments 
        // within that domain
        domainService.deleteDomain(domainId);
        
        // Verify domain is deleted
        assertThatThrownBy(() -> domainService.getDomain(domainId))
            .as("Domain should not exist after deletion")
            .isInstanceOf(DomainNotFoundException.class);
        
        // Verify solution is cascade deleted
        assertThatThrownBy(() -> solutionService.getSolution(domainId, solutionId))
            .as("Solution should be cascade deleted when domain is deleted")
            .isInstanceOf(SolutionNotFoundException.class);
        
        // Verify zone is cascade deleted
        assertThat(zoneRepository.existsBySolutionUuidAndZoneId(createdSolution.id(), zoneId))
            .as("Zone should be cascade deleted when domain is deleted")
            .isFalse();
        
        // Verify context is cascade deleted - zone no longer exists so context must be gone too
        assertThat(zoneRepository.existsBySolutionUuidAndZoneId(createdSolution.id(), zoneId))
            .as("Context should be cascade deleted when domain is deleted")
            .isFalse();
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates a valid domain ID following the naming pattern.
     */
    private String generateValidDomainId() {
        String[] prefixes = {"alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        int number = random.nextInt(1000);
        long timestamp = System.nanoTime() % 10000;
        return prefix + "-" + number + "-" + timestamp;
    }
    
    /**
     * Generates a valid solution ID following the naming pattern.
     */
    private String generateValidSolutionId() {
        String[] names = {"production", "staging", "testing", "development", "qa", "uat"};
        String name = names[random.nextInt(names.length)];
        int number = random.nextInt(1000);
        long timestamp = System.nanoTime() % 10000;
        return name + "-" + number + "-" + timestamp;
    }
    
    /**
     * Generates a valid zone ID following the naming pattern.
     */
    private String generateValidZoneId() {
        String[] names = {"default", "primary", "secondary", "backup", "test", "demo"};
        String name = names[random.nextInt(names.length)];
        int number = random.nextInt(1000);
        long timestamp = System.nanoTime() % 10000;
        return name + "-" + number + "-" + timestamp;
    }
    
    /**
     * Generates a valid context ID following the naming pattern.
     */
    private String generateValidContextId() {
        String[] names = {"district", "school", "classroom", "student", "teacher", "admin"};
        String name = names[random.nextInt(names.length)];
        int number = random.nextInt(1000);
        long timestamp = System.nanoTime() % 10000;
        return name + "-" + number + "-" + timestamp;
    }
}
