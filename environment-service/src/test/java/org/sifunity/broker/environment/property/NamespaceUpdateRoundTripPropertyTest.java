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
 * Property-based test for Property 2: Namespace update round-trip (full replacement).
 *
 * <p><strong>Property:</strong> For any existing namespace and for any valid update
 * request body (with valid {@code zone}, {@code uri}, {@code url}), updating the
 * namespace via PUT then retrieving it by ID via GET SHALL produce a response where
 * all fields match the update request body — not a merge of old and new data.</p>
 *
 * <p><strong>Validates: Requirements 4.1, 4.7</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NamespaceUpdateRoundTripPropertyTest {

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
        doNothing().when(namespaceEventPublisher)
                .publishNamespaceUpdatedEvent(any(), anyString(), anyString());

        domainId = "ns-upd-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "ns-upd-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
    }

    /**
     * Property 2: Namespace update round-trip replaces all fields (full replacement).
     *
     * <p><strong>Validates: Requirements 4.1, 4.7</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: namespace-registry, Property 2: Namespace update round-trip")
    void namespaceUpdateRoundTripReplacesAllFields() {
        // Create initial namespace
        NamespaceCreateBody originalBody = new NamespaceCreateBody(
                null, "environment-global", randomUri(), randomUrl());

        NamespaceDTO created = namespacesService.createNamespace(domainId, solutionId, originalBody);
        UUID id = UUID.fromString(created.id());

        // Generate different update values
        String updatedZone = "environment-global";
        String updatedUri = randomUri();
        String updatedUrl = randomUrl();

        NamespaceCreateBody updateBody = new NamespaceCreateBody(
                null, updatedZone, updatedUri, updatedUrl);

        // Update
        namespacesService.updateNamespace(domainId, solutionId, id, updateBody);

        // Retrieve
        NamespaceDTO retrieved = namespacesService.getNamespaceById(domainId, solutionId, id);

        // Assert all fields match update body, not original
        assertThat(retrieved.id()).as("id should be preserved").isEqualTo(created.id());
        assertThat(retrieved.zone()).as("zone should match update body").isEqualTo(updatedZone);
        assertThat(retrieved.uri()).as("uri should match update body, not original").isEqualTo(updatedUri);
        assertThat(retrieved.url()).as("url should match update body, not original").isEqualTo(updatedUrl);
    }

    private String randomUri() {
        return "http://www.sifassociation.org/ns/" + random.nextInt(100000);
    }

    private String randomUrl() {
        return "https://schemas.example.org/" + random.nextInt(100000) + "/schema.xsd";
    }
}
