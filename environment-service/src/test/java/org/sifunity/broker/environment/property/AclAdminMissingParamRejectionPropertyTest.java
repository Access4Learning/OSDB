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
 * Property 5: Admin missing environmentId is rejected.
 *
 * <p>For any admin environment making a GET request to /acl-entries without
 * an environmentId parameter, the controller shall return HTTP 400 Bad Request.</p>
 *
 * <p><strong>Validates: Requirements 3.2</strong></p>
 */
class AclAdminMissingParamRejectionPropertyTest {

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
    Arbitrary<Environment> adminEnvironments() {
        return uuids().map(id -> {
            Environment env = new Environment();
            env.setId(id);
            env.setSolutionUuid(UUID.randomUUID());
            return env;
        });
    }

    @Property(tries = 10)
    @Tag("Feature: session-scoped-acl-listing, Property 5: Admin missing environmentId is rejected")
    void adminWithoutEnvironmentIdReturns400(
            @ForAll("adminEnvironments") Environment admin) throws Exception {

        reset(aclService, authorizationService);

        when(authorizationService.isAdmin(admin)).thenReturn(true);

        mockMvc.perform(get(BASE_PATH)
                        .requestAttr("requester", admin))
                .andExpect(status().isBadRequest());
    }
}
