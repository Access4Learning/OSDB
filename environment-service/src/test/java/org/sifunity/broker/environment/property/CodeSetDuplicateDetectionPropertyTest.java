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
 * Property-based test for Property 10: Duplicate detection.
 *
 * <p><strong>Property:</strong> For any existing code set with {@code id} I and
 * {@code zone} Z in a solution, attempting to create another code set with the same
 * {@code id} I and {@code zone} Z in the same solution SHALL return HTTP 409 Conflict.</p>
 *
 * <p><strong>Validates: Requirements 3.11</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CodeSetDuplicateDetectionPropertyTest {

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

        domainId = "cs-dup-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "cs-dup-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
    }

    /**
     * Property 10: Duplicate detection returns 409 for same id + zone + solution.
     *
     * <p><strong>Validates: Requirements 3.11</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: codeset-registry, Property 10: Duplicate detection")
    void duplicateDetectionReturns409() {
        String id = "dup-test-" + random.nextInt(100000);
        String zone = "environment-global";

        // Create first code set
        CodeSetCreateBody body = new CodeSetCreateBody(
                id, zone,
                "v2024." + random.nextInt(9),
                "2024-06-15T10:30:00Z",
                null, null
        );

        CodeSetDTO created = codeSetsService.createCodeSet(domainId, solutionId, body);
        assertThat(created.id()).isEqualTo(id);

        // Attempt to create duplicate with same id and zone
        CodeSetCreateBody duplicateBody = new CodeSetCreateBody(
                id, zone,
                "v2025." + random.nextInt(9),
                "2025-01-01T00:00:00Z",
                "https://different-source.example.com",
                null
        );

        assertThatThrownBy(() -> codeSetsService.createCodeSet(domainId, solutionId, duplicateBody))
                .isInstanceOf(SifBrokerException.class)
                .satisfies(ex -> {
                    SifBrokerException sbe = (SifBrokerException) ex;
                    assertThat(sbe.getHttpStatus())
                            .as("Duplicate code set should return HTTP 409")
                            .isEqualTo(HttpStatus.CONFLICT);
                });
    }
}
