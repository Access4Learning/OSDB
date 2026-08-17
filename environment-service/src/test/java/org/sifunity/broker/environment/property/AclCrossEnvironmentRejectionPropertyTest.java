// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.environment.controller.AclController;
import org.sifunity.broker.environment.controller.GlobalExceptionHandler;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.service.AclService;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Property 2: Non-admin cross-environment access is rejected.
 *
 * <p>For any non-admin environment and any environmentId parameter value that
 * differs from the requester's own environment ID, the controller shall return
 * HTTP 403 Forbidden.</p>
 *
 * <p><strong>Validates: Requirements 2.1</strong></p>
 */
class AclCrossEnvironmentRejectionPropertyTest {

    private final AclService aclService = mock(AclService.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final AclController controller = new AclController(aclService, authorizationService);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private static final String BASE_PATH = "/test-domain/test-solution/acl-entries";

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<Environment> nonAdminEnvironments() {
        return uuids().map(id -> {
            Environment env = new Environment();
            env.setId(id);
            env.setSolutionUuid(UUID.randomUUID());
            return env;
        });
    }

    @Property(tries = 10)
    @Tag("Feature: session-scoped-acl-listing, Property 2: Non-admin cross-environment access is rejected")
    void nonAdminWithMismatchedIdReturns403(
            @ForAll("nonAdminEnvironments") Environment requester,
            @ForAll("uuids") UUID targetId) throws Exception {

        reset(aclService, authorizationService);

        // Ensure target differs from requester
        Assume.that(!targetId.equals(requester.getId()));

        when(authorizationService.isAdmin(requester)).thenReturn(false);

        mockMvc.perform(get(BASE_PATH)
                        .param("environmentId", targetId.toString())
                        .requestAttr("requester", requester))
                .andExpect(status().isForbidden());
    }
}
