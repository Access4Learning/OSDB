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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.domain.Solution;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.repository.DomainRepository;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.repository.SolutionRepository;
import org.sifunity.broker.environment.service.*;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Property-based test for Property 11: serviceType header validation.
 *
 * <p><strong>Property:</strong> For any request that includes a {@code serviceType}
 * header with a value other than {@code UTILITY} (case-insensitive), the service
 * SHALL return HTTP 400. Requests with {@code serviceType = UTILITY} or no
 * {@code serviceType} header SHALL be accepted.</p>
 *
 * <p>Tests at the controller layer using MockMvc since serviceType validation
 * is in the controller.</p>
 *
 * <p><strong>Validates: Requirements 11.6, 12.1, 12.2, 12.3</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class NamespaceServiceTypeHeaderPropertyTest {

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

    @Autowired
    private DomainRepository domainRepository;

    @MockBean
    private NamespaceEventPublisher namespaceEventPublisher;

    private final Random random = new Random();

    private String domainId;
    private String solutionId;
    private Environment adminEnvironment;

    @BeforeEach
    void setUp() {
        doNothing().when(namespaceEventPublisher)
                .publishNamespaceCreatedEvent(any(), anyString(), anyString());

        domainId = GlobalAdminConstants.GLOBAL_DOMAIN;
        solutionId = GlobalAdminConstants.GLOBAL_SOLUTION;

        try {
            domainService.createDomain(new DomainCreateRequest(domainId, null));
        } catch (Exception ignored) {
        }
        try {
            solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
        } catch (Exception ignored) {
        }

        adminEnvironment = createAdminEnvironment();
    }

    /**
     * Property 11: Non-UTILITY serviceType header returns 400.
     *
     * <p><strong>Validates: Requirements 11.6, 12.1, 12.2, 12.3</strong></p>
     */
    @RepeatedTest(3)
    @Tag("Feature: namespace-registry, Property 11: serviceType header validation")
    void serviceTypeHeaderValidation() throws Exception {
        String basePath = "/" + domainId + "/" + solutionId + "/namespaces";

        // Test 1: Random non-UTILITY header value → 400
        String invalidServiceType = randomNonUtilityServiceType();
        mockMvc.perform(get(basePath)
                        .header("serviceType", invalidServiceType)
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().isBadRequest());

        // Test 2: serviceType = UTILITY → accepted (200 or 204)
        mockMvc.perform(get(basePath)
                        .header("serviceType", "UTILITY")
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().is2xxSuccessful());

        // Test 3: No serviceType header → accepted (200 or 204)
        mockMvc.perform(get(basePath)
                        .requestAttr("requester", adminEnvironment))
                .andExpect(status().is2xxSuccessful());
    }

    private String randomNonUtilityServiceType() {
        String[] invalidValues = {
                "OBJECT", "FUNCTIONAL", "SERVICEPATH", "XQUERYTEMPLATE",
                "RICH", "NAMEDQUERY", "AGGREGATE", "utility_wrong",
                "object", "functional", "INVALID-" + random.nextInt(1000)
        };
        return invalidValues[random.nextInt(invalidValues.length)];
    }

    private Environment createAdminEnvironment() {
        UUID domainUuid = domainRepository.findByDomainId(domainId)
                .orElseThrow(() -> new RuntimeException("Domain not found: " + domainId))
                .getId();
        Solution solution = solutionRepository.findByDomainUuidAndSolutionId(domainUuid, solutionId)
                .orElseThrow(() -> new RuntimeException("Solution not found: " + solutionId));

        Environment env = Environment.builder()
                .applicationName("admin-ns-stype-app-" + System.nanoTime())
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
