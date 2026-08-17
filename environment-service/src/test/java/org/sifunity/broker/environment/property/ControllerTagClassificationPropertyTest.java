// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 1: Controller Tag Classification Completeness.
 *
 * <p>For any REST controller class in the environment-service, that controller SHALL have
 * exactly one @Tag annotation with name equal to either "SIF API" or "Internal API".</p>
 *
 * <p><strong>Validates: Requirements 4.1</strong></p>
 */
class ControllerTagClassificationPropertyTest {

    private static final String BASE_PACKAGE = "org.sifunity.broker.environment";
    private static final Set<String> VALID_TAG_NAMES = Set.of("SIF API", "Internal API");

    @Test
    void everyRestControllerHasExactlyOneValidTagAnnotation() {
        // Scan classpath for all @RestController classes in the environment-service package
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<BeanDefinition> candidates = scanner.findCandidateComponents(BASE_PACKAGE);

        assertThat(candidates)
                .as("Should find at least one @RestController in package %s", BASE_PACKAGE)
                .isNotEmpty();

        List<String> violations = new ArrayList<>();

        for (BeanDefinition bd : candidates) {
            String className = bd.getBeanClassName();
            Class<?> controllerClass;
            try {
                controllerClass = Class.forName(className);
            } catch (ClassNotFoundException e) {
                violations.add(className + " - could not load class");
                continue;
            }

            // Get all @Tag annotations on the class
            Tag[] tags = controllerClass.getAnnotationsByType(Tag.class);

            if (tags.length == 0) {
                violations.add(className + " - missing @Tag annotation");
            } else if (tags.length > 1) {
                violations.add(className + " - has " + tags.length + " @Tag annotations (expected exactly 1)");
            } else {
                String tagName = tags[0].name();
                if (!VALID_TAG_NAMES.contains(tagName)) {
                    violations.add(className + " - @Tag name is \"" + tagName
                            + "\" (expected \"SIF API\" or \"Internal API\")");
                }
            }
        }

        assertThat(violations)
                .as("All @RestController classes must have exactly one @Tag with name 'SIF API' or 'Internal API'")
                .isEmpty();
    }
}
