// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.domain.EntityType;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.SolutionDTO;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Property-based test for blank tag key validation using JUnit 5 RepeatedTest.
 *
 * <p>This test validates Property 7 from the design document:
 * "For any string composed entirely of whitespace (including the empty string),
 * attempting to create a tag with that string as the tagKey should be rejected
 * with HTTP 400."</p>
 *
 * <p>Uses MockMvc to test through the controller layer, verifying that the
 * {@code @NotBlank} validation on {@code EntityTagCreateRequest.tagKey} produces
 * an HTTP 400 Bad Request response.</p>
 *
 * <p><strong>Validates: Requirements 7.3</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
class EntityTagBlankKeyValidationPropertyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    private final Random random = new Random();

    private static final String[] BLANK_TAG_KEYS = {"", " ", "  ", "\t", "\n", "   \t  "};

    /**
     * Property 7: Blank Tag Key Validation
     *
     * <p><strong>Validates: Requirements 7.3</strong></p>
     *
     * <p>Creates a valid parent domain and solution, then attempts to POST a tag
     * with a randomly chosen blank/whitespace-only tagKey. Asserts that the
     * controller returns HTTP 400 Bad Request due to {@code @NotBlank} validation.</p>
     */
    @RepeatedTest(10)
    @Tag("entity-tags-property-7")
    void blankTagKeyRejection() throws Exception {
        // Set up parent domain and solution
        String domainId = generateValidDomainId();
        domainService.createDomain(new DomainCreateRequest(domainId, "Test domain for blank key validation"));

        String solutionId = generateValidSolutionId();
        solutionService.createSolution(
                new SolutionCreateRequest(domainId, solutionId, "Test solution for blank key validation"));

        // Generate random valid data for other fields
        EntityType entityType = generateRandomEntityType();
        UUID entityId = UUID.randomUUID();
        String tagValue = random.nextBoolean() ? generateValidTagValue() : null;

        // Randomly choose a blank/whitespace-only tagKey
        String blankTagKey = BLANK_TAG_KEYS[random.nextInt(BLANK_TAG_KEYS.length)];

        // Build request as a Map to control the JSON directly
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("entityType", entityType.name());
        requestMap.put("entityId", entityId.toString());
        requestMap.put("tagKey", blankTagKey);
        requestMap.put("tagValue", tagValue);

        // POST to the controller — @Valid + @NotBlank should reject with HTTP 400
        mockMvc.perform(post("/{domainId}/{solutionId}/tags", domainId, solutionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestMap)))
                .andExpect(status().isBadRequest());
    }

    // ========== Test Data Generators ==========

    private String generateValidDomainId() {
        String[] prefixes = {"alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        int number = random.nextInt(1000);
        long timestamp = System.nanoTime() % 10000;
        return prefix + "-" + number + "-" + timestamp;
    }

    private String generateValidSolutionId() {
        String[] environments = {"production", "staging", "testing", "development", "qa", "uat", "demo", "sandbox"};
        String environment = environments[random.nextInt(environments.length)];
        int number = random.nextInt(100);
        long timestamp = System.nanoTime() % 10000;
        return environment + "-" + number + "-" + timestamp;
    }

    private EntityType generateRandomEntityType() {
        EntityType[] values = EntityType.values();
        return values[random.nextInt(values.length)];
    }

    private String generateValidTagValue() {
        String[] values = {"true", "false", "gold", "silver", "bronze", "enabled", "disabled",
                "high", "medium", "low", "300", "600", "1200", "custom-value-" + random.nextInt(100)};
        return values[random.nextInt(values.length)];
    }
}
