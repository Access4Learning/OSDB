// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sifunity.broker.environment.dto.XQueryParameterDTO;
import org.sifunity.broker.environment.dto.XQueryParametersDTO;
import org.sifunity.broker.environment.dto.XQueryTemplateBody;
import org.sifunity.broker.environment.dto.XQueryTemplateUpdateBody;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code xquery.returnType} must be an absolute URI.
 *
 * <p>The authoritative schema types it {@code type: string, format: uri}. A template was found in
 * the transcript environment carrying {@code "returnType": "void"}, which the conformance check
 * reported as {@code #/xquerys/xquery/0/returnType: [void] is not a valid URI}. The broker had
 * accepted it on create, so every subsequent read of that template returned a nonconformant body.</p>
 *
 * <p>Validating on the way in is the fix. Rejecting a value the broker cannot emit conformantly
 * stops one bad create from becoming a permanent response defect, and no amount of response-side
 * work can undo a value that is already stored.</p>
 */
@DisplayName("xquery returnType must be an absolute URI")
class XQueryReturnTypeUriFormatTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    @DisplayName("the reported case - returnType 'void' - is rejected on create")
    void rejectsTheReportedVoidCase() {
        XQueryTemplateBody body = createBody("void");

        assertThat(validator.validate(body))
                .as("'void' parses only as a relative reference, so it is not a URI")
                .anyMatch(v -> v.getPropertyPath().toString().equals("returnTypeAbsoluteUri"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "void",                     // the reported case
            "students",                 // bare word
            "3.3:students",             // looks scheme-ish but the scheme is not valid
            "/datamodel/na/3.3",        // absolute path, still a relative reference
            "://missing-scheme",        // malformed
            "http://has space/path"     // illegal character
    })
    @DisplayName("non-URI return types are rejected")
    void rejectsNonUriReturnTypes(String returnType) {
        assertThat(validator.validate(createBody(returnType)))
                .as("returnType '%s' is not an absolute URI", returnType)
                .anyMatch(v -> v.getPropertyPath().toString().equals("returnTypeAbsoluteUri"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // The schema's own example, from the returnType description
            "http://www.sifassociation.org/datamodel/na/3.3:students",
            // What the certification suite uses
            "http://www.sifassociation.org/datamodel/na/4.x/StudentPersonal",
            "https://example.org/datamodel/StudentPersonal",
            // urn: forms are legitimate here - this is a namespace identifier the broker never
            // dereferences, which is why absoluteness rather than http/https is the requirement
            "urn:sif:data/au/3.4.4",
            "urn:ietf:rfc:3986"
    })
    @DisplayName("valid absolute URIs are accepted")
    void acceptsAbsoluteUris(String returnType) {
        assertThat(validator.validate(createBody(returnType)))
                .as("returnType '%s' is an absolute URI and must be accepted", returnType)
                .noneMatch(v -> v.getPropertyPath().toString().equals("returnTypeAbsoluteUri"));
    }

    @Test
    @DisplayName("an omitted returnType on update means 'leave unchanged', not a violation")
    void updateToleratesOmittedReturnType() {
        XQueryTemplateUpdateBody update = new XQueryTemplateUpdateBody(
                null, "APPROVED", null, null, null, null, null);

        assertThat(validator.validate(update))
                .as("update semantics are partial - an absent field is preserved, not rejected")
                .isEmpty();
    }

    @Test
    @DisplayName("a supplied-but-invalid returnType on update is rejected")
    void updateRejectsInvalidReturnType() {
        XQueryTemplateUpdateBody update = new XQueryTemplateUpdateBody(
                null, null, null, null, null, null, "void");

        assertThat(validator.validate(update))
                .as("an update can store an unemittable value just as easily as a create")
                .anyMatch(v -> v.getPropertyPath().toString().equals("returnTypeAbsoluteUri"));
    }

    /**
     * Blank is not absent on update, and this is the difference from create.
     *
     * <p>{@code XQueryTemplateService.update} writes any non-null value, so an empty string clears a
     * field that create requires. It is reachable from the admin console: open a template, clear the
     * Return Type box, save — the edit panel sends the trimmed contents whether or not it is empty.
     * Create has {@code @NotBlank} to fall back on; update has nothing, so the constraint itself has
     * to reject blank.</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("a blank returnType on update is rejected, since blank is not absent")
    void updateRejectsBlankReturnType(String returnType) {
        XQueryTemplateUpdateBody update = new XQueryTemplateUpdateBody(
                null, null, null, null, null, null, returnType);

        assertThat(validator.validate(update))
                .as("blank clears a required field, leaving a template that cannot be emitted "
                        + "conformantly; only this constraint can catch it")
                .anyMatch(v -> v.getPropertyPath().toString().equals("returnTypeAbsoluteUri"));
    }

    @Test
    @DisplayName("a blank returnType on create is left to @NotBlank, not double-reported")
    void createLeavesBlankReturnTypeToNotBlank() {
        assertThat(validator.validate(createBody("")))
                .as("create already requires returnType, so the URI constraint stays quiet and the "
                        + "client gets one clear message rather than two")
                .anyMatch(v -> v.getPropertyPath().toString().equals("returnType"))
                .noneMatch(v -> v.getPropertyPath().toString().equals("returnTypeAbsoluteUri"));
    }

    @Test
    @DisplayName("a valid absolute returnType on update is accepted")
    void updateAcceptsAbsoluteReturnType() {
        XQueryTemplateUpdateBody update = new XQueryTemplateUpdateBody(
                null, null, null, null, null, null,
                "http://www.sifassociation.org/datamodel/na/3.3:students");

        assertThat(validator.validate(update))
                .as("positive control - the update constraint must not reject everything")
                .isEmpty();
    }

    /**
     * A create body that is valid apart from the returnType under test, so a violation can only
     * come from the URI constraint.
     */
    private static XQueryTemplateBody createBody(String returnType) {
        return new XQueryTemplateBody(
                "test",
                "SINGULAR",
                "APPROVED",
                null,
                "A description",
                "let $x := 1 return $x",
                new XQueryParametersDTO(List.of(
                        new XQueryParameterDTO("p1", "string", null, "param 1", false))),
                returnType);
    }
}
