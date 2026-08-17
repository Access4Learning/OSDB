// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.service.*;

import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Property-based test for Property 8: Delete removes namespace.
 *
 * <p><strong>Property:</strong> For any existing namespace, deleting it via DELETE
 * then attempting to retrieve it by ID via GET SHALL return HTTP 404.</p>
 *
 * <p><strong>Validates: Requirements 5.1</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NamespaceDeletePropertyTest {

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
                .publishNamespaceDeletedEvent(any(), anyString(), anyString());

        domainId = "ns-del-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "ns-del-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
    }

    /**
     * Property 8: Delete removes namespace, subsequent GET returns 404.
     *
     * <p><strong>Validates: Requirements 5.1</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: namespace-registry, Property 8: Delete removes namespace")
    void deleteRemovesNamespace() {
        NamespaceCreateBody body = new NamespaceCreateBody(
                null, "environment-global",
                "http://www.sifassociation.org/ns/del-" + random.nextInt(100000),
                "https://schemas.example.org/" + random.nextInt(100000) + "/schema.xsd"
        );

        NamespaceDTO created = namespacesService.createNamespace(domainId, solutionId, body);
        UUID id = UUID.fromString(created.id());

        // Verify it exists
        NamespaceDTO retrieved = namespacesService.getNamespaceById(domainId, solutionId, id);
        assertThat(retrieved.id()).isEqualTo(created.id());

        // Delete it
        namespacesService.deleteNamespace(domainId, solutionId, id);

        // Attempt GET — should return 404
        assertThatThrownBy(() -> namespacesService.getNamespaceById(domainId, solutionId, id))
                .isInstanceOf(SifBrokerException.class)
                .satisfies(ex -> {
                    SifBrokerException sbe = (SifBrokerException) ex;
                    assertThat(sbe.getHttpStatus())
                            .as("GET after DELETE should return HTTP 404")
                            .isEqualTo(HttpStatus.NOT_FOUND);
                });
    }
}
