// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import net.jqwik.api.*;
import org.sifunity.broker.environment.dto.TagDefinitionCreateRequest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for Property 4: Blank tagName rejection.
 *
 * <p><strong>Property 4: Blank tagName rejection</strong></p>
 *
 * <p>For any string composed entirely of whitespace characters (including empty string),
 * a create request with that string as tagName SHALL be rejected with a validation error.</p>
 *
 * <p><strong>Validates: Requirements 3.3</strong></p>
 *
 * <p>This test exercises Jakarta Bean Validation ({@code @NotBlank}) on the
 * {@link TagDefinitionCreateRequest} record to verify that blank tagName values
 * are always rejected regardless of the specific whitespace composition.</p>
 */
class BlankTagNameRejectionPropertyTest {

    private static final Validator VALIDATOR;

    static {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            VALIDATOR = factory.getValidator();
        }
    }

    /**
     * Property 4: Blank tagName rejection — whitespace-only strings
     *
     * <p>Any string composed entirely of whitespace characters (spaces, tabs, newlines,
     * carriage returns) used as tagName should trigger a {@code @NotBlank} constraint
     * violation on the TagDefinitionCreateRequest.</p>
     *
     * <p><strong>Validates: Requirements 3.3</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: tag-management-admin, Property 4: Blank tagName rejection")
    void whitespaceOnlyTagNameIsRejected(
            @ForAll("whitespaceOnlyStrings") String blankTagName) {

        TagDefinitionCreateRequest request = new TagDefinitionCreateRequest(
                blankTagName,
                "Some description",
                List.of("SOLUTION"),
                null,
                "admin-user"
        );

        Set<ConstraintViolation<TagDefinitionCreateRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations)
                .as("Whitespace-only tagName '%s' should be rejected by @NotBlank", blankTagName)
                .isNotEmpty();

        boolean hasTagNameViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("tagName"));

        assertThat(hasTagNameViolation)
                .as("Violation should be on tagName field for input '%s'", blankTagName)
                .isTrue();
    }

    /**
     * Property 4: Blank tagName rejection — empty string
     *
     * <p>An empty string used as tagName should trigger a {@code @NotBlank} constraint
     * violation on the TagDefinitionCreateRequest.</p>
     *
     * <p><strong>Validates: Requirements 3.3</strong></p>
     */
    @Property(tries = 20)
    @Tag("Feature: tag-management-admin, Property 4: Blank tagName rejection")
    void emptyTagNameIsRejected(@ForAll("emptyOrBlankStrings") String blankTagName) {

        TagDefinitionCreateRequest request = new TagDefinitionCreateRequest(
                blankTagName,
                "Valid description",
                List.of("ENVIRONMENT", "QUEUE"),
                List.of("true", "false"),
                "system"
        );

        Set<ConstraintViolation<TagDefinitionCreateRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations)
                .as("Empty/blank tagName '%s' should be rejected by @NotBlank", blankTagName)
                .isNotEmpty();

        boolean hasTagNameViolation = violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("tagName"));

        assertThat(hasTagNameViolation)
                .as("Violation should be on tagName field for input '%s'", blankTagName)
                .isTrue();
    }

    // ========== Arbitraries ==========

    /**
     * Generates strings composed entirely of whitespace characters:
     * spaces, tabs, newlines, carriage returns.
     */
    @Provide
    Arbitrary<String> whitespaceOnlyStrings() {
        return Arbitraries.of(' ', '\t', '\n', '\r')
                .list()
                .ofMinSize(1)
                .ofMaxSize(20)
                .map(chars -> {
                    StringBuilder sb = new StringBuilder();
                    for (char c : chars) {
                        sb.append(c);
                    }
                    return sb.toString();
                });
    }

    /**
     * Generates empty string and various whitespace-only strings
     * to cover the full blank input space.
     */
    @Provide
    Arbitrary<String> emptyOrBlankStrings() {
        Arbitrary<String> emptyString = Arbitraries.just("");

        Arbitrary<String> whitespace = Arbitraries.of(' ', '\t', '\n', '\r')
                .list()
                .ofMinSize(1)
                .ofMaxSize(15)
                .map(chars -> {
                    StringBuilder sb = new StringBuilder();
                    for (char c : chars) {
                        sb.append(c);
                    }
                    return sb.toString();
                });

        return Arbitraries.oneOf(emptyString, whitespace);
    }
}
