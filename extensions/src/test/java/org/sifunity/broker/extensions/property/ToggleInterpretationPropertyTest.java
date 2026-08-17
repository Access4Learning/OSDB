// Feature: message-sequence-extension, Property 3: Toggle interpretation
package org.sifunity.broker.extensions.property;

import net.jqwik.api.*;
import org.sifunity.broker.extensions.config.SequencingProperties;
import org.sifunity.broker.extensions.sequencing.ToggleService;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property 3: Toggle interpretation — enabled iff tag value is exactly "true".
 *
 * <p>For any string value, {@link ToggleService} returns {@code true} iff value equals
 * {@code "true"}. For {@code null}, empty, {@code "false"}, {@code "TRUE"}, {@code "yes"},
 * {@code "1"}, or any arbitrary string, returns {@code false}.</p>
 *
 * <p><strong>Validates: Requirements 7.2, 7.3</strong></p>
 */
@PropertyDefaults(tries = 10)
class ToggleInterpretationPropertyTest {

    private static final String TEST_DOMAIN_ID = "test-domain";
    private static final String TEST_SOLUTION_ID = "test-solution-id";

    // ========== Helpers ==========

    private ToggleService createToggleService(RestTemplate restTemplate) {
        SequencingProperties properties = new SequencingProperties();
        properties.setEntityTagsBaseUrl("http://localhost:8080");
        properties.setToggleCacheTtl(Duration.ofMillis(0));
        return new ToggleService(restTemplate, properties);
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> nonTrueStrings() {
        return Arbitraries.oneOf(
                Arbitraries.of("false", "FALSE", "True", "TRUE", "yes", "YES", "no", "1", "0", "", " ", "null"),
                Arbitraries.strings().ofMinLength(0).ofMaxLength(50)
                        .filter(s -> !"true".equals(s))
        );
    }

    // ========== Properties ==========

    /**
     * When the entity-tags API returns a tag with value exactly "true", isSequencingEnabled SHALL return true.
     */
    @Example
    void exactlyTrueReturnsEnabled() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(anyString(), eq(ToggleService.EntityTagResult[].class)))
                .thenReturn(new ToggleService.EntityTagResult[]{new ToggleService.EntityTagResult("true")});

        ToggleService toggleService = createToggleService(restTemplate);

        assertThat(toggleService.isSequencingEnabled(TEST_DOMAIN_ID, TEST_SOLUTION_ID)).isTrue();
    }

    /**
     * For any string value that is NOT exactly "true", isSequencingEnabled SHALL return false.
     */
    @Property
    @Tag("Feature: message-sequence-extension, Property 3: Toggle interpretation")
    void nonTrueStringReturnsDisabled(@ForAll("nonTrueStrings") String tagValue) {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(anyString(), eq(ToggleService.EntityTagResult[].class)))
                .thenReturn(new ToggleService.EntityTagResult[]{new ToggleService.EntityTagResult(tagValue)});

        ToggleService toggleService = createToggleService(restTemplate);

        assertThat(toggleService.isSequencingEnabled(TEST_DOMAIN_ID, TEST_SOLUTION_ID)).isFalse();
    }

    /**
     * When the entity-tags API returns an empty array, isSequencingEnabled SHALL return false.
     */
    @Example
    void emptyArrayReturnsDisabled() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(anyString(), eq(ToggleService.EntityTagResult[].class)))
                .thenReturn(new ToggleService.EntityTagResult[]{});

        ToggleService toggleService = createToggleService(restTemplate);

        assertThat(toggleService.isSequencingEnabled(TEST_DOMAIN_ID, TEST_SOLUTION_ID)).isFalse();
    }

    /**
     * When the entity-tags API returns null, isSequencingEnabled SHALL return false.
     */
    @Example
    void nullResponseReturnsDisabled() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(anyString(), eq(ToggleService.EntityTagResult[].class)))
                .thenReturn(null);

        ToggleService toggleService = createToggleService(restTemplate);

        assertThat(toggleService.isSequencingEnabled(TEST_DOMAIN_ID, TEST_SOLUTION_ID)).isFalse();
    }
}
