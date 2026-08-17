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
 * Property-based test for Property 6: Zone-scoping override (by URI).
 *
 * <p><strong>Property:</strong> For any set of namespaces where some have
 * {@code zone = "environment-global"} (global) and some have a specific zone value,
 * and where some zone-specific namespaces share the same {@code uri} as global ones:
 * querying with a specific {@code zoneId} SHALL return a result set where (a) each
 * namespace with a zone-specific entry for the requested zone is returned instead of
 * its global counterpart (matched by {@code uri}), (b) global namespaces with no
 * zone-specific override are still returned, and (c) zone-specific namespaces with
 * no global counterpart are also returned.</p>
 *
 * <p><strong>Validates: Requirements 1.3, 7.1, 7.2, 7.3, 7.4, 7.6</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NamespaceZoneScopingPropertyTest {

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

        domainId = "ns-zs-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "ns-zs-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
    }

    /**
     * Property 6: Zone-scoping override logic (by URI).
     *
     * <p><strong>Validates: Requirements 1.3, 7.1, 7.2, 7.3, 7.4, 7.6</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: namespace-registry, Property 6: Zone-scoping override")
    void zoneScopingOverrideLogic() {
        String specificZone = "zone-" + random.nextInt(10000);

        // Create global-only namespaces (no zone override)
        int globalOnlyCount = 1 + random.nextInt(3);
        Set<String> globalOnlyUris = new HashSet<>();
        for (int i = 0; i < globalOnlyCount; i++) {
            String uri = "http://global-only-" + i + "-" + random.nextInt(100000);
            globalOnlyUris.add(uri);
            createNamespace(uri, "environment-global");
        }

        // Create namespaces that exist in both global and zone-specific (override by URI)
        int overrideCount = 1 + random.nextInt(3);
        Set<String> overrideUris = new HashSet<>();
        for (int i = 0; i < overrideCount; i++) {
            String uri = "http://override-" + i + "-" + random.nextInt(100000);
            overrideUris.add(uri);
            createNamespace(uri, "environment-global");
            createNamespace(uri, specificZone);
        }

        // Create zone-only namespaces (no global counterpart)
        int zoneOnlyCount = 1 + random.nextInt(3);
        Set<String> zoneOnlyUris = new HashSet<>();
        for (int i = 0; i < zoneOnlyCount; i++) {
            String uri = "http://zone-only-" + i + "-" + random.nextInt(100000);
            zoneOnlyUris.add(uri);
            createNamespace(uri, specificZone);
        }

        // Query with specific zone
        List<NamespaceDTO> results = namespacesService.listNamespaces(domainId, solutionId, specificZone);
        Map<String, NamespaceDTO> resultMap = results.stream()
                .collect(Collectors.toMap(NamespaceDTO::uri, dto -> dto));

        // (a) Zone-specific entries replace global counterparts (matched by URI)
        for (String uri : overrideUris) {
            assertThat(resultMap).containsKey(uri);
            assertThat(resultMap.get(uri).zone())
                    .as("Override uri '%s' should return zone-specific entry, not global", uri)
                    .isEqualTo(specificZone);
        }

        // (b) Global namespaces with no zone-specific override are still returned
        for (String uri : globalOnlyUris) {
            assertThat(resultMap).containsKey(uri);
            assertThat(resultMap.get(uri).zone())
                    .as("Global-only uri '%s' should still be returned", uri)
                    .isEqualTo("environment-global");
        }

        // (c) Zone-specific namespaces with no global counterpart are included
        for (String uri : zoneOnlyUris) {
            assertThat(resultMap).containsKey(uri);
            assertThat(resultMap.get(uri).zone())
                    .as("Zone-only uri '%s' should be included", uri)
                    .isEqualTo(specificZone);
        }

        // Total count
        int expectedCount = globalOnlyCount + overrideCount + zoneOnlyCount;
        assertThat(results).hasSize(expectedCount);
    }

    private void createNamespace(String uri, String zone) {
        NamespaceCreateBody body = new NamespaceCreateBody(
                null, zone, uri,
                "https://schemas.example.org/" + random.nextInt(100000) + "/schema.xsd"
        );
        namespacesService.createNamespace(domainId, solutionId, body);
    }
}
