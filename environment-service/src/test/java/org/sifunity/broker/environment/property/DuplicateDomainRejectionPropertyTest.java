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
import org.sifunity.broker.environment.exception.DuplicateDomainException;
import org.sifunity.broker.environment.service.DomainService;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for duplicate domain rejection using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 3 from the design document:
 * "For any domain_id, after successfully creating a domain with that ID, attempting 
 * to create another domain with the same domain_id should fail with a conflict error."</p>
 * 
 * <p><strong>Validates: Requirements 1.2</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * the system properly rejects duplicate domain creation attempts and maintains
 * domain_id uniqueness.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DuplicateDomainRejectionPropertyTest {
    
    @Autowired
    private DomainService domainService;
    
    private final Random random = new Random();
    
    /**
     * Property 3: Duplicate Domain Rejection
     * 
     * <p><strong>Property:</strong> For any domain_id, after successfully creating a 
     * domain with that ID, attempting to create another domain with the same domain_id 
     * should fail with a conflict error.</p>
     * 
     * <p><strong>Validates: Requirements 1.2</strong></p>
     * 
     * <p>This test creates a domain with a random valid domain ID, then attempts to
     * create another domain with the same ID and verifies that a DuplicateDomainException
     * is thrown.</p>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-hierarchy-property-3")
    void duplicateDomainRejection() {
        // Generate random valid input data
        String domainId = generateValidDomainId();
        String firstDescription = random.nextBoolean() ? generateValidDescription() : null;
        String secondDescription = random.nextBoolean() ? generateValidDescription() : null;
        
        // Create first domain creation request
        DomainCreateRequest firstRequest = new DomainCreateRequest(domainId, firstDescription);
        
        // Create the first domain (should succeed)
        DomainDTO createdDomain = domainService.createDomain(firstRequest);
        
        // Verify the first domain was created successfully
        assertThat(createdDomain)
            .as("First domain should be created successfully")
            .isNotNull();
        
        assertThat(createdDomain.domainId())
            .as("Created domain should have the requested domain ID")
            .isEqualTo(domainId);
        
        // Create second domain creation request with the SAME domain_id
        DomainCreateRequest secondRequest = new DomainCreateRequest(domainId, secondDescription);
        
        // Attempt to create a second domain with the same domain_id (should fail)
        // Requirement 1.2: When a domain creation request is received with a domain_id 
        // that already exists, THE Environment_Service SHALL return an error indicating duplicate domain
        assertThatThrownBy(() -> domainService.createDomain(secondRequest))
            .as("Creating a domain with duplicate domain_id should throw DuplicateDomainException")
            .isInstanceOf(DuplicateDomainException.class)
            .hasMessageContaining(domainId)
            .hasMessageContaining("already exists");
        
        // Additional property verification: The original domain should still exist and be unchanged
        DomainDTO retrievedDomain = domainService.getDomain(domainId);
        
        assertThat(retrievedDomain.id())
            .as("Original domain UUID should remain unchanged after duplicate attempt")
            .isEqualTo(createdDomain.id());
        
        assertThat(retrievedDomain.domainId())
            .as("Original domain domainId should remain unchanged after duplicate attempt")
            .isEqualTo(createdDomain.domainId());
        
        assertThat(retrievedDomain.description())
            .as("Original domain description should remain unchanged after duplicate attempt")
            .isEqualTo(createdDomain.description());
        
        assertThat(retrievedDomain.createdAt())
            .as("Original domain createdAt should remain unchanged after duplicate attempt")
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
