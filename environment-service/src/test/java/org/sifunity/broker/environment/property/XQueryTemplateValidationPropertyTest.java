// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

// Feature: named-query-support, Property 5: Type and status enum validation

import net.jqwik.api.*;
import org.sifunity.broker.environment.dto.XQueryParametersDTO;
import org.sifunity.broker.environment.dto.XQueryTemplateBody;
import org.sifunity.broker.environment.dto.XQueryTemplateCreateRequest;
import org.sifunity.broker.environment.repository.SolutionServiceRepository;
import org.sifunity.broker.environment.repository.*;
import org.sifunity.broker.environment.service.XQueryTemplateService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Property-based test for Property 5: Type and status enum validation.
 *
 * <p><strong>Property:</strong> For any string NOT in {SINGULAR, FORMULA, EXTENDED},
 * creating a template with that string as type SHALL be rejected. For any string NOT
 * in {PENDING, APPROVED, DISALLOWED}, creating a template with that string as status
 * SHALL be rejected. The request SHALL fail without persisting anything.</p>
 *
 * <p>This is a unit-level test — no Spring context or database required since
 * validation happens before persistence.</p>
 *
 * <p><strong>Validates: Requirements 2.3, 2.4</strong></p>
 */
class XQueryTemplateValidationPropertyTest {

    private static final Set<String> VALID_TYPES = Set.of("SINGULAR", "FORMULA", "EXTENDED");
    private static final Set<String> VALID_STATUSES = Set.of("PENDING", "APPROVED", "DISALLOWED");

    private final XQueryTemplateRepository xQueryTemplateRepository = mock(XQueryTemplateRepository.class);
    private final SolutionRepository solutionRepository = mock(SolutionRepository.class);
    private final EnvironmentRepository environmentRepository = mock(EnvironmentRepository.class);
    private final DomainRepository domainRepository = mock(DomainRepository.class);
    // Creating a template also creates its companion solution_services row, so the service needs
    // this collaborator. Mocked: this test is about type/status validation, not the coupling.
    private final SolutionServiceRepository solutionServiceRepository = mock(SolutionServiceRepository.class);

    private final XQueryTemplateService service = new XQueryTemplateService(
            xQueryTemplateRepository,
            solutionRepository,
            environmentRepository,
            domainRepository,
            solutionServiceRepository
    );

    /**
     * Property 5a: Invalid type strings are rejected.
     *
     * <p>For any string NOT in {SINGULAR, FORMULA, EXTENDED} (case-insensitive),
     * creating a template with that type SHALL throw IllegalArgumentException
     * and nothing is persisted.</p>
     *
     * <p><strong>Validates: Requirements 2.3</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: named-query-support, Property 5: Type and status enum validation")
    void invalidTypeStringsAreRejected(@ForAll("invalidTypes") String invalidType) {
        XQueryTemplateCreateRequest request = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                "validToken",       // non-blank token passes first validation
                invalidType,        // invalid type - should be rejected
                "APPROVED",         // valid status
                null, null, null, new XQueryParametersDTO(List.of()), null
        ));

        assertThatThrownBy(() -> service.create("test-domain", "test-solution", UUID.randomUUID(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid type");

        // Verify no persistence occurred
        verifyNoInteractions(xQueryTemplateRepository);
    }

    /**
     * Property 5b: Invalid status strings are rejected.
     *
     * <p>For any string NOT in {PENDING, APPROVED, DISALLOWED} (case-insensitive),
     * creating a template with that status SHALL throw IllegalArgumentException
     * and nothing is persisted.</p>
     *
     * <p><strong>Validates: Requirements 2.4</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: named-query-support, Property 5: Type and status enum validation")
    void invalidStatusStringsAreRejected(@ForAll("invalidStatuses") String invalidStatus) {
        XQueryTemplateCreateRequest request = new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                "validToken",       // non-blank token passes first validation
                "SINGULAR",         // valid type
                invalidStatus,      // invalid status - should be rejected
                null, null, null, new XQueryParametersDTO(List.of()), null
        ));

        assertThatThrownBy(() -> service.create("test-domain", "test-solution", UUID.randomUUID(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid status");

        // Verify no persistence occurred
        verifyNoInteractions(xQueryTemplateRepository);
    }

    // ========== Arbitraries (Test Data Generators) ==========

    /**
     * Generates strings that are NOT valid XQueryType values (case-insensitive).
     *
     * <p>Produces: random alphabetic strings, near-miss typos, numeric strings,
     * strings with special characters, and empty/blank strings that are not null.</p>
     */
    @Provide
    Arbitrary<String> invalidTypes() {
        Arbitrary<String> randomAlpha = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(s -> !VALID_TYPES.contains(s.toUpperCase()));

        Arbitrary<String> nearMisses = Arbitraries.of(
                "SING", "SINGULARS", "FORMUL", "FORMULAS", "EXTEND", "EXTENDEDS",
                "singular1", "form ula", "EXTE NDED", "OBJECT", "UTILITY",
                "DELAYED", "IMMEDIATE", "FUNCTIONAL", "QUERY"
        );

        Arbitrary<String> numericStrings = Arbitraries.strings()
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(10);

        Arbitrary<String> specialChars = Arbitraries.of(
                "SINGULAR!", "@FORMULA", "EX#TENDED", "sing-ular",
                "formula.extended", "TYPE_ONE", "type/two"
        );

        return Arbitraries.oneOf(randomAlpha, nearMisses, numericStrings, specialChars);
    }

    /**
     * Generates strings that are NOT valid XQueryStatus values (case-insensitive).
     *
     * <p>Produces: random alphabetic strings, near-miss typos, numeric strings,
     * strings with special characters, and empty/blank strings that are not null.</p>
     */
    @Provide
    Arbitrary<String> invalidStatuses() {
        Arbitrary<String> randomAlpha = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(s -> !VALID_STATUSES.contains(s.toUpperCase()));

        Arbitrary<String> nearMisses = Arbitraries.of(
                "PEND", "PENDINGS", "APPROVE", "APPROVEDS", "DISALLOW", "DISALLOWEDS",
                "pending1", "app roved", "DIS ALLOWED", "ACTIVE", "INACTIVE",
                "ENABLED", "DISABLED", "REJECTED", "ACCEPTED"
        );

        Arbitrary<String> numericStrings = Arbitraries.strings()
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(10);

        Arbitrary<String> specialChars = Arbitraries.of(
                "PENDING!", "@APPROVED", "DIS#ALLOWED", "pend-ing",
                "approved.status", "STATUS_ONE", "status/two"
        );

        return Arbitraries.oneOf(randomAlpha, nearMisses, numericStrings, specialChars);
    }
}
