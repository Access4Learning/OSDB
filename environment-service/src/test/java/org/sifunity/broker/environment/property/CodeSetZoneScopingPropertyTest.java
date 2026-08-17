// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.service.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Property-based test for Property 7: Zone-scoping override.
 *
 * <p><strong>Property:</strong> For any set of code sets where some have
 * {@code zone = "environment-global"} (global) and some have a specific zone value,
 * and where some zone-specific code sets share the same {@code id} as global ones:
 * querying with a specific {@code zoneId} SHALL return a result set where (a) each
 * code set with a zone-specific entry for the requested zone is returned instead of
 * its global counterpart, (b) global code sets with no zone-specific override are
 * still returned, and (c) zone-specific code sets with no global counterpart are
 * also returned.</p>
 *
 * <p><strong>Validates: Requirements 1.3, 2.3, 7.1, 7.2, 7.3, 7.4, 7.6</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CodeSetZoneScopingPropertyTest {

    @Autowired
    private CodeSetsService codeSetsService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @MockBean
    private CodeSetEventPublisher codeSetEventPublisher;

    private final Random random = new Random();

    private String domainId;
    private String solutionId;

    @BeforeEach
    void setUp() {
        doNothing().when(codeSetEventPublisher)
                .publishCodeSetCreatedEvent(any(), anyString(), anyString());

        domainId = "cs-zs-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "cs-zs-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
    }

    /**
     * Property 7: Zone-scoping override logic.
     *
     * <p><strong>Validates: Requirements 1.3, 2.3, 7.1, 7.2, 7.3, 7.4, 7.6</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: codeset-registry, Property 7: Zone-scoping override")
    void zoneScopingOverrideLogic() {
        String specificZone = "zone-" + random.nextInt(10000);

        // Create global-only code sets (no zone override)
        int globalOnlyCount = 1 + random.nextInt(3);
        Set<String> globalOnlyIds = new HashSet<>();
        for (int i = 0; i < globalOnlyCount; i++) {
            String id = "global-only-" + i + "-" + random.nextInt(100000);
            globalOnlyIds.add(id);
            createCodeSet(id, "environment-global");
        }

        // Create code sets that exist in both global and zone-specific (override)
        int overrideCount = 1 + random.nextInt(3);
        Set<String> overrideIds = new HashSet<>();
        for (int i = 0; i < overrideCount; i++) {
            String id = "override-" + i + "-" + random.nextInt(100000);
            overrideIds.add(id);
            createCodeSet(id, "environment-global");
            createCodeSet(id, specificZone);
        }

        // Create zone-only code sets (no global counterpart)
        int zoneOnlyCount = 1 + random.nextInt(3);
        Set<String> zoneOnlyIds = new HashSet<>();
        for (int i = 0; i < zoneOnlyCount; i++) {
            String id = "zone-only-" + i + "-" + random.nextInt(100000);
            zoneOnlyIds.add(id);
            createCodeSet(id, specificZone);
        }

        // Query with specific zone
        List<CodeSetDTO> results = codeSetsService.listCodeSets(domainId, solutionId, specificZone);
        Map<String, CodeSetDTO> resultMap = results.stream()
                .collect(Collectors.toMap(CodeSetDTO::id, dto -> dto));

        // (a) Zone-specific entries replace global counterparts
        for (String id : overrideIds) {
            assertThat(resultMap).containsKey(id);
            assertThat(resultMap.get(id).zone())
                    .as("Override id '%s' should return zone-specific entry, not global", id)
                    .isEqualTo(specificZone);
        }

        // (b) Global code sets with no zone-specific override are still returned
        for (String id : globalOnlyIds) {
            assertThat(resultMap).containsKey(id);
            assertThat(resultMap.get(id).zone())
                    .as("Global-only id '%s' should still be returned", id)
                    .isEqualTo("environment-global");
        }

        // (c) Zone-specific code sets with no global counterpart are included
        for (String id : zoneOnlyIds) {
            assertThat(resultMap).containsKey(id);
            assertThat(resultMap.get(id).zone())
                    .as("Zone-only id '%s' should be included", id)
                    .isEqualTo(specificZone);
        }

        // Total count should be globalOnly + override + zoneOnly
        int expectedCount = globalOnlyCount + overrideCount + zoneOnlyCount;
        assertThat(results).hasSize(expectedCount);
    }

    private void createCodeSet(String id, String zone) {
        CodeSetCreateBody body = new CodeSetCreateBody(
                id, zone,
                "v2024." + random.nextInt(9),
                "2024-06-15T10:30:00Z",
                null, null
        );
        codeSetsService.createCodeSet(domainId, solutionId, body);
    }
}
