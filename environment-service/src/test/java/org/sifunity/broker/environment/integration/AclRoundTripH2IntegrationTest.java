// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.dto.AclEntryDTO;
import org.sifunity.broker.environment.dto.AclEntryCreateRequest;
import org.sifunity.broker.environment.dto.ContextServiceDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.service.AclService;
import org.sifunity.broker.environment.service.ContextServiceService;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Integration test for ACL create/list round-trip with real H2 database.
 *
 * <p>Verifies that creating an ACL entry via the external API (serviceName + contextUuid)
 * and listing it back returns the original fields, and that a context_service record
 * was auto-created in the database.</p>
 *
 * <p><strong>Validates: Requirements 6.1, 6.2, 6.4</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AclRoundTripH2IntegrationTest {

    @Autowired
    private AclService aclService;

    @Autowired
    private ContextServiceService contextServiceService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    @Autowired
    private ZoneService zoneService;

    private TestEntityHierarchy hierarchy;
    private UUID environmentId;

    @BeforeEach
    void setUp() {
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);

        SifEnvironmentRegistrationResponse envResponse = environmentService.registerEnvironment(hierarchy.getDomainId(),
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("AclRoundTripApp-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null)));
        environmentId = envResponse.environment().id();
    }

    /**
     * Create ACL entry via API with serviceName + contextUuid, verify response preserves fields.
     * Requirements: 6.1
     */
    @Test
    void createAclEntry_responseContainsServiceNameAndContextUuid() {
        AclEntryCreateRequest request = new AclEntryCreateRequest(
                environmentId, "StudentService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.QUERY, AccessLevel.APPROVED);

        AclEntryDTO created = aclService.createAclEntry(request);

        assertThat(created.id()).isNotNull();
        assertThat(created.environmentId()).isEqualTo(environmentId);
        assertThat(created.serviceName()).isEqualTo("StudentService");
        assertThat(created.zoneId()).isEqualTo(hierarchy.getZoneId());
        assertThat(created.accessType()).isEqualTo(AccessType.QUERY);
        assertThat(created.accessLevel()).isEqualTo(AccessLevel.APPROVED);
    }

    /**
     * List ACL entries after creation, verify response contains serviceName + contextUuid.
     * Requirements: 6.2
     */
    @Test
    void listAclEntries_responseContainsServiceNameAndContextUuid() {
        aclService.createAclEntry(new AclEntryCreateRequest(
                environmentId, "TeacherService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.SUBSCRIBE, AccessLevel.APPROVED));

        List<AclEntryDTO> entries = aclService.listAclEntries(environmentId);

        assertThat(entries).hasSize(1);
        AclEntryDTO entry = entries.get(0);
        assertThat(entry.serviceName()).isEqualTo("TeacherService");
        assertThat(entry.zoneId()).isEqualTo(hierarchy.getZoneId());
    }

    /**
     * Verify context_service was auto-created when ACL entry was created.
     * Requirements: 6.4
     */
    @Test
    void createAclEntry_autoCreatesContextService() {
        String serviceName = "AutoCreatedService";

        // Before ACL creation, no context_service should exist for this name
        List<ContextServiceDTO> before = contextServiceService.listByContext(hierarchy.getContextUuid());
        assertThat(before).extracting(ContextServiceDTO::serviceName)
                .doesNotContain(serviceName);

        // Create ACL entry
        aclService.createAclEntry(new AclEntryCreateRequest(
                environmentId, serviceName, hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.QUERY, AccessLevel.APPROVED));

        // After ACL creation, context_service should exist
        List<ContextServiceDTO> after = contextServiceService.listByContext(hierarchy.getContextUuid());
        assertThat(after).extracting(ContextServiceDTO::serviceName)
                .contains(serviceName);
    }

    /**
     * Multiple ACL entries for same service reuse the same context_service record.
     * Requirements: 6.4
     */
    @Test
    void multipleAclEntries_sameSevice_reuseContextService() {
        // Create two ACL entries for same service but different access types
        aclService.createAclEntry(new AclEntryCreateRequest(
                environmentId, "SharedService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.QUERY, AccessLevel.APPROVED));
        aclService.createAclEntry(new AclEntryCreateRequest(
                environmentId, "SharedService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.SUBSCRIBE, AccessLevel.APPROVED));

        // Only one context_service should exist for this service name
        List<ContextServiceDTO> services = contextServiceService.listByContext(hierarchy.getContextUuid());
        long count = services.stream()
                .filter(cs -> "SharedService".equals(cs.serviceName()))
                .count();
        assertThat(count).isEqualTo(1);
    }
}
