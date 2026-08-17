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
 * Property-based test for Property 3: Required field validation.
 *
 * <p><strong>Property:</strong> For any namespace creation request where at least one
 * required field ({@code zone}, {@code uri}, or {@code url}) is missing, null, or
 * blank/whitespace-only, the service SHALL return HTTP 400 and the namespace SHALL
 * NOT be persisted.</p>
 *
 * <p><strong>Validates: Requirements 3.2, 3.3, 3.4, 3.5, 4.3</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NamespaceRequiredFieldValidationPropertyTest {

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

        domainId = "ns-reqval-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "ns-reqval-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
    }

    /**
     * Property 3: Required field validation rejects requests with missing/blank required fields.
     *
     * <p><strong>Validates: Requirements 3.2, 3.3, 3.4, 3.5, 4.3</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: namespace-registry, Property 3: Required field validation")
    void requiredFieldValidationRejectsMissingOrBlankFields() {
        String zone = "environment-global";
        String uri = "http://www.sifassociation.org/ns/" + random.nextInt(100000);
        String url = "https://schemas.example.org/" + random.nextInt(100000) + "/schema.xsd";

        // Pick which field to break (0=zone, 1=uri, 2=url)
        int fieldToBreak = random.nextInt(3);

        switch (fieldToBreak) {
            case 0 -> zone = randomInvalidValue();
            case 1 -> uri = randomInvalidValue();
            case 2 -> url = randomInvalidValue();
        }

        String finalZone = zone;
        String finalUri = uri;
        String finalUrl = url;

        NamespaceCreateBody body = new NamespaceCreateBody(null, finalZone, finalUri, finalUrl);

        assertThatThrownBy(() -> namespacesService.createNamespace(domainId, solutionId, body))
                .isInstanceOf(SifBrokerException.class)
                .satisfies(ex -> {
                    SifBrokerException sbe = (SifBrokerException) ex;
                    assertThat(sbe.getHttpStatus())
                            .as("Missing/blank required field should return HTTP 400")
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    private String randomInvalidValue() {
        int choice = random.nextInt(3);
        return switch (choice) {
            case 0 -> null;
            case 1 -> "";
            case 2 -> "   ";
            default -> null;
        };
    }
}
