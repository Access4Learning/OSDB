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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Property-based test for Property 9: Duplicate detection (uri + zone).
 *
 * <p><strong>Property:</strong> For any existing namespace with {@code uri} U and
 * {@code zone} Z in a solution, attempting to create another namespace with the same
 * {@code uri} U and {@code zone} Z in the same solution SHALL return HTTP 409 Conflict.</p>
 *
 * <p><strong>Validates: Requirements 3.9</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NamespaceDuplicateDetectionPropertyTest {

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

        domainId = "ns-dup-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "ns-dup-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
    }

    /**
     * Property 9: Duplicate detection returns 409 for same uri + zone + solution.
     *
     * <p><strong>Validates: Requirements 3.9</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: namespace-registry, Property 9: Duplicate detection")
    void duplicateDetectionReturns409() {
        String uri = "http://www.sifassociation.org/ns/dup-" + random.nextInt(100000);
        String zone = "environment-global";

        // Create first namespace
        NamespaceCreateBody body = new NamespaceCreateBody(
                null, zone, uri,
                "https://schemas.example.org/" + random.nextInt(100000) + "/schema.xsd"
        );

        NamespaceDTO created = namespacesService.createNamespace(domainId, solutionId, body);
        assertThat(created.id()).isNotNull();

        // Attempt to create duplicate with same uri and zone
        NamespaceCreateBody duplicateBody = new NamespaceCreateBody(
                null, zone, uri,
                "https://different-schemas.example.org/" + random.nextInt(100000) + "/schema.xsd"
        );

        assertThatThrownBy(() -> namespacesService.createNamespace(domainId, solutionId, duplicateBody))
                .isInstanceOf(SifBrokerException.class)
                .satisfies(ex -> {
                    SifBrokerException sbe = (SifBrokerException) ex;
                    assertThat(sbe.getHttpStatus())
                            .as("Duplicate namespace should return HTTP 409")
                            .isEqualTo(HttpStatus.CONFLICT);
                });
    }
}
