// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import org.sifunity.broker.environment.controller.AclController;
import org.sifunity.broker.environment.controller.GlobalExceptionHandler;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.service.AclService;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Property 6: Error responses conform to SifErrorResponse format.
 *
 * <p>For any request that triggers a 403 (non-admin cross-environment) or
 * 400 (admin missing environmentId) error, the response body shall contain
 * non-null {@code code}, {@code message}, {@code scope}, and {@code timestamp}
 * fields conforming to the SifErrorResponse record.</p>
 *
 * <p><strong>Validates: Requirements 5.1, 5.2</strong></p>
 */
class AclErrorResponseFormatPropertyTest {

    private final AclService aclService = mock(AclService.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final AclController controller = new AclController(aclService, authorizationService);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String BASE_PATH = "/test-domain/test-solution/acl-entries";

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<Environment> environments() {
        return uuids().map(id -> {
            Environment env = new Environment();
            env.setId(id);
            env.setSolutionUuid(UUID.randomUUID());
            return env;
        });
    }

    private void assertSifErrorResponseFormat(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.has("error")).isTrue();
        JsonNode body = root.get("error");
        assertThat(body.has("id")).isTrue();
        assertThat(body.get("id").isNull()).isFalse();
        assertThat(body.has("code")).isTrue();
        assertThat(body.get("code").isNull()).isFalse();
        assertThat(body.has("subCode")).isTrue();
        assertThat(body.get("subCode").isNull()).isFalse();
        assertThat(body.has("message")).isTrue();
        assertThat(body.get("message").isNull()).isFalse();
        assertThat(body.has("scope")).isTrue();
        assertThat(body.get("scope").isNull()).isFalse();
        assertThat(body.has("timestamp"))
                .as("SIF 3.7 errorCommon does not include 'timestamp' — must be absent from wire")
                .isFalse();
    }

    @Property(tries = 10)
    @Tag("Feature: session-scoped-acl-listing, Property 6: Error responses conform to SifErrorResponse format")
    void forbiddenResponseConformsToSifErrorFormat(
            @ForAll("environments") Environment requester,
            @ForAll("uuids") UUID targetId) throws Exception {

        reset(aclService, authorizationService);

        // Ensure mismatch for 403
        Assume.that(!targetId.equals(requester.getId()));

        when(authorizationService.isAdmin(requester)).thenReturn(false);

        MvcResult result = mockMvc.perform(get(BASE_PATH)
                        .param("environmentId", targetId.toString())
                        .requestAttr("requester", requester))
                .andExpect(status().isForbidden())
                .andReturn();

        assertSifErrorResponseFormat(result);
    }

    @Property(tries = 10)
    @Tag("Feature: session-scoped-acl-listing, Property 6: Error responses conform to SifErrorResponse format")
    void badRequestResponseConformsToSifErrorFormat(
            @ForAll("environments") Environment admin) throws Exception {

        reset(aclService, authorizationService);

        when(authorizationService.isAdmin(admin)).thenReturn(true);

        MvcResult result = mockMvc.perform(get(BASE_PATH)
                        .requestAttr("requester", admin))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertSifErrorResponseFormat(result);
    }
}
