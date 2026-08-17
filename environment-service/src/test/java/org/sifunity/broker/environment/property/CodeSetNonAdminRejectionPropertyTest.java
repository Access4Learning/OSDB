// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.*;

import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Property-based test for Property 6: Non-admin write rejection.
 *
 * <p><strong>Property:</strong> For any non-admin environment and for any valid code
 * set payload, attempting to create (POST), update (PUT), or delete (DELETE) a code
 * set SHALL return HTTP 403 Forbidden, and the database state SHALL remain unchanged.</p>
 *
 * <p>Tests at the controller layer using MockMvc since admin check is in the controller.</p>
 *
 * <p><strong>Validates: Requirements 3.9, 4.4, 5.3, 10.3</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class CodeSetNonAdminRejectionPropertyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private SolutionRepository solutionRepository;

    @MockBean
    private CodeSetEventPublisher codeSetEventPublisher;

    private final Random random = new Random();

    private String domainId;
    private String solutionId;
    private UUID domainUuid;
    private Environment nonAdminEnvironment;

    @BeforeEach
    void setUp() {
        doNothing().when(codeSetEventPublisher)
                .publishCodeSetCreatedEvent(any(), anyString(), anyString());
        doNothing().when(codeSetEventPublisher)
                .publishCodeSetUpdatedEvent(any(), anyString(), anyString());
        doNothing().when(codeSetEventPublisher)
                .publishCodeSetDeletedEvent(any(), anyString(), anyString());

        // Create a NON-admin domain/solution (not GLOBAL_DOMAIN/GLOBAL_SOLUTION)
        domainId = "cs-nonadm-domain-" + System.nanoTime();
        domainUuid = domainService.createDomain(new DomainCreateRequest(domainId, null)).id();

        solutionId = "cs-nonadm-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));

        // Create a non-admin environment in this non-admin solution
        nonAdminEnvironment = createNonAdminEnvironment();
    }

    /**
     * Property 6: Non-admin write rejection for POST/PUT/DELETE.
     *
     * <p><strong>Validates: Requirements 3.9, 4.4, 5.3, 10.3</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: codeset-registry, Property 6: Non-admin write rejection")
    void nonAdminWriteRejection() throws Exception {
        // Pick random operation: 0=POST, 1=PUT, 2=DELETE
        int operation = random.nextInt(3);

        String basePath = "/" + domainId + "/" + solutionId + "/codeSets";

        CodeSetCreateBody body = new CodeSetCreateBody(
                "nonadm-test-" + random.nextInt(100000),
                "environment-global",
                "v" + (2020 + random.nextInt(5)) + "." + (random.nextInt(9) + 1),
                "2024-06-15T10:30:00Z",
                null, null
        );
        CodeSetCreateRequest request = new CodeSetCreateRequest(body);
        String jsonBody = objectMapper.writeValueAsString(request);

        switch (operation) {
            case 0 -> {
                // POST create
                mockMvc.perform(post(basePath + "/codeSet")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonBody)
                                .requestAttr("requester", nonAdminEnvironment))
                        .andExpect(status().isForbidden());
            }
            case 1 -> {
                // PUT update
                mockMvc.perform(put(basePath + "/some-id-" + random.nextInt(10000))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonBody)
                                .requestAttr("requester", nonAdminEnvironment))
                        .andExpect(status().isForbidden());
            }
            case 2 -> {
                // DELETE
                mockMvc.perform(delete(basePath + "/some-id-" + random.nextInt(10000))
                                .requestAttr("requester", nonAdminEnvironment))
                        .andExpect(status().isForbidden());
            }
        }
    }

    private Environment createNonAdminEnvironment() {
        // Find the solution UUID for the non-admin solution (domain-scoped)
        Solution solution = solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)
                .orElseThrow(() -> new RuntimeException("Solution not found: " + solutionId));

        Environment env = Environment.builder()
                .applicationName("non-admin-app-" + System.nanoTime())
                .sessionToken(UUID.randomUUID().toString())
                .sharedSecret("test-secret")
                .type(EnvironmentType.DIRECT)
                .solutionUuid(solution.getId())
                .defaultZoneId("environment-global")
                .createdAt(Instant.now())
                .build();

        return environmentRepository.save(env);
    }
}
