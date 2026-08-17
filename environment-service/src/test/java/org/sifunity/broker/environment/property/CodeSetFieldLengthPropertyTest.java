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
 * Property-based test for Property 5: Field length enforcement.
 *
 * <p><strong>Property:</strong> For any code set creation request where the {@code id}
 * field exceeds 128 characters, or any code item's {@code description} exceeds 1024
 * characters, or any code item's {@code definition} exceeds 4096 characters, the
 * service SHALL return HTTP 400.</p>
 *
 * <p><strong>Validates: Requirements 8.6, 8.7, 8.8</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CodeSetFieldLengthPropertyTest {

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

        domainId = "cs-flen-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "cs-flen-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
    }

    /**
     * Property 5: Field length enforcement rejects oversized fields.
     *
     * <p><strong>Validates: Requirements 8.6, 8.7, 8.8</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: codeset-registry, Property 5: Field length enforcement")
    void fieldLengthEnforcementRejectsOversizedFields() {
        // Pick which field to exceed: 0=id>128, 1=description>1024, 2=definition>4096
        int fieldToBreak = random.nextInt(3);

        String id = "flen-test-" + random.nextInt(100000);
        String description = null;
        String definition = null;
        CodeItemsDTO codeItems = null;

        switch (fieldToBreak) {
            case 0 -> {
                // id exceeds 128 chars
                int overLength = 129 + random.nextInt(100);
                id = "X".repeat(overLength);
            }
            case 1 -> {
                // codeItem description exceeds 1024 chars
                int overLength = 1025 + random.nextInt(200);
                description = "D".repeat(overLength);
                CodeItemDTO item = new CodeItemDTO(
                        "CODE1", null, null, "ValidValue",
                        description, null, null,
                        CodeItemAction.ADD, "2024-06-15T10:30:00Z"
                );
                codeItems = new CodeItemsDTO(List.of(item));
            }
            case 2 -> {
                // codeItem definition exceeds 4096 chars
                int overLength = 4097 + random.nextInt(500);
                definition = "F".repeat(overLength);
                CodeItemDTO item = new CodeItemDTO(
                        "CODE1", null, null, "ValidValue",
                        null, definition, null,
                        CodeItemAction.ADD, "2024-06-15T10:30:00Z"
                );
                codeItems = new CodeItemsDTO(List.of(item));
            }
        }

        CodeSetCreateBody body = new CodeSetCreateBody(
                id,
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
                            .as("Oversized field should return HTTP 400")
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }
}
