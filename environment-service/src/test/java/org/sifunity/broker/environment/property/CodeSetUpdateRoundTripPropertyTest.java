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
import org.sifunity.broker.common.constant.CodeItemAction;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.service.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

/**
 * Property-based test for Property 2: Code set update round-trip (full replacement).
 *
 * <p><strong>Property:</strong> For any existing code set and for any valid update
 * request body (with valid {@code zone}, {@code version}, {@code timestamp}, and
 * optional fields), updating the code set via PUT then retrieving it by ID via GET
 * SHALL produce a response where all fields match the update request body — not a
 * merge of old and new data.</p>
 *
 * <p><strong>Validates: Requirements 4.1, 4.6</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CodeSetUpdateRoundTripPropertyTest {

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
        // Stub event publisher to avoid HTTP calls
        doNothing().when(codeSetEventPublisher)
                .publishCodeSetCreatedEvent(any(), anyString(), anyString());
        doNothing().when(codeSetEventPublisher)
                .publishCodeSetUpdatedEvent(any(), anyString(), anyString());

        // Create infrastructure: domain, solution (auto-creates environment-global zone + codeSets provider)
        domainId = "cs-upd-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));

        solutionId = "cs-upd-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
    }

    /**
     * Property 2: Code set update round-trip replaces all fields (full replacement).
     *
     * <p><strong>Validates: Requirements 4.1, 4.6</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: codeset-registry, Property 2: Code set update round-trip")
    void codeSetUpdateRoundTripReplacesAllFields() {
        // 1. Create an initial code set with random values
        String zone = "environment-global";
        CodeSetCreateBody originalBody = new CodeSetCreateBody(
                "update-test-" + random.nextInt(100000),
                zone,
                randomVersion(),
                randomTimestamp(),
                randomOptionalSource(),
                randomOptionalCodeItems()
        );

        CodeSetDTO created = codeSetsService.createCodeSet(domainId, solutionId, originalBody);
        assertThat(created.id()).isNotNull().isNotBlank();

        // 2. Generate completely different update values
        String updatedVersion = randomVersion();
        String updatedTimestamp = randomTimestamp();
        String updatedSource = randomOptionalSource();
        CodeItemsDTO updatedCodeItems = randomOptionalCodeItems();

        CodeSetCreateBody updateBody = new CodeSetCreateBody(
                created.id(),
                zone,
                updatedVersion,
                updatedTimestamp,
                updatedSource,
                updatedCodeItems
        );

        // 3. Update via service (PUT semantics)
        CodeSetDTO updated = codeSetsService.updateCodeSet(
                domainId, solutionId, created.id(), updateBody);

        // 4. Retrieve by ID
        CodeSetDTO retrieved = codeSetsService.getCodeSetById(
                domainId, solutionId, created.id(), zone);

        // 5. Assert all fields match update request body — NOT original (Req 4.1, 4.6)
        assertThat(retrieved.id())
                .as("id should match after update")
                .isEqualTo(created.id());
        assertThat(retrieved.zone())
                .as("zone should match update body")
                .isEqualTo(zone);
        assertThat(retrieved.version())
                .as("version should match update body, not original")
                .isEqualTo(updatedVersion);
        assertThat(retrieved.timestamp())
                .as("timestamp should match update body, not original")
                .isEqualTo(updatedTimestamp);
        assertThat(retrieved.source())
                .as("source should match update body, not original")
                .isEqualTo(updatedSource);

        // Assert codeItems match update body — full replacement, not merge (Req 4.6)
        assertCodeItemsMatch(updatedCodeItems, retrieved.codeItems());
    }

    // ========== Assertion Helpers ==========

    private void assertCodeItemsMatch(CodeItemsDTO expected, CodeItemsDTO actual) {
        if (expected == null || expected.codeItem() == null || expected.codeItem().isEmpty()) {
            if (actual != null && actual.codeItem() != null) {
                assertThat(actual.codeItem())
                        .as("codeItems should be empty when update body has none")
                        .isEmpty();
            }
            return;
        }

        assertThat(actual).as("codeItems should not be null").isNotNull();
        assertThat(actual.codeItem()).as("codeItem list should not be null").isNotNull();
        assertThat(actual.codeItem())
                .as("codeItem list size should match update body")
                .hasSameSizeAs(expected.codeItem());

        for (int i = 0; i < expected.codeItem().size(); i++) {
            CodeItemDTO exp = expected.codeItem().get(i);
            CodeItemDTO act = actual.codeItem().get(i);

            assertThat(act.code())
                    .as("codeItem[%d].code should match update body", i)
                    .isEqualTo(exp.code());
            assertThat(act.value())
                    .as("codeItem[%d].value should match update body", i)
                    .isEqualTo(exp.value());
            assertThat(act.action())
                    .as("codeItem[%d].action should match update body", i)
                    .isEqualTo(exp.action());
            assertThat(act.timestamp())
                    .as("codeItem[%d].timestamp should match update body", i)
                    .isEqualTo(exp.timestamp());
            assertThat(act.source())
                    .as("codeItem[%d].source should match update body", i)
                    .isEqualTo(exp.source());
            assertThat(act.namespace())
                    .as("codeItem[%d].namespace should match update body", i)
                    .isEqualTo(exp.namespace());
            assertThat(act.description())
                    .as("codeItem[%d].description should match update body", i)
                    .isEqualTo(exp.description());
            assertThat(act.definition())
                    .as("codeItem[%d].definition should match update body", i)
                    .isEqualTo(exp.definition());

            // Assert nested aliases
            assertAliasesMatch(exp.aliases(), act.aliases(), i);
        }
    }

    private void assertAliasesMatch(AliasesDTO expected, AliasesDTO actual, int codeItemIndex) {
        if (expected == null || expected.alias() == null || expected.alias().isEmpty()) {
            if (actual != null && actual.alias() != null) {
                assertThat(actual.alias())
                        .as("codeItem[%d].aliases should be empty when update body has none", codeItemIndex)
                        .isEmpty();
            }
            return;
        }

        assertThat(actual).as("codeItem[%d].aliases should not be null", codeItemIndex).isNotNull();
        assertThat(actual.alias()).as("codeItem[%d].alias list should not be null", codeItemIndex).isNotNull();
        assertThat(actual.alias())
                .as("codeItem[%d].alias list size should match update body", codeItemIndex)
                .hasSameSizeAs(expected.alias());

        for (int j = 0; j < expected.alias().size(); j++) {
            AliasDTO exp = expected.alias().get(j);
            AliasDTO act = actual.alias().get(j);

            assertThat(act.source())
                    .as("codeItem[%d].alias[%d].source should match update body", codeItemIndex, j)
                    .isEqualTo(exp.source());
            assertThat(act.namespace())
                    .as("codeItem[%d].alias[%d].namespace should match update body", codeItemIndex, j)
                    .isEqualTo(exp.namespace());

            if (exp.code() == null) {
                assertThat(act.code())
                        .as("codeItem[%d].alias[%d].code should be null", codeItemIndex, j)
                        .isNull();
            } else {
                assertThat(act.code())
                        .as("codeItem[%d].alias[%d].code should not be null", codeItemIndex, j)
                        .isNotNull();
                assertThat(act.code().old())
                        .as("codeItem[%d].alias[%d].code.old should match update body", codeItemIndex, j)
                        .isEqualTo(exp.code().old());
                assertThat(act.code().official())
                        .as("codeItem[%d].alias[%d].code.official should match update body", codeItemIndex, j)
                        .isEqualTo(exp.code().official());
                assertThat(act.code().value())
                        .as("codeItem[%d].alias[%d].code.value should match update body", codeItemIndex, j)
                        .isEqualTo(exp.code().value());
            }
        }
    }

    // ========== Random Data Generators ==========

    private String randomVersion() {
        return "v" + (2020 + random.nextInt(5)) + "." + (random.nextInt(9) + 1);
    }

    private String randomTimestamp() {
        int year = 2020 + random.nextInt(5);
        int month = random.nextInt(12) + 1;
        int day = random.nextInt(28) + 1;
        int hour = random.nextInt(24);
        int minute = random.nextInt(60);
        return String.format("%04d-%02d-%02dT%02d:%02d:00Z", year, month, day, hour, minute);
    }

    private String randomOptionalSource() {
        if (random.nextBoolean()) {
            return null;
        }
        String[] sources = {
                "https://nces.ed.gov/ipeds/cipcode",
                "https://example.org/codes",
                "urn:sif:codeset:source:" + random.nextInt(1000)
        };
        return sources[random.nextInt(sources.length)];
    }

    private CodeItemsDTO randomOptionalCodeItems() {
        // ~50% chance of no codeItems
        if (random.nextBoolean()) {
            return null;
        }

        int count = 1 + random.nextInt(4); // 1 to 4 code items
        List<CodeItemDTO> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(randomCodeItem());
        }
        return new CodeItemsDTO(items);
    }

    private CodeItemDTO randomCodeItem() {
        String code = String.valueOf(random.nextInt(9000) + 1000);
        String value = randomValue();
        CodeItemAction action = randomAction();
        String timestamp = randomTimestamp();
        String source = randomOptionalSource();
        String namespace = randomOptionalNamespace();
        String description = randomOptionalDescription();
        String definition = randomOptionalDefinition();
        AliasesDTO aliases = randomOptionalAliases();

        return new CodeItemDTO(
                code, source, namespace, value,
                description, definition, aliases,
                action, timestamp
        );
    }

    private String randomValue() {
        String[] values = {
                "American Indian", "Asian", "Black", "Hispanic",
                "White", "Two or More", "Male", "Female", "Non-Binary"
        };
        return values[random.nextInt(values.length)];
    }

    private CodeItemAction randomAction() {
        CodeItemAction[] values = CodeItemAction.values();
        return values[random.nextInt(values.length)];
    }

    private String randomOptionalNamespace() {
        if (random.nextBoolean()) {
            return null;
        }
        return "urn:sif:codeset:ns:" + random.nextInt(1000);
    }

    private String randomOptionalDescription() {
        if (random.nextBoolean()) {
            return null;
        }
        String[] descs = {
                "A person having origins in the original peoples",
                "Standard classification code",
                "Grade level indicator for K-12"
        };
        return descs[random.nextInt(descs.length)];
    }

    private String randomOptionalDefinition() {
        if (random.nextBoolean()) {
            return null;
        }
        return "Full definition text for code item " + random.nextInt(10000);
    }

    private AliasesDTO randomOptionalAliases() {
        // ~60% chance of no aliases
        if (random.nextInt(5) < 3) {
            return null;
        }

        int count = 1 + random.nextInt(3); // 1 to 3 aliases
        List<AliasDTO> aliases = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            aliases.add(randomAlias());
        }
        return new AliasesDTO(aliases);
    }

    private AliasDTO randomAlias() {
        AliasCodeDTO code = randomOptionalAliasCode();
        String source = randomOptionalSource();
        String namespace = randomOptionalNamespace();
        return new AliasDTO(code, source, namespace);
    }

    private AliasCodeDTO randomOptionalAliasCode() {
        if (random.nextInt(4) == 0) {
            return null;
        }
        Boolean old = random.nextBoolean() ? random.nextBoolean() : null;
        Boolean official = random.nextBoolean() ? random.nextBoolean() : null;
        String value = "ALIAS-" + random.nextInt(10000);
        return new AliasCodeDTO(old, official, value);
    }
}
