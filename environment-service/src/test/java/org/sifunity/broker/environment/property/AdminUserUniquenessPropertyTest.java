// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.dto.AdminUserCreateRequest;
import org.sifunity.broker.environment.dto.AdminUserDTO;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.DomainDTO;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.SolutionDTO;
import org.sifunity.broker.environment.exception.DuplicateAdminUserException;
import org.sifunity.broker.environment.service.AdminUserService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for username uniqueness within domain+solution scope.
 *
 * <p><strong>Feature: admin-user-management, Property 2: Username uniqueness within domain+solution scope</strong></p>
 *
 * <p>For any username and domain+solution pair, creating a second admin user with the
 * same username in the same domain+solution should be rejected, while creating a user
 * with the same username in a different domain+solution should succeed.</p>
 *
 * <p><strong>Validates: Requirements 1.2, 1.3</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminUserUniquenessPropertyTest {

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    private final Random random = new Random();

    /**
     * Property 2: Username uniqueness within domain+solution scope
     *
     * <p>Verifies that duplicate username in same domain+solution is rejected with
     * DuplicateAdminUserException, while the same username in a different domain+solution
     * succeeds.</p>
     *
     * <p><strong>Validates: Requirements 1.2, 1.3</strong></p>
     */
    @RepeatedTest(10)
    @Tag("admin-user-management-property-2")
    void duplicateUsernameInSameScopeRejected_differentScopeSucceeds() {
        // Generate a random valid username and password
        String username = generateValidUsername();
        String password = generateValidPassword();

        // Create domain A and solution A
        String domainIdA = generateValidDomainId("domA");
        DomainDTO domainA = domainService.createDomain(new DomainCreateRequest(domainIdA, "Domain A"));
        String solutionIdA = generateValidSolutionId("solA");
        SolutionDTO solutionA = solutionService.createSolution(new SolutionCreateRequest(domainIdA, solutionIdA, "Solution A"));

        // Create domain B and solution B (different scope)
        String domainIdB = generateValidDomainId("domB");
        DomainDTO domainB = domainService.createDomain(new DomainCreateRequest(domainIdB, "Domain B"));
        String solutionIdB = generateValidSolutionId("solB");
        SolutionDTO solutionB = solutionService.createSolution(new SolutionCreateRequest(domainIdB, solutionIdB, "Solution B"));

        // Step 1: Create user with username in domain+solution A → expect success
        AdminUserCreateRequest firstRequest = new AdminUserCreateRequest(
                username, password, domainA.domainId(), solutionA.solutionId());
        AdminUserDTO firstUser = adminUserService.createUser(firstRequest);

        assertThat(firstUser)
                .as("First user creation should succeed")
                .isNotNull();
        assertThat(firstUser.username())
                .as("Created user should have the requested username")
                .isEqualTo(username);

        // Step 2: Attempt to create another user with SAME username in SAME domain+solution A → expect rejection
        // Requirement 1.2: Enforce username uniqueness within domain+solution
        // Requirement 1.3: Reject with HTTP 409 Conflict
        AdminUserCreateRequest duplicateRequest = new AdminUserCreateRequest(
                username, generateValidPassword(), domainA.domainId(), solutionA.solutionId());

        assertThatThrownBy(() -> adminUserService.createUser(duplicateRequest))
                .as("Creating user with duplicate username in same domain+solution should throw DuplicateAdminUserException")
                .isInstanceOf(DuplicateAdminUserException.class)
                .hasMessageContaining("already exists");

        // Step 3: Create user with SAME username in DIFFERENT domain+solution B → expect success
        AdminUserCreateRequest differentScopeRequest = new AdminUserCreateRequest(
                username, password, domainB.domainId(), solutionB.solutionId());
        AdminUserDTO secondUser = adminUserService.createUser(differentScopeRequest);

        assertThat(secondUser)
                .as("User creation with same username in different domain+solution should succeed")
                .isNotNull();
        assertThat(secondUser.username())
                .as("Second user should have the same username")
                .isEqualTo(username);
        assertThat(secondUser.id())
                .as("Second user should have a different UUID than the first")
                .isNotEqualTo(firstUser.id());

        // Verify original user is unchanged after duplicate attempt and cross-scope creation
        AdminUserDTO retrievedFirst = adminUserService.getUserById(firstUser.id());
        assertThat(retrievedFirst.username())
                .as("Original user should remain unchanged")
                .isEqualTo(username);
        assertThat(retrievedFirst.domainUuid())
                .as("Original user domain should remain unchanged")
                .isEqualTo(domainA.id());
        assertThat(retrievedFirst.solutionUuid())
                .as("Original user solution should remain unchanged")
                .isEqualTo(solutionA.id());
    }

    // ========== Test Data Generators ==========

    /**
     * Generates a valid username matching pattern ^[a-zA-Z0-9_-]{3,128}$.
     */
    private String generateValidUsername() {
        String[] prefixes = {"admin", "user", "operator", "manager", "tech", "support", "staff", "lead"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        int number = random.nextInt(10000);
        long timestamp = System.nanoTime() % 100000;
        String separator = random.nextBoolean() ? "-" : "_";
        return prefix + separator + number + separator + timestamp;
    }

    /**
     * Generates a valid password (8-72 characters).
     */
    private String generateValidPassword() {
        int length = 8 + random.nextInt(20);
        StringBuilder sb = new StringBuilder(length);
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%";
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Generates a valid domain ID with a prefix for uniqueness across iterations.
     */
    private String generateValidDomainId(String prefix) {
        int number = random.nextInt(10000);
        long timestamp = System.nanoTime() % 100000;
        return prefix + "-" + number + "-" + timestamp;
    }

    /**
     * Generates a valid solution ID with a prefix for uniqueness across iterations.
     */
    private String generateValidSolutionId(String prefix) {
        int number = random.nextInt(10000);
        long timestamp = System.nanoTime() % 100000;
        return prefix + "-" + number + "-" + timestamp;
    }
}
