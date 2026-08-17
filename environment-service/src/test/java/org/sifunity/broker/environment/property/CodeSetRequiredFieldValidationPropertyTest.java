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
 * <p><strong>Property:</strong> For any code set creation request where at least one
 * required field ({@code zone}, {@code version}, or {@code timestamp}) is missing,
 * null, or blank/whitespace-only, the service SHALL return HTTP 400 and the code set
 * SHALL NOT be persisted.</p>
 *
 * <p><strong>Validates: Requirements 3.2, 3.3, 3.4, 3.5, 4.3</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CodeSetRequiredFieldValidationPropertyTest {

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

        domainId = "cs-reqval-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "cs-reqval-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
    }

    /**
     * Property 3: Required field validation rejects requests with missing/blank required fields.
     *
     * <p><strong>Validates: Requirements 3.2, 3.3, 3.4, 3.5, 4.3</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: codeset-registry, Property 3: Required field validation")
    void requiredFieldValidationRejectsMissingOrBlankFields() {
        // Randomly choose which required field(s) to invalidate
        String zone = "environment-global";
        String version = "v2024.1";
        String timestamp = "2024-06-15T10:30:00Z";

        // Pick which field to break (0=zone, 1=version, 2=timestamp)
        int fieldToBreak = random.nextInt(3);

        switch (fieldToBreak) {
            case 0 -> zone = randomInvalidValue();
            case 1 -> version = randomInvalidValue();
            case 2 -> timestamp = randomInvalidValue();
        }

        String finalZone = zone;
        String finalVersion = version;
        String finalTimestamp = timestamp;

        CodeSetCreateBody body = new CodeSetCreateBody(
                "reqval-test-" + random.nextInt(100000),
                finalZone, finalVersion, finalTimestamp,
                null, null
        );

        assertThatThrownBy(() -> codeSetsService.createCodeSet(domainId, solutionId, body))
                .isInstanceOf(SifBrokerException.class)
                .satisfies(ex -> {
                    SifBrokerException sbe = (SifBrokerException) ex;
                    assertThat(sbe.getHttpStatus())
                            .as("Missing/blank required field should return HTTP 400")
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    /**
     * Returns null, empty string, or whitespace-only string randomly.
     */
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
