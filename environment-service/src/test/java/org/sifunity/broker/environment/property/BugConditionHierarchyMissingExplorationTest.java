// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.exception.SolutionNotFoundException;
import org.sifunity.broker.environment.service.EnvironmentService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Bug Condition Exploration Test — Entity Hierarchy Missing Causes Test Failures.
 *
 * <p>This test demonstrates the bug condition: when a test calls
 * {@code environmentService.registerEnvironment()} with a {@code solutionId}
 * that has NO backing Domain → Solution hierarchy in the database, the service
 * throws {@link SolutionNotFoundException}.</p>
 *
 * <p>After the UUID FK migration (V21), the service layer resolves the string
 * {@code solutionId} to a Solution UUID. If no Solution entity exists, the
 * resolution fails with {@code SolutionNotFoundException}. This is the root
 * cause of ~40+ test failures: tests that were written before the migration
 * create environments without first setting up the required hierarchy.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.10</strong></p>
 *
 * <p><strong>EXPECTED OUTCOME on unfixed code:</strong> Test FAILS — this confirms
 * the bug exists. The test encodes the expected post-fix behavior and will pass
 * once the fix (TestEntityHierarchy) is applied.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("bugfix-exploration")
class BugConditionHierarchyMissingExplorationTest {

    @Autowired
    private EnvironmentService environmentService;

    /**
     * Bug Condition: Registering an environment with a solutionId that has no
     * backing Solution entity in the database should throw SolutionNotFoundException.
     *
     * <p>This directly reproduces the failure pattern seen in ~40+ tests after
     * the UUID FK migration. Tests that skip Domain → Solution hierarchy creation
     * and pass an arbitrary solutionId string will get this exception.</p>
     *
     * <p><strong>Validates: Requirements 1.1, 1.10</strong></p>
     */
    @Test
    @DisplayName("registerEnvironment with non-existent solutionId throws SolutionNotFoundException")
    void registerEnvironmentWithoutHierarchy_throwsSolutionNotFoundException() {
        // Arrange: create a registration request with a solutionId that does NOT
        // correspond to any Solution entity in the database — no Domain or Solution
        // was created beforehand, mimicking the pre-migration test pattern.
        String nonExistentSolutionId = "nonexistent-solution-" + System.nanoTime();
        String nonExistentDomainId = "nonexistent-domain-" + System.nanoTime();

        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("test-app-" + System.nanoTime(), EnvironmentType.DIRECT, new DefaultZoneDTO("default-zone"), nonExistentSolutionId, null));

        // Act & Assert: the service should throw SolutionNotFoundException because
        // the solutionId cannot be resolved to a Solution UUID — no Solution exists.
        assertThatThrownBy(() -> environmentService.registerEnvironment(nonExistentDomainId, request))
                .isInstanceOf(SolutionNotFoundException.class)
                .hasMessageContaining(nonExistentSolutionId);
    }
}
