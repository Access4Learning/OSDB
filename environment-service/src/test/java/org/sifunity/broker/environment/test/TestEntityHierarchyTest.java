// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link TestEntityHierarchy} shared utility.
 *
 * <p>Validates: Requirements 2.1</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TestEntityHierarchyTest {

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Test
    void create_producesNonNullIds() {
        TestEntityHierarchy hierarchy = TestEntityHierarchy.create(domainService, solutionService);

        assertThat(hierarchy.getDomainId()).isNotNull().isNotBlank();
        assertThat(hierarchy.getSolutionId()).isNotNull().isNotBlank();
        assertThat(hierarchy.getSolutionUuid()).isNotNull();
        assertThat(hierarchy.getDomainUuid()).isNotNull();
    }

    @Test
    void resourceBasePath_returnsCorrectFormat() {
        TestEntityHierarchy hierarchy = TestEntityHierarchy.create(domainService, solutionService);

        String basePath = hierarchy.resourceBasePath();

        assertThat(basePath).isEqualTo("/" + hierarchy.getDomainId() + "/" + hierarchy.getSolutionId());
        assertThat(basePath).startsWith("/");
        assertThat(basePath).contains("/");
    }

    @Test
    void create_producesUniqueIdsAcrossMultipleCalls() {
        int iterations = 5;
        Set<String> domainIds = new HashSet<>();
        Set<String> solutionIds = new HashSet<>();

        for (int i = 0; i < iterations; i++) {
            TestEntityHierarchy hierarchy = TestEntityHierarchy.create(domainService, solutionService);
            domainIds.add(hierarchy.getDomainId());
            solutionIds.add(hierarchy.getSolutionId());
        }

        assertThat(domainIds).hasSize(iterations);
        assertThat(solutionIds).hasSize(iterations);
    }
}
