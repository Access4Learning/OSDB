// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.environment.domain.TagDefinition;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for TagDefinition entity tagName uppercase invariant.
 *
 * <p><strong>Property 1: TagName uppercase invariant</strong></p>
 *
 * <p>For any string provided as tagName in a create request, the persisted
 * TagDefinition's tagName SHALL equal the input string converted to uppercase.</p>
 *
 * <p><strong>Validates: Requirements 2.4, 3.1</strong></p>
 *
 * <p>This test exercises the {@code @PrePersist} and {@code @PreUpdate} lifecycle
 * hooks on the TagDefinition entity via reflection to verify that tagName is always
 * uppercased regardless of the input casing. The lifecycle hooks are the mechanism
 * that ensures the uppercase invariant holds for all persisted entities.</p>
 */
class TagNameUppercaseInvariantPropertyTest {

    private static final Method ON_CREATE;
    private static final Method ON_UPDATE;

    static {
        try {
            ON_CREATE = TagDefinition.class.getDeclaredMethod("onCreate");
            ON_CREATE.setAccessible(true);
            ON_UPDATE = TagDefinition.class.getDeclaredMethod("onUpdate");
            ON_UPDATE.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to access TagDefinition lifecycle methods", e);
        }
    }

    /**
     * Property 1: TagName uppercase invariant — @PrePersist
     *
     * <p><strong>Validates: Requirements 2.4, 3.1</strong></p>
     *
     * <p>For any non-null string set as tagName, after {@code onCreate()} executes
     * (simulating JPA @PrePersist), the tagName SHALL equal the input uppercased.</p>
     */
    @Property(tries = 20)
    @Tag("tag-management-admin")
    @Tag("property-1-tagname-uppercase-invariant")
    void tagNameIsUppercasedOnPrePersist(@ForAll("tagNames") String inputTagName) throws Exception {
        TagDefinition entity = new TagDefinition();
        entity.setTagName(inputTagName);
        entity.setApplicableEntityTypes(List.of("SOLUTION"));
        entity.setCreatedBy("test-user");

        // Invoke @PrePersist lifecycle callback
        ON_CREATE.invoke(entity);

        assertThat(entity.getTagName())
            .as("tagName must equal input uppercased after @PrePersist")
            .isEqualTo(inputTagName.toUpperCase());
    }

    /**
     * Property 1: TagName uppercase invariant — @PreUpdate
     *
     * <p><strong>Validates: Requirements 2.4, 3.1</strong></p>
     *
     * <p>For any non-null string set as tagName, after {@code onUpdate()} executes
     * (simulating JPA @PreUpdate), the tagName SHALL equal the input uppercased.</p>
     */
    @Property(tries = 20)
    @Tag("tag-management-admin")
    @Tag("property-1-tagname-uppercase-invariant")
    void tagNameIsUppercasedOnPreUpdate(@ForAll("tagNames") String inputTagName) throws Exception {
        TagDefinition entity = new TagDefinition();
        entity.setTagName("INITIAL_VALUE");
        entity.setApplicableEntityTypes(List.of("ENVIRONMENT"));
        entity.setCreatedBy("test-user");

        // Simulate initial persist
        ON_CREATE.invoke(entity);

        // Change tagName and invoke @PreUpdate
        entity.setTagName(inputTagName);
        ON_UPDATE.invoke(entity);

        assertThat(entity.getTagName())
            .as("tagName must equal input uppercased after @PreUpdate")
            .isEqualTo(inputTagName.toUpperCase());
    }

    /**
     * Property 1: TagName uppercase invariant — idempotence
     *
     * <p><strong>Validates: Requirements 2.4, 3.1</strong></p>
     *
     * <p>Applying the uppercase transformation multiple times (persist then update)
     * SHALL produce the same result as applying it once.</p>
     */
    @Property(tries = 20)
    @Tag("tag-management-admin")
    @Tag("property-1-tagname-uppercase-invariant")
    void uppercaseTransformationIsIdempotent(@ForAll("tagNames") String inputTagName) throws Exception {
        TagDefinition entity = new TagDefinition();
        entity.setTagName(inputTagName);
        entity.setApplicableEntityTypes(List.of("QUEUE"));
        entity.setCreatedBy("test-user");

        // Apply @PrePersist
        ON_CREATE.invoke(entity);
        String afterPersist = entity.getTagName();

        // Apply @PreUpdate on already-uppercased value
        ON_UPDATE.invoke(entity);
        String afterUpdate = entity.getTagName();

        assertThat(afterUpdate)
            .as("Uppercase transformation must be idempotent")
            .isEqualTo(afterPersist)
            .isEqualTo(inputTagName.toUpperCase());
    }

    // ========== Arbitraries ==========

    /**
     * Generates tag name strings covering various casing patterns:
     * lowercase, uppercase, mixed case, with underscores and digits.
     */
    @Provide
    Arbitrary<String> tagNames() {
        // Alphanumeric + underscore characters typical for tag names
        Arbitrary<String> alphaNumericTags = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('_')
            .ofMinLength(1)
            .ofMaxLength(50);

        // Pure lowercase to stress the uppercase conversion
        Arbitrary<String> lowercaseTags = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withChars('_')
            .ofMinLength(1)
            .ofMaxLength(30);

        // Mixed case with special patterns
        Arbitrary<String> mixedCaseTags = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withChars('_')
            .ofMinLength(1)
            .ofMaxLength(40);

        return Arbitraries.oneOf(alphaNumericTags, lowercaseTags, mixedCaseTags);
    }
}
