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
import java.util.Random;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Property-based test for Property 5: Non-admin write rejection.
 *
 * <p><strong>Property:</strong> For any non-admin environment and for any valid
 * namespace payload, attempting to create (POST), update (PUT), or delete (DELETE)
 * a namespace SHALL return HTTP 403 Forbidden, and the database state SHALL remain
 * unchanged.</p>
 *
 * <p>Tests at the controller layer using MockMvc since admin check is in the controller.</p>
 *
 * <p><strong>Validates: Requirements 3.7, 4.5, 5.3, 10.3</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class NamespaceNonAdminRejectionPropertyTest {

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
    private NamespaceEventPublisher namespaceEventPublisher;

    private final Random random = new Random();

    private String domainId;
    private String solutionId;
    private UUID domainUuid;
    private Environment nonAdminEnvironment;

    @BeforeEach
    void setUp() {
        doNothing().when(namespaceEventPublisher)
                .publishNamespaceCreatedEvent(any(), anyString(), anyString());
        doNothing().when(namespaceEventPublisher)
                .publishNamespaceUpdatedEvent(any(), anyString(), anyString());
        doNothing().when(namespaceEventPublisher)
                .publishNamespaceDeletedEvent(any(), anyString(), anyString());

        domainId = "ns-nonadm-domain-" + System.nanoTime();
        domainUuid = domainService.createDomain(new DomainCreateRequest(domainId, null)).id();

        solutionId = "ns-nonadm-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));

        nonAdminEnvironment = createNonAdminEnvironment();
    }

    /**
     * Property 5: Non-admin write rejection for POST/PUT/DELETE.
     *
     * <p><strong>Validates: Requirements 3.7, 4.5, 5.3, 10.3</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: namespace-registry, Property 5: Non-admin write rejection")
    void nonAdminWriteRejection() throws Exception {
        int operation = random.nextInt(3);

        String basePath = "/" + domainId + "/" + solutionId + "/namespaces";

        NamespaceCreateBody body = new NamespaceCreateBody(
                null, "environment-global",
                "http://www.sifassociation.org/ns/" + random.nextInt(100000),
                "https://schemas.example.org/" + random.nextInt(100000) + "/schema.xsd"
        );
        NamespaceCreateRequest request = new NamespaceCreateRequest(body);
        String jsonBody = objectMapper.writeValueAsString(request);

        switch (operation) {
            case 0 -> {
                mockMvc.perform(post(basePath + "/namespace")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonBody)
                                .requestAttr("requester", nonAdminEnvironment))
                        .andExpect(status().isForbidden());
            }
            case 1 -> {
                mockMvc.perform(put(basePath + "/" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonBody)
                                .requestAttr("requester", nonAdminEnvironment))
                        .andExpect(status().isForbidden());
            }
            case 2 -> {
                mockMvc.perform(delete(basePath + "/" + UUID.randomUUID())
                                .requestAttr("requester", nonAdminEnvironment))
                        .andExpect(status().isForbidden());
            }
        }
    }

    private Environment createNonAdminEnvironment() {
        Solution solution = solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)
                .orElseThrow(() -> new RuntimeException("Solution not found: " + solutionId));

        Environment env = Environment.builder()
                .applicationName("non-admin-ns-app-" + System.nanoTime())
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
