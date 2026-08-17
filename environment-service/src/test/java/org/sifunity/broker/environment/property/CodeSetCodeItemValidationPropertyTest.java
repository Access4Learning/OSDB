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
import org.sifunity.broker.common.constant.CodeItemAction;
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.service.*;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Property-based test for Property 4: CodeItem validation.
 *
 * <p><strong>Property:</strong> For any code set creation request containing codeItems
 * where at least one code item has an invalid required field (code blank/null, value
 * blank/null or exceeding 128 chars, action null, timestamp blank/null), the service
 * SHALL return HTTP 400 and the code set SHALL NOT be persisted.</p>
 *
 * <p><strong>Validates: Requirements 13.1, 13.2, 13.3, 13.4, 8.4, 8.5</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CodeSetCodeItemValidationPropertyTest {

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

        domainId = "cs-cival-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "cs-cival-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
    }

    /**
     * Property 4: CodeItem validation rejects requests with invalid code item fields.
     *
     * <p><strong>Validates: Requirements 13.1, 13.2, 13.3, 13.4, 8.4, 8.5</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: codeset-registry, Property 4: CodeItem validation")
    void codeItemValidationRejectsInvalidFields() {
        // Pick which code item field to invalidate
        // 0=code blank/null, 1=value blank/null, 2=value exceeds 128, 3=action null, 4=timestamp blank/null
        int fieldToBreak = random.nextInt(5);

        String code = "CODE-" + random.nextInt(10000);
        String value = "ValidValue";
        CodeItemAction action = CodeItemAction.ADD;
        String timestamp = "2024-06-15T10:30:00Z";

        switch (fieldToBreak) {
            case 0 -> code = randomBlankOrNull();
            case 1 -> value = randomBlankOrNull();
            case 2 -> value = "X".repeat(129); // exceeds 128 chars
            case 3 -> action = null;
            case 4 -> timestamp = randomBlankOrNull();
        }

        CodeItemDTO invalidItem = new CodeItemDTO(
                code, null, null, value,
                null, null, null,
                action, timestamp
        );

        CodeItemsDTO codeItems = new CodeItemsDTO(List.of(invalidItem));

        CodeSetCreateBody body = new CodeSetCreateBody(
                "cival-test-" + random.nextInt(100000),
                "environment-global",
                "v2024.1",
                "2024-06-15T10:30:00Z",
                null,
                codeItems
        );

        assertThatThrownBy(() -> codeSetsService.createCodeSet(domainId, solutionId, body))
                .isInstanceOf(SifBrokerException.class)
                .satisfies(ex -> {
                    SifBrokerException sbe = (SifBrokerException) ex;
                    assertThat(sbe.getHttpStatus())
                            .as("Invalid code item field should return HTTP 400")
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    private String randomBlankOrNull() {
        int choice = random.nextInt(3);
        return switch (choice) {
            case 0 -> null;
            case 1 -> "";
            case 2 -> "   ";
            default -> null;
        };
    }
}
