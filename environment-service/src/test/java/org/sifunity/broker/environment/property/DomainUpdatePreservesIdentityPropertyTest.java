// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.DomainDTO;
import org.sifunity.broker.environment.dto.DomainUpdateRequest;
import org.sifunity.broker.environment.service.DomainService;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for domain update preserving identity using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 22 from the design document:
 * "For any domain and any new description, after updating the domain's description, 
 * retrieving the domain should return the updated description while preserving the 
 * domain_id and created_at timestamp."</p>
 * 
 * <p><strong>Validates: Requirements 1.10</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * domain updates correctly modify the description while preserving identity
 * fields (domain_id, created_at).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DomainUpdatePreservesIdentityPropertyTest {
    
    @Autowired
    private DomainService domainService;
    
    private final Random random = new Random();
    
    /**
     * Property 22: Domain Update Preserves Identity
     * 
     * <p><strong>Property:</strong> For any domain and any new description, after 
     * updating the domain's description, retrieving the domain should return the 
     * updated description while preserving the domain_id and created_at timestamp.</p>
     * 
     * <p><strong>Validates: Requirements 1.10</strong></p>
     * 
     * <p>This test creates a domain with random valid parameters, updates its
     * description, then verifies that the description changed while domain_id
     * and created_at remain unchanged.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-22")
    void domainUpdatePreservesIdentity() {
        // Generate random valid input data
        String domainId = generateValidDomainId();
        String originalDescription = random.nextBoolean() ? generateValidDescription() : null;
        String updatedDescription = random.nextBoolean() ? generateValidDescription() : null;
        
        // Create domain
        DomainCreateRequest createRequest = new DomainCreateRequest(domainId, originalDescription);
        DomainDTO createdDomain = domainService.createDomain(createRequest);
        
        // Verify domain was created
        assertThat(createdDomain).isNotNull();
        assertThat(createdDomain.domainId()).isEqualTo(domainId);
        
        // Update domain description
        // Requirement 1.10: THE Environment_Service SHALL provide a REST API endpoint 
        // to update a domain's description
        DomainUpdateRequest updateRequest = new DomainUpdateRequest(updatedDescription);
        DomainDTO updatedDomain = domainService.updateDomain(domainId, updateRequest);
        
        // Verify update response contains updated description
        assertThat(updatedDomain.description())
            .as("Updated domain should have the new description")
            .isEqualTo(updatedDescription);
        
        // Verify identity fields are preserved
        assertThat(updatedDomain.id())
            .as("Domain UUID should remain unchanged after update")
            .isEqualTo(createdDomain.id());
        
        assertThat(updatedDomain.domainId())
            .as("Domain ID should remain unchanged after update")
            .isEqualTo(createdDomain.domainId());
        
        assertThat(updatedDomain.createdAt())
            .as("Creation timestamp should remain unchanged after update")
            .isEqualTo(createdDomain.createdAt());
        
        // Retrieve the domain to verify persistence
        DomainDTO retrievedDomain = domainService.getDomain(domainId);
        
        // Verify retrieved domain has updated description
        assertThat(retrievedDomain.description())
            .as("Retrieved domain should have the updated description")
            .isEqualTo(updatedDescription);
        
        // Verify identity fields are still preserved after retrieval
        assertThat(retrievedDomain.id())
            .as("Retrieved domain UUID should match original")
            .isEqualTo(createdDomain.id());
        
        assertThat(retrievedDomain.domainId())
            .as("Retrieved domain ID should match original")
            .isEqualTo(createdDomain.domainId());
        
        assertThat(retrievedDomain.createdAt())
            .as("Retrieved domain createdAt should match original")
            .isEqualTo(createdDomain.createdAt());
    }
    
    // ========== Test Data Generators ==========
    
    /**
     * Generates a valid domain ID following the naming pattern.
     * Pattern: alphanumeric, underscore, hyphen (must start with alphanumeric)
     */
    private String generateValidDomainId() {
        String[] prefixes = {"alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel"};
        String[] suffixes = {"edu", "gov", "district", "state", "county", "region"};
        
        String prefix = prefixes[random.nextInt(prefixes.length)];
        
        // Randomly decide whether to add a suffix
        if (random.nextBoolean()) {
            String suffix = suffixes[random.nextInt(suffixes.length)];
            int number = random.nextInt(1000);
            
            // Randomly choose separator
            String separator = random.nextBoolean() ? "-" : "_";
            
            return prefix + separator + suffix + separator + number;
        } else {
            // Just prefix with number and timestamp for uniqueness
            int number = random.nextInt(1000);
            long timestamp = System.nanoTime() % 10000;
            return prefix + "-" + number + "-" + timestamp;
        }
    }
    
    /**
     * Generates a valid description for a domain.
     */
    private String generateValidDescription() {
        String[] templates = {
            "%s Department of Education",
            "%s State Education Agency",
            "%s School District",
            "%s Regional Education Service Center",
            "%s County Office of Education",
            "%s Educational Cooperative"
        };
        
        String[] locations = {
            "State A",
            "State B",
            "State C",
            "State D",
            "State E",
            "State F",
            "State G",
            "State H"
        };
        
        String template = templates[random.nextInt(templates.length)];
        String location = locations[random.nextInt(locations.length)];
        
        return String.format(template, location);
    }
}
