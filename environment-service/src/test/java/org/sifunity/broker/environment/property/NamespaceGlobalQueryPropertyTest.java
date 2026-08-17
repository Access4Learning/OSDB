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
 * Property-based test for Property 7: Environment-global query returns all.
 *
 * <p><strong>Property:</strong> For any set of namespaces across multiple zones,
 * querying with {@code zoneId = "environment-global"} SHALL return ALL namespaces
 * from ALL zones without applying zone-scoping override logic.</p>
 *
 * <p><strong>Validates: Requirements 1.2, 7.5</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NamespaceGlobalQueryPropertyTest {

    @Autowired
    private NamespacesService namespacesService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @MockBean
    private NamespaceEventPublisher namespaceEventPublisher;

    private final Random random = new Random();

    private String domainId;
    private String solutionId;

    @BeforeEach
    void setUp() {
        doNothing().when(namespaceEventPublisher)
                .publishNamespaceCreatedEvent(any(), anyString(), anyString());

        domainId = "ns-gq-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "ns-gq-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
    }

    /**
     * Property 7: Environment-global query returns all namespaces from all zones.
     *
     * <p><strong>Validates: Requirements 1.2, 7.5</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: namespace-registry, Property 7: Environment-global query returns all")
    void environmentGlobalQueryReturnsAll() {
        int zoneCount = 2 + random.nextInt(3); // 2-4 zones
        List<String> zones = new ArrayList<>();
        zones.add("environment-global");
        for (int i = 0; i < zoneCount - 1; i++) {
            zones.add("zone-" + i + "-" + random.nextInt(10000));
        }

        int totalCreated = 0;
        Map<String, List<String>> urisByZone = new HashMap<>();

        for (String zone : zones) {
            int count = 1 + random.nextInt(3);
            List<String> uris = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String uri = "http://gq-" + zone.substring(0, Math.min(zone.length(), 8))
                        + "-" + i + "-" + random.nextInt(100000);
                createNamespace(uri, zone);
                uris.add(uri);
                totalCreated++;
            }
            urisByZone.put(zone, uris);
        }

        // Query with environment-global — should return ALL
        List<NamespaceDTO> results = namespacesService.listNamespaces(
                domainId, solutionId, "environment-global");

        assertThat(results)
                .as("environment-global query should return ALL namespaces from ALL zones")
                .hasSize(totalCreated);

        Set<String> resultUris = new HashSet<>();
        for (NamespaceDTO dto : results) {
            resultUris.add(dto.uri());
        }

        for (Map.Entry<String, List<String>> entry : urisByZone.entrySet()) {
            for (String uri : entry.getValue()) {
                assertThat(resultUris)
                        .as("Namespace '%s' from zone '%s' should be in results", uri, entry.getKey())
                        .contains(uri);
            }
        }
    }

    private void createNamespace(String uri, String zone) {
        NamespaceCreateBody body = new NamespaceCreateBody(
                null, zone, uri,
                "https://schemas.example.org/" + random.nextInt(100000) + "/schema.xsd"
        );
        namespacesService.createNamespace(domainId, solutionId, body);
    }
}
