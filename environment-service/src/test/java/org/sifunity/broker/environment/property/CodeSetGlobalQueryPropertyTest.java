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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Property-based test for Property 8: Environment-global query returns all.
 *
 * <p><strong>Property:</strong> For any set of code sets across multiple zones,
 * querying with {@code zoneId = "environment-global"} SHALL return ALL code sets
 * from ALL zones without applying zone-scoping override logic.</p>
 *
 * <p><strong>Validates: Requirements 1.2, 7.5</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CodeSetGlobalQueryPropertyTest {

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

        domainId = "cs-gq-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "cs-gq-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
    }

    /**
     * Property 8: Environment-global query returns all code sets from all zones.
     *
     * <p><strong>Validates: Requirements 1.2, 7.5</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: codeset-registry, Property 8: Environment-global query returns all")
    void environmentGlobalQueryReturnsAll() {
        // Create code sets across multiple zones
        int zoneCount = 2 + random.nextInt(3); // 2-4 zones
        List<String> zones = new ArrayList<>();
        zones.add("environment-global");
        for (int i = 0; i < zoneCount - 1; i++) {
            zones.add("zone-" + i + "-" + random.nextInt(10000));
        }

        int totalCreated = 0;
        Map<String, List<String>> codeSetIdsByZone = new HashMap<>();

        for (String zone : zones) {
            int count = 1 + random.nextInt(3); // 1-3 per zone
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String id = "gq-" + zone.substring(0, Math.min(zone.length(), 8)) + "-" + i + "-" + random.nextInt(100000);
                createCodeSet(id, zone);
                ids.add(id);
                totalCreated++;
            }
            codeSetIdsByZone.put(zone, ids);
        }

        // Query with environment-global — should return ALL
        List<CodeSetDTO> results = codeSetsService.listCodeSets(
                domainId, solutionId, "environment-global");

        assertThat(results)
                .as("environment-global query should return ALL code sets from ALL zones")
                .hasSize(totalCreated);

        // Verify every created code set is present
        Set<String> resultIds = new HashSet<>();
        for (CodeSetDTO dto : results) {
            resultIds.add(dto.id());
        }

        for (Map.Entry<String, List<String>> entry : codeSetIdsByZone.entrySet()) {
            for (String id : entry.getValue()) {
                assertThat(resultIds)
                        .as("Code set '%s' from zone '%s' should be in results", id, entry.getKey())
                        .contains(id);
            }
        }
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
