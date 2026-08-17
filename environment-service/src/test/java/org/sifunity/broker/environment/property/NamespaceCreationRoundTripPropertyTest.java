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

import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Property-based test for Property 1: Namespace creation round-trip.
 *
 * <p><strong>Property:</strong> For any valid namespace creation request (with valid
 * {@code zone}, {@code uri}, {@code url}), creating the namespace via the service
 * then retrieving it by the returned broker-assigned UUID {@code id} SHALL produce
 * a response where {@code zone}, {@code uri}, and {@code url} match the original
 * request fields, and the {@code id} is a valid UUID assigned by the broker.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 2.1, 8.1, 8.6</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NamespaceCreationRoundTripPropertyTest {

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

        domainId = "ns-rt-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "ns-rt-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
    }

    /**
     * Property 1: Namespace creation round-trip preserves all fields.
     *
     * <p><strong>Validates: Requirements 3.1, 2.1, 8.1, 8.6</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: namespace-registry, Property 1: Namespace creation round-trip")
    void namespaceCreationRoundTripPreservesAllFields() {
        String zone = "environment-global";
        String uri = randomUri();
        String url = randomUrl();

        NamespaceCreateBody body = new NamespaceCreateBody(null, zone, uri, url);

        NamespaceDTO created = namespacesService.createNamespace(domainId, solutionId, body);

        // Verify id is valid UUID
        assertThat(created.id())
                .as("Created namespace must have a non-null id")
                .isNotNull()
                .isNotBlank();
        UUID.fromString(created.id()); // throws if not valid UUID

        // Retrieve by ID
        NamespaceDTO retrieved = namespacesService.getNamespaceById(
                domainId, solutionId, UUID.fromString(created.id()));

        // Assert all fields match
        assertThat(retrieved.id()).as("id should round-trip").isEqualTo(created.id());
        assertThat(retrieved.zone()).as("zone should round-trip").isEqualTo(zone);
        assertThat(retrieved.uri()).as("uri should round-trip").isEqualTo(uri);
        assertThat(retrieved.url()).as("url should round-trip").isEqualTo(url);
    }

    private String randomUri() {
        String[] prefixes = {
                "http://www.sifassociation.org/datamodel/na/",
                "http://www.sifassociation.org/infrastructure/",
                "urn:sif:namespace:"
        };
        return prefixes[random.nextInt(prefixes.length)] + random.nextInt(100000);
    }

    private String randomUrl() {
        String[] prefixes = {
                "https://specification.sifassociation.org/Implementation/NA/",
                "https://example.org/schemas/",
                "https://schemas.sifassociation.org/"
        };
        return prefixes[random.nextInt(prefixes.length)] + random.nextInt(100000) + "/schema.xsd";
    }
}
