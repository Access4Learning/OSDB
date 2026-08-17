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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Property-based test for Property 11: ID assignment when not provided.
 *
 * <p><strong>Property:</strong> For any valid code set creation request where the
 * {@code id} field is absent or null, the service SHALL assign a non-null, non-blank
 * identifier in the response. When the {@code id} field IS provided (within 128 chars),
 * the response SHALL use the provided value.</p>
 *
 * <p><strong>Validates: Requirements 3.6</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CodeSetIdAssignmentPropertyTest {

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

        domainId = "cs-ida-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "cs-ida-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
    }

    /**
     * Property 11: ID assignment — auto-assigned when absent, preserved when provided.
     *
     * <p><strong>Validates: Requirements 3.6</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: codeset-registry, Property 11: ID assignment when not provided")
    void idAssignmentBehavior() {
        // Randomly test either absent id or provided id
        boolean provideId = random.nextBoolean();

        String providedId = provideId
                ? "provided-id-" + random.nextInt(100000)
                : null;

        CodeSetCreateBody body = new CodeSetCreateBody(
                providedId,
                "environment-global",
                "v2024." + (random.nextInt(9) + 1),
                "2024-06-15T10:30:00Z",
                null, null
        );

        CodeSetDTO created = codeSetsService.createCodeSet(domainId, solutionId, body);

        if (provideId) {
            // When id IS provided, response should use provided value
            assertThat(created.id())
                    .as("When id is provided, response should use provided value")
                    .isEqualTo(providedId);
        } else {
            // When id is absent/null, service should assign a non-null non-blank id
            assertThat(created.id())
                    .as("When id is absent, service should assign a non-null non-blank id")
                    .isNotNull()
                    .isNotBlank();
        }
    }
}
