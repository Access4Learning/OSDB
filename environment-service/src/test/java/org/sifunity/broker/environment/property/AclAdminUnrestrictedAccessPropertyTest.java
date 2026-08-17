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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Property 4: Admin unrestricted access.
 *
 * <p>For any admin environment and any target environment ID, calling
 * GET /acl-entries?environmentId={targetId} shall return HTTP 200 with
 * the ACL entries belonging to the target environment.</p>
 *
 * <p><strong>Validates: Requirements 3.1</strong></p>
 */
class AclAdminUnrestrictedAccessPropertyTest {

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
    Arbitrary<Environment> adminEnvironments() {
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
    @Tag("Feature: session-scoped-acl-listing, Property 4: Admin unrestricted access")
    void adminWithTargetIdReturns200WithTargetEntries(
            @ForAll("adminEnvironments") Environment admin,
            @ForAll("uuids") UUID targetId,
            @ForAll("aclEntryCounts") int entryCount) throws Exception {

        reset(aclService, authorizationService);

        List<AclEntryDTO> entries = generateEntries(targetId, entryCount);

        when(authorizationService.isAdmin(admin)).thenReturn(true);
        when(aclService.listAclEntries(targetId)).thenReturn(entries);

        MvcResult result = mockMvc.perform(get(BASE_PATH)
                        .param("environmentId", targetId.toString())
                        .requestAttr("requester", admin))
                .andExpect(status().isOk())
                .andReturn();

        List<AclEntryDTO> responseEntries = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<AclEntryDTO>>() {});

        assertThat(responseEntries).hasSize(entryCount);
        assertThat(responseEntries)
                .allSatisfy(entry ->
                        assertThat(entry.environmentId()).isEqualTo(targetId));
    }
}
