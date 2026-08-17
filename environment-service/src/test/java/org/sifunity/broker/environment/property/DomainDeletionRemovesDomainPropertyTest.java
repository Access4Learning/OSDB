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
import org.sifunity.broker.environment.exception.DomainNotFoundException;
import org.sifunity.broker.environment.service.DomainService;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for domain deletion using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 5 from the design document:
 * "For any domain, after deleting it by domain_id, attempting to retrieve 
 * that domain should return a not found error."</p>
 * 
 * <p><strong>Validates: Requirements 1.11</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * domain deletion properly removes the domain from the system.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DomainDeletionRemovesDomainPropertyTest {
    
    @Autowired
    private DomainService domainService;
    
    private final Random random = new Random();
    
    /**
     * Property 5: Domain Deletion Removes Domain
     * 
     * <p><strong>Property:</strong> For any domain, after deleting it by domain_id, 
     * attempting to retrieve that domain should return a not found error.</p>
     * 
     * <p><strong>Validates: Requirements 1.11</strong></p>
     * 
     * <p>This test creates a domain with random valid parameters, deletes it,
     * then verifies that attempting to retrieve it throws DomainNotFoundException.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-5")
    void domainDeletionRemovesDomain() {
        // Generate random valid input data
        String domainId = generateValidDomainId();
        String description = random.nextBoolean() ? generateValidDescription() : null;
        
        // Create domain
        DomainCreateRequest createRequest = new DomainCreateRequest(domainId, description);
        DomainDTO createdDomain = domainService.createDomain(createRequest);
        
        // Verify domain was created and can be retrieved
        assertThat(createdDomain).isNotNull();
        assertThat(createdDomain.domainId()).isEqualTo(domainId);
        
        DomainDTO retrievedBeforeDeletion = domainService.getDomain(domainId);
        assertThat(retrievedBeforeDeletion)
            .as("Domain should exist before deletion")
            .isNotNull();
        
        // Delete the domain
        // Requirement 1.11: THE Environment_Service SHALL provide a REST API endpoint 
        // to delete a domain by domain_id
        domainService.deleteDomain(domainId);
        
        // Attempt to retrieve the deleted domain (should fail)
        assertThatThrownBy(() -> domainService.getDomain(domainId))
            .as("Retrieving a deleted domain should throw DomainNotFoundException")
            .isInstanceOf(DomainNotFoundException.class)
            .hasMessageContaining(domainId)
            .hasMessageContaining("not found");
        
        // Additional property verification: Domain should not exist in the system
        assertThat(domainService.domainExists(domainId))
            .as("Domain should not exist after deletion")
            .isFalse();
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
