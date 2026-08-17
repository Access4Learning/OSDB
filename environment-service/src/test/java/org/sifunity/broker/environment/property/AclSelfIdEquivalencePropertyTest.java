// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.dto.AclEntryDTO;
import org.sifunity.broker.environment.controller.AclController;
import org.sifunity.broker.environment.controller.GlobalExceptionHandler;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.service.AclService;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Property 3: Non-admin explicit self-ID is equivalent to omission.
 *
 * <p>For any non-admin environment, calling GET /acl-entries?environmentId={requester.getId()}
 * shall produce the same 200 OK response as calling GET /acl-entries without the parameter.</p>
 *
 * <p><strong>Validates: Requirements 2.2</strong></p>
 */
class AclSelfIdEquivalencePropertyTest {

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

    @Provide
    Arbitrary<Integer> aclEntryCounts() {
        return Arbitraries.integers().between(0, 5);
    }

    private List<AclEntryDTO> generateEntries(UUID environmentId, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new AclEntryDTO(
                        UUID.randomUUID(), environmentId, "Service" + i,
                        null, null, AccessType.QUERY, AccessLevel.APPROVED))
                .toList();
    }

    @Property(tries = 10)
    @Tag("Feature: session-scoped-acl-listing, Property 3: Non-admin explicit self-ID is equivalent to omission")
    void explicitSelfIdProducesSameResultAsOmission(
            @ForAll("nonAdminEnvironments") Environment requester,
            @ForAll("aclEntryCounts") int entryCount) throws Exception {

        reset(aclService, authorizationService);

        // Use a fixed list so both calls return the same data
        List<AclEntryDTO> entries = generateEntries(requester.getId(), entryCount);

        when(authorizationService.isAdmin(requester)).thenReturn(false);
        when(aclService.listAclEntries(requester.getId())).thenReturn(entries);

        // Call without environmentId
        MvcResult withoutParam = mockMvc.perform(get(BASE_PATH)
                        .requestAttr("requester", requester))
                .andExpect(status().isOk())
                .andReturn();

        // Call with own environmentId
        MvcResult withOwnId = mockMvc.perform(get(BASE_PATH)
                        .param("environmentId", requester.getId().toString())
                        .requestAttr("requester", requester))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(withOwnId.getResponse().getContentAsString())
                .isEqualTo(withoutParam.getResponse().getContentAsString());
    }
}
