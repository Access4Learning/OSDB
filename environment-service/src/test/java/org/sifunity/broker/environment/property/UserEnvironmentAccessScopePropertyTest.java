// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.exception.DomainSolutionMismatchException;
import org.sifunity.broker.environment.service.AdminUserService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.UserEnvironmentAccessService;

import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based test for domain+solution scope enforcement on user-environment associations.
 *
 * <p>This test validates Property 4 from the design document:
 * "For any admin user scoped to (domainUuid_A, solutionUuid_A) and any environment
 * scoped to solutionUuid_B, granting access should succeed if and only if
 * solutionUuid_A == solutionUuid_B and the solution's domain matches domainUuid_A."</p>
 *
 * <p><strong>Validates: Requirements 2.3, 2.4</strong></p>
 *
 * <p>This test runs 10 iterations with randomized data to verify that granting
 * environment access enforces domain+solution scope matching.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("Feature: admin-user-management, Property 4: Domain+solution scope enforcement for associations")
class UserEnvironmentAccessScopePropertyTest {

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private UserEnvironmentAccessService accessService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    private final Random random = new Random();

    /**
     * Property 4: Domain+solution scope enforcement for associations
     *
     * <p><strong>Property:</strong> For any admin user scoped to (domainUuid_A, solutionUuid_A)
     * and any environment scoped to solutionUuid_B, granting access should succeed if and only if
     * solutionUuid_A == solutionUuid_B and the solution's domain matches domainUuid_A.</p>
     *
     * <p><strong>Validates: Requirements 2.3, 2.4</strong></p>
     *
     * <p>This test:
     * <ul>
     *   <li>Creates a domain with two solutions (A and B)</li>
     *   <li>Creates an admin user scoped to domain + solution A</li>
     *   <li>Creates environments in solution A and solution B</li>
     *   <li>Verifies granting access to environment in solution A succeeds</li>
     *   <li>Verifies granting access to environment in solution B throws DomainSolutionMismatchException</li>
     * </ul>
     */
    @RepeatedTest(10)
    @Tag("domain-solution-scope-enforcement")
    void grantAccessSucceedsOnlyWhenEnvironmentMatchesUserScope() {
        // Create a domain
        String domainId = generateValidDomainId();
        DomainCreateRequest domainRequest = new DomainCreateRequest(domainId, "Test domain for scope enforcement");
        DomainDTO domain = domainService.createDomain(domainRequest);

        // Create solution A within the domain
        String solutionIdA = generateValidSolutionId();
        SolutionCreateRequest solutionRequestA = new SolutionCreateRequest(domainId, solutionIdA, "Solution A");
        SolutionDTO solutionA = solutionService.createSolution(solutionRequestA);

        // Create solution B within the same domain
        String solutionIdB = generateValidSolutionId();
        SolutionCreateRequest solutionRequestB = new SolutionCreateRequest(domainId, solutionIdB, "Solution B");
        SolutionDTO solutionB = solutionService.createSolution(solutionRequestB);

        // Create an admin user scoped to domain + solution A
        String username = generateValidUsername();
        AdminUserCreateRequest userRequest = new AdminUserCreateRequest(
                username,
                generateValidPassword(),
                domain.domainId(),
                solutionA.solutionId()
        );
        AdminUserDTO adminUser = adminUserService.createUser(userRequest);

        // Create an environment in solution A
        String appNameA = generateUniqueApplicationName();
        EnvironmentRegistrationRequest envRequestA = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appNameA, generateRandomEnvironmentType(), null, solutionIdA, null));
        SifEnvironmentRegistrationResponse envResponseA = environmentService.registerEnvironment(domainId, envRequestA);
        UUID environmentIdA = envResponseA.environment().id();

        // Create an environment in solution B
        String appNameB = generateUniqueApplicationName();
        EnvironmentRegistrationRequest envRequestB = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(appNameB, generateRandomEnvironmentType(), null, solutionIdB, null));
        SifEnvironmentRegistrationResponse envResponseB = environmentService.registerEnvironment(domainId, envRequestB);
        UUID environmentIdB = envResponseB.environment().id();

        // Requirement 2.3: Granting access to environment in solution A should SUCCEED
        UserEnvironmentAccessRequest accessRequestA = new UserEnvironmentAccessRequest(environmentIdA);
        UserEnvironmentAccessDTO accessResult = accessService.grantAccess(adminUser.id(), accessRequestA);

        assertThat(accessResult)
                .as("Granting access to environment in same solution should succeed")
                .isNotNull();
        assertThat(accessResult.userId()).isEqualTo(adminUser.id());
        assertThat(accessResult.environmentId()).isEqualTo(environmentIdA);
        assertThat(accessResult.grantedAt()).isNotNull();

        // Requirement 2.4: Granting access to environment in solution B should FAIL
        UserEnvironmentAccessRequest accessRequestB = new UserEnvironmentAccessRequest(environmentIdB);
        assertThatThrownBy(() -> accessService.grantAccess(adminUser.id(), accessRequestB))
                .as("Granting access to environment in different solution should throw DomainSolutionMismatchException")
                .isInstanceOf(DomainSolutionMismatchException.class);
    }

    // ========== Test Data Generators ==========

    /**
     * Generates a valid domain ID following the naming pattern.
     */
    private String generateValidDomainId() {
        String[] prefixes = {"domain", "tenant", "org", "district"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        long timestamp = System.nanoTime() % 100000;
        int randomValue = random.nextInt(1000);
        return prefix + "-" + timestamp + "-" + randomValue;
    }

    /**
     * Generates a valid solution ID following the naming pattern.
     */
    private String generateValidSolutionId() {
        String[] prefixes = {"solution", "env", "deployment", "instance"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        long timestamp = System.nanoTime() % 100000;
        int randomValue = random.nextInt(1000);
        return prefix + "-" + timestamp + "-" + randomValue;
    }

    /**
     * Generates a valid username matching ^[a-zA-Z0-9_-]{3,128}$.
     */
    private String generateValidUsername() {
        String[] prefixes = {"admin", "user", "operator", "manager"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        long timestamp = System.nanoTime() % 100000;
        int randomValue = random.nextInt(1000);
        return prefix + "-" + timestamp + "-" + randomValue;
    }

    /**
     * Generates a valid password (8-72 chars).
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
     * Generates a unique application name.
     */
    private String generateUniqueApplicationName() {
        String[] prefixes = {"app", "service", "system", "platform"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        long timestamp = System.nanoTime();
        int randomValue = random.nextInt(10000);
        return prefix + "-" + timestamp + "-" + randomValue;
    }

    /**
     * Generates a random environment type.
     */
    private EnvironmentType generateRandomEnvironmentType() {
        EnvironmentType[] types = EnvironmentType.values();
        return types[random.nextInt(types.length)];
    }

    /**
     * Generates a valid zone ID.
     */
    private String generateValidZoneId() {
        String[] prefixes = {"zone", "area", "region", "sector"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        int number = random.nextInt(1000);
        return prefix + "-" + number;
    }
}
