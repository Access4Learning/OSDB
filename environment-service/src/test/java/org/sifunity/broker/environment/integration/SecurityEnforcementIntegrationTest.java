// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.GlobalAdminConstants;
import org.sifunity.broker.environment.dto.*;
import org.sifunity.broker.environment.test.config.TestSecurityConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for security enforcement on zone and context creation.
 *
 * <p>Verifies that non-admin environments receive 403 Forbidden when attempting
 * zone or context creation, while admin environments continue to get 201 Created.</p>
 *
 * <p><strong>Validates: Requirements 2.1, 2.2, 3.1, 3.2</strong></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "disable-security"})
@Import(TestSecurityConfiguration.class)
@Transactional
@DisplayName("Security Enforcement Integration Tests - Zone/Context Creation")
class SecurityEnforcementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String domainId;
    private String solutionId;
    private String basePath;

    @BeforeEach
    void setUp() throws Exception {
        domainId = "secdom" + System.currentTimeMillis();
        solutionId = "secsol" + System.currentTimeMillis();
        basePath = "/" + domainId + "/" + solutionId + "/zones";

        // Create domain and solution for test
        mockMvc.perform(post("/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new DomainCreateRequest(domainId, "Security test domain"))))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/" + domainId + "/solutions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SolutionCreateRequest(domainId, solutionId, "Security test solution"))))
            .andExpect(status().isCreated());
    }

    @Nested
    @DisplayName("Zone Creation - Non-Admin Rejection")
    class ZoneCreationNonAdminRejection {

        @Test
        @DisplayName("Non-admin environment (no fingerprint) gets 403 on zone creation")
        void nonAdminWithoutFingerprint_returns403() throws Exception {
            ZoneCreateRequestWrapper req = new ZoneCreateRequestWrapper(
                new ZoneCreateBody("testzone1", "Test zone"));

            mockMvc.perform(post(basePath + "/zone")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.subCode").value("ZONE_004"))
                .andExpect(jsonPath("$.error.message").value("Only admin environments can create zones"));
        }

        @Test
        @DisplayName("Non-admin environment (regular fingerprint) gets 403 on zone creation")
        void nonAdminWithRegularFingerprint_returns403() throws Exception {
            ZoneCreateRequestWrapper req = new ZoneCreateRequestWrapper(
                new ZoneCreateBody("testzone2", "Test zone"));

            mockMvc.perform(post(basePath + "/zone")
                    .header("X-Environment-Fingerprint", "consumer-app")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.subCode").value("ZONE_004"))
                .andExpect(jsonPath("$.error.message").value("Only admin environments can create zones"));
        }
    }

    @Nested
    @DisplayName("Zone Creation - Admin Allowed")
    class ZoneCreationAdminAllowed {

        @Test
        @DisplayName("Broker-services fingerprint gets 201 on zone creation")
        void brokerServicesFingerprint_returns201() throws Exception {
            ZoneCreateRequestWrapper req = new ZoneCreateRequestWrapper(
                new ZoneCreateBody("adminzone1", "Admin zone"));

            mockMvc.perform(post(basePath + "/zone")
                    .header("X-Environment-Fingerprint", "broker-services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.zone.id").value("adminzone1"));
        }

        @Test
        @DisplayName("Global admin domain/solution gets 201 on zone creation")
        void globalAdmin_returns201() throws Exception {
            // GLOBAL_DOMAIN/GLOBAL_SOLUTION already exist from bootstrap
            String adminDomainId = GlobalAdminConstants.GLOBAL_DOMAIN;
            String adminSolutionId = GlobalAdminConstants.GLOBAL_SOLUTION;

            String adminBasePath = "/" + adminDomainId + "/" + adminSolutionId + "/zones";
            String zoneId = "globalzone" + System.currentTimeMillis();
            ZoneCreateRequestWrapper req = new ZoneCreateRequestWrapper(
                new ZoneCreateBody(zoneId, "Global admin zone"));

            // Global admin path: isGlobalAdmin(GLOBAL_DOMAIN, GLOBAL_SOLUTION) == true
            mockMvc.perform(post(adminBasePath + "/zone")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.zone.id").value(zoneId));
        }
    }

    @Nested
    @DisplayName("Context Creation - Non-Admin Rejection")
    class ContextCreationNonAdminRejection {

        private String zoneId;

        @BeforeEach
        void setUpZone() throws Exception {
            zoneId = "ctxzone" + System.currentTimeMillis();
            ZoneCreateRequestWrapper zoneReq = new ZoneCreateRequestWrapper(
                new ZoneCreateBody(zoneId, "Zone for context tests"));

            mockMvc.perform(post(basePath + "/zone")
                    .header("X-Environment-Fingerprint", "broker-services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(zoneReq)))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Non-admin environment (no fingerprint) gets 403 on context creation")
        void nonAdminWithoutFingerprint_returns403() throws Exception {
            ContextCreateRequestWrapper req = new ContextCreateRequestWrapper(
                new ContextCreateBody("testctx1", "Test context"));
            String contextPath = basePath + "/" + zoneId + "/contexts/context";

            mockMvc.perform(post(contextPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.subCode").value("ZONE_004"))
                .andExpect(jsonPath("$.error.message").value("Only admin environments can create contexts"));
        }

        @Test
        @DisplayName("Non-admin environment (regular fingerprint) gets 403 on context creation")
        void nonAdminWithRegularFingerprint_returns403() throws Exception {
            ContextCreateRequestWrapper req = new ContextCreateRequestWrapper(
                new ContextCreateBody("testctx2", "Test context"));
            String contextPath = basePath + "/" + zoneId + "/contexts/context";

            mockMvc.perform(post(contextPath)
                    .header("X-Environment-Fingerprint", "provider-app")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.subCode").value("ZONE_004"))
                .andExpect(jsonPath("$.error.message").value("Only admin environments can create contexts"));
        }
    }

    @Nested
    @DisplayName("Context Creation - Admin Allowed")
    class ContextCreationAdminAllowed {

        private String zoneId;

        @BeforeEach
        void setUpZone() throws Exception {
            zoneId = "admctx" + System.currentTimeMillis();
            ZoneCreateRequestWrapper zoneReq = new ZoneCreateRequestWrapper(
                new ZoneCreateBody(zoneId, "Zone for admin context tests"));

            mockMvc.perform(post(basePath + "/zone")
                    .header("X-Environment-Fingerprint", "broker-services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(zoneReq)))
                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Broker-services fingerprint gets 201 on context creation")
        void brokerServicesFingerprint_returns201() throws Exception {
            ContextCreateRequestWrapper req = new ContextCreateRequestWrapper(
                new ContextCreateBody("adminctx1", "Admin context"));
            String contextPath = basePath + "/" + zoneId + "/contexts/context";

            mockMvc.perform(post(contextPath)
                    .header("X-Environment-Fingerprint", "broker-services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.context.id").value("adminctx1"));
        }

        @Test
        @DisplayName("Global admin domain/solution gets 201 on context creation")
        void globalAdmin_returns201() throws Exception {
            // GLOBAL_DOMAIN/GLOBAL_SOLUTION already exist from bootstrap
            String adminDomainId = GlobalAdminConstants.GLOBAL_DOMAIN;
            String adminSolutionId = GlobalAdminConstants.GLOBAL_SOLUTION;

            String adminBasePath = "/" + adminDomainId + "/" + adminSolutionId + "/zones";
            String adminZoneId = "gadmzone" + System.currentTimeMillis();

            mockMvc.perform(post(adminBasePath + "/zone")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new ZoneCreateRequestWrapper(new ZoneCreateBody(adminZoneId, "Global admin zone")))))
                .andExpect(status().isCreated());

            ContextCreateRequestWrapper req = new ContextCreateRequestWrapper(
                new ContextCreateBody("gadmctx1", "Global admin context"));
            mockMvc.perform(post(adminBasePath + "/" + adminZoneId + "/contexts/context")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.context.id").value("gadmctx1"));
        }
    }
}
