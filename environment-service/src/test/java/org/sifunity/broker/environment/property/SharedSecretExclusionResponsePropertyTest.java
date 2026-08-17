// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.Arbitrary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Property 3: SharedSecret Exclusion from Non-Registration Responses.
 *
 * <p>For any registered environment, GET response JSON does not contain sharedSecret.</p>
 *
 * <p>Tag: Feature: sif-environment-response, Property 3: SharedSecret Exclusion</p>
 *
 * <p><strong>Validates: Requirements 5.6, 6.3</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Transactional
class SharedSecretExclusionResponsePropertyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    private TestEntityHierarchy hierarchy;

    @BeforeEach
    void setUp() {
        hierarchy = TestEntityHierarchy.create(domainService, solutionService);
    }

    private static final Arbitrary<EnvironmentRegistrationRequest> REQUESTS =
            SifEnvironmentResponseArbitraries.validRegistrationRequests();

    @RepeatedTest(10)
    @Tag("Feature: sif-environment-response, Property 3: SharedSecret Exclusion")
    void getResponseJsonDoesNotContainSharedSecret() throws Exception {
        EnvironmentRegistrationRequest request = REQUESTS.sample();

        // Register via POST using hierarchy-provided domain/solution
        String requestJson = objectMapper.writeValueAsString(request);
        MvcResult postResult = mockMvc.perform(post(hierarchy.resourceBasePath() + "/environments/environment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();

        String postBody = postResult.getResponse().getContentAsString();
        // POST response SHOULD contain sharedSecret (inside environment wrapper)
        assertThat(postBody).contains("sharedSecret");

        // Extract id from POST response (wrapped under "environment" key)
        String id = objectMapper.readTree(postBody).get("environment").get("id").asText();

        // GET by ID
        MvcResult getResult = mockMvc.perform(get(hierarchy.resourceBasePath() + "/environments/" + id))
                .andExpect(status().isOk())
                .andReturn();

        String getBody = getResult.getResponse().getContentAsString();
        // GET response must NOT contain sharedSecret
        assertThat(getBody).doesNotContain("sharedSecret");
    }
}
