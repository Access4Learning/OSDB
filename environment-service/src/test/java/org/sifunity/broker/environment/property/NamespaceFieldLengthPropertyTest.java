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
 * Property-based test for Property 4: URI/URL length enforcement.
 *
 * <p><strong>Property:</strong> For any namespace creation request where the {@code uri}
 * field exceeds 2048 characters or the {@code url} field exceeds 2048 characters,
 * the service SHALL return HTTP 400.</p>
 *
 * <p><strong>Validates: Requirements 4.4, 8.2, 8.3</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NamespaceFieldLengthPropertyTest {

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

        domainId = "ns-flen-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "ns-flen-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
    }

    /**
     * Property 4: URI/URL length enforcement rejects oversized fields.
     *
     * <p><strong>Validates: Requirements 4.4, 8.2, 8.3</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: namespace-registry, Property 4: URI/URL length enforcement")
    void fieldLengthEnforcementRejectsOversizedFields() {
        // Pick which field to exceed: 0=uri>2048, 1=url>2048
        int fieldToBreak = random.nextInt(2);

        String uri = "http://www.sifassociation.org/ns/valid";
        String url = "https://schemas.example.org/valid/schema.xsd";

        switch (fieldToBreak) {
            case 0 -> {
                int overLength = 2049 + random.nextInt(200);
                uri = "http://www.sifassociation.org/" + "X".repeat(overLength);
            }
            case 1 -> {
                int overLength = 2049 + random.nextInt(200);
                url = "https://schemas.example.org/" + "X".repeat(overLength);
            }
        }

        String finalUri = uri;
        String finalUrl = url;

        NamespaceCreateBody body = new NamespaceCreateBody(
                null, "environment-global", finalUri, finalUrl);

        assertThatThrownBy(() -> namespacesService.createNamespace(domainId, solutionId, body))
                .isInstanceOf(SifBrokerException.class)
                .satisfies(ex -> {
                    SifBrokerException sbe = (SifBrokerException) ex;
                    assertThat(sbe.getHttpStatus())
                            .as("Oversized field should return HTTP 400")
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }
}
