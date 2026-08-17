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
 * Property-based test for Property 10: ID is broker-assigned UUID.
 *
 * <p><strong>Property:</strong> For any valid namespace creation request — whether or
 * not an {@code id} field is provided in the request body — the service SHALL assign
 * a broker-generated UUID as the {@code id} in the response. Any {@code id} value
 * provided in the request body SHALL be ignored.</p>
 *
 * <p><strong>Validates: Requirements 3.6</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NamespaceIdAssignmentPropertyTest {

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

        domainId = "ns-ida-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "ns-ida-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
    }

    /**
     * Property 10: ID is broker-assigned UUID — provided id is ignored.
     *
     * <p><strong>Validates: Requirements 3.6</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: namespace-registry, Property 10: ID is broker-assigned UUID")
    void idIsBrokerAssignedUuid() {
        boolean provideId = random.nextBoolean();

        String providedId = provideId ? UUID.randomUUID().toString() : null;

        NamespaceCreateBody body = new NamespaceCreateBody(
                providedId, "environment-global",
                "http://www.sifassociation.org/ns/ida-" + random.nextInt(100000),
                "https://schemas.example.org/" + random.nextInt(100000) + "/schema.xsd"
        );

        NamespaceDTO created = namespacesService.createNamespace(domainId, solutionId, body);

        // Response must always have a valid UUID id
        assertThat(created.id())
                .as("Broker must assign a non-null non-blank UUID id")
                .isNotNull()
                .isNotBlank();

        // Verify it's a valid UUID
        UUID assignedId = UUID.fromString(created.id());
        assertThat(assignedId).isNotNull();

        if (provideId) {
            // When id IS provided, broker should IGNORE it and assign a new UUID
            assertThat(created.id())
                    .as("Provided id should be ignored — broker assigns new UUID")
                    .isNotEqualTo(providedId);
        }
    }
}
