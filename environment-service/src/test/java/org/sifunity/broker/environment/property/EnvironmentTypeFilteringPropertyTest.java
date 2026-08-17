// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.dto.SifEnvironmentResponse;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Property-based test for environment type filtering using JUnit 5 RepeatedTest.
 *
 * <p>This test validates Property 6 from the design document:
 * "For any environment type filter, querying environments should return only
 * environments matching that type."</p>
 *
 * <p>Uses the actual EnvironmentType enum values: DIRECT and BROKERED.
 * ADMIN is excluded because it is a privileged type created by bootstrap.</p>
 *
 * <p><strong>Validates: Requirements 2.5</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EnvironmentTypeFilteringPropertyTest {

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    private final Random random = new Random();

    @AfterEach
    void cleanupTestData() {
        environmentRepository.findAll().stream()
            .filter(env -> !("ADMIN".equals(env.getApplicationName())))
            .forEach(env -> {
                try {
                    environmentRepository.delete(env);
                } catch (Exception e) {
                    // Ignore deletion errors
                }
            });
        environmentRepository.flush();
    }

    /**
     * Property 6: Environment type filtering
     *
     * <p>Creates random numbers of DIRECT and BROKERED environments, then verifies
     * that filtering by each type returns only environments of that type, and that
     * no-filter returns all environments.</p>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-6")
    void environmentTypeFiltering() {
        // Create Domain → Solution hierarchy so solutionId resolves to a valid UUID
        TestEntityHierarchy hierarchy = TestEntityHierarchy.create(domainService, solutionService);

        int directCount = random.nextInt(6);   // 0-5
        int brokeredCount = random.nextInt(6); // 0-5

        List<SifEnvironmentRegistrationResponse> allEnvironments = new ArrayList<>();
        List<SifEnvironmentRegistrationResponse> directEnvironments = new ArrayList<>();
        List<SifEnvironmentRegistrationResponse> brokeredEnvironments = new ArrayList<>();

        // Create DIRECT environments
        for (int i = 0; i < directCount; i++) {
            SifEnvironmentRegistrationResponse response = createEnvironment(EnvironmentType.DIRECT, i, hierarchy.getDomainId(), hierarchy.getSolutionId());
            allEnvironments.add(response);
            directEnvironments.add(response);
        }

        // Create BROKERED environments
        for (int i = 0; i < brokeredCount; i++) {
            SifEnvironmentRegistrationResponse response = createEnvironment(EnvironmentType.BROKERED, i, hierarchy.getDomainId(), hierarchy.getSolutionId());
            allEnvironments.add(response);
            brokeredEnvironments.add(response);
        }

        // Test filtering by each type
        testTypeFilter(EnvironmentType.DIRECT, directEnvironments);
        testTypeFilter(EnvironmentType.BROKERED, brokeredEnvironments);

        // Test no filtering (null type) - should return all environments
        testNoFilter(allEnvironments);

        // Test empty type filter when count is 0
        if (directCount == 0) {
            testEmptyTypeFilter(EnvironmentType.DIRECT);
        }
        if (brokeredCount == 0) {
            testEmptyTypeFilter(EnvironmentType.BROKERED);
        }
    }

    private void testTypeFilter(EnvironmentType type, List<SifEnvironmentRegistrationResponse> expectedEnvironments) {
        Pageable pageable = PageRequest.of(0, 100);
        Page<SifEnvironmentResponse> result = environmentService.listEnvironments(type, pageable);

        // Filter out bootstrap admin and broker-services environments (not created by this test)
        List<SifEnvironmentResponse> filteredResults = result.getContent().stream()
            .filter(env -> env.consumerName() != null
                && !"ADMIN".equals(env.consumerName())
                && !"INTERNAL_CONSUMER_SERVICES".equals(env.consumerName())
                && !"broker-services".equals(env.fingerprint()))
            .toList();

        for (SifEnvironmentResponse env : filteredResults) {
            assertThat(env.type())
                .as("Environment %s should have type %s", env.consumerName(), type)
                .isEqualTo(type);
        }

        assertThat(filteredResults)
            .as("Should return exactly %d environments when filtering by %s",
                expectedEnvironments.size(), type)
            .hasSize(expectedEnvironments.size());

        List<String> expectedAppNames = expectedEnvironments.stream()
            .map(r -> r.environment().consumerName())
            .toList();

        List<String> actualAppNames = filteredResults.stream()
            .map(SifEnvironmentResponse::consumerName)
            .toList();

        assertThat(actualAppNames)
            .as("Should contain all expected %s environments", type)
            .containsExactlyInAnyOrderElementsOf(expectedAppNames);
    }

    private void testNoFilter(List<SifEnvironmentRegistrationResponse> allEnvironments) {
        Pageable pageable = PageRequest.of(0, 100);
        Page<SifEnvironmentResponse> result = environmentService.listEnvironments(null, pageable);

        // Filter out bootstrap admin and broker-services environments (not created by this test)
        List<SifEnvironmentResponse> nonAdminResults = result.getContent().stream()
            .filter(env -> env.consumerName() != null
                && !"ADMIN".equals(env.consumerName())
                && !"INTERNAL_CONSUMER_SERVICES".equals(env.consumerName())
                && !"broker-services".equals(env.fingerprint()))
            .toList();

        assertThat(nonAdminResults)
            .as("Should return all %d environments when not filtering by type",
                allEnvironments.size())
            .hasSize(allEnvironments.size());

        List<String> expectedAppNames = allEnvironments.stream()
            .map(r -> r.environment().consumerName())
            .toList();

        List<String> actualAppNames = nonAdminResults.stream()
            .map(SifEnvironmentResponse::consumerName)
            .toList();

        assertThat(actualAppNames)
            .as("Should contain all created environments when not filtering")
            .containsExactlyInAnyOrderElementsOf(expectedAppNames);
    }

    private void testEmptyTypeFilter(EnvironmentType type) {
        Pageable pageable = PageRequest.of(0, 100);
        Page<SifEnvironmentResponse> result = environmentService.listEnvironments(type, pageable);

        // Filter out bootstrap admin and broker-services environments (not created by this test)
        List<SifEnvironmentResponse> filteredResults = result.getContent().stream()
            .filter(env -> env.consumerName() != null
                && !"ADMIN".equals(env.consumerName())
                && !"INTERNAL_CONSUMER_SERVICES".equals(env.consumerName())
                && !"broker-services".equals(env.fingerprint()))
            .toList();

        assertThat(filteredResults)
            .as("Should return empty results when filtering by %s with no environments of that type", type)
            .isEmpty();
    }

    private SifEnvironmentRegistrationResponse createEnvironment(EnvironmentType type, int index, String domainId, String solutionId) {
        String applicationName = generateUniqueApplicationName(type, index);
        // Use null for defaultZoneId since zone validation is not what this test covers
        String defaultZoneId = null;
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, type, DefaultZoneDTO.of(defaultZoneId), solutionId, null));

        return environmentService.registerEnvironment(domainId, request);
    }

    private String generateUniqueApplicationName(EnvironmentType type, int index) {
        String[] prefixes = {"App", "Service", "System", "Platform", "Tool", "Client", "Server"};
        String[] suffixes = {"Pro", "Lite", "Plus", "Enterprise", "Basic", "Advanced", "Standard"};

        String prefix = prefixes[random.nextInt(prefixes.length)];
        String suffix = suffixes[random.nextInt(suffixes.length)];
        long timestamp = System.nanoTime();

        return prefix + "-" + type.name() + "-" + suffix + "-" + index + "-" + timestamp;
    }

    private String generateValidZoneId() {
        String[] zones = {"production", "staging", "development", "test", "demo", "sandbox"};
        String base = zones[random.nextInt(zones.length)];
        int number = random.nextInt(100);
        return base + "-" + number;
    }

    private String generateValidContextId() {
        String[] contexts = {"default", "primary", "secondary", "backup", "main", "alt"};
        String base = contexts[random.nextInt(contexts.length)];
        int number = random.nextInt(50);
        return base + "-" + number;
    }
}


