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
import org.sifunity.broker.common.exception.SifBrokerException;
import org.sifunity.broker.environment.dto.AclEntryCreateRequest;
import org.sifunity.broker.environment.dto.ContextServiceCreateRequest;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Integration test for single-provider constraint enforcement at the service layer.
 *
 * <p>Verifies that only one environment can have a PROVIDE ACL entry for a given
 * context_service. A second PROVIDE attempt from a different environment is rejected
 * with a conflict error.</p>
 *
 * <p><strong>Validates: Requirements 4.2, 4.3</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SingleProviderConstraintH2IntegrationTest {

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
    private UUID environment1Id;
    private UUID environment2Id;

    @BeforeEach
    void setUp() {
        hierarchy = TestEntityHierarchy.createFull(domainService, solutionService, zoneService);

        // Register two environments
        SifEnvironmentRegistrationResponse env1Response = environmentService.registerEnvironment(
                hierarchy.getDomainId(),
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("SingleProviderEnv1-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null)));
        environment1Id = env1Response.environment().id();

        SifEnvironmentRegistrationResponse env2Response = environmentService.registerEnvironment(
                hierarchy.getDomainId(),
                new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("SingleProviderEnv2-" + System.currentTimeMillis(), EnvironmentType.BROKERED, new DefaultZoneDTO(hierarchy.getZoneId()), hierarchy.getSolutionId(), null)));
        environment2Id = env2Response.environment().id();
    }

    /**
     * First PROVIDE ACL entry for a context_service succeeds.
     * Requirements: 4.2
     */
    @Test
    void firstProvideAcl_succeeds() {
        AclEntryDTO created = aclService.createAclEntry(new AclEntryCreateRequest(
                environment1Id, "ExclusiveService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.PROVIDE, AccessLevel.APPROVED));

        assertThat(created).isNotNull();
        assertThat(created.accessType()).isEqualTo(AccessType.PROVIDE);
        assertThat(created.serviceName()).isEqualTo("ExclusiveService");
    }

    /**
     * Second PROVIDE ACL entry for same context_service from different environment is rejected.
     * Requirements: 4.3
     */
    @Test
    void secondProvideAcl_fromDifferentEnvironment_throwsConflict() {
        // First environment provides the service
        aclService.createAclEntry(new AclEntryCreateRequest(
                environment1Id, "ExclusiveService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.PROVIDE, AccessLevel.APPROVED));

        // Second environment attempts to provide the same service â€” should fail
        assertThatThrownBy(() -> aclService.createAclEntry(new AclEntryCreateRequest(
                environment2Id, "ExclusiveService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.PROVIDE, AccessLevel.APPROVED)))
                .isInstanceOf(SifBrokerException.class)
                .hasMessageContaining("provider already exists");
    }

    /**
     * Non-PROVIDE access types are not affected by single-provider constraint.
     * Requirements: 4.2, 4.3
     */
    @Test
    void nonProvideAccessTypes_notAffectedByConstraint() {
        // First environment provides
        aclService.createAclEntry(new AclEntryCreateRequest(
                environment1Id, "SharedQueryService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.PROVIDE, AccessLevel.APPROVED));

        // Second environment can QUERY the same service (not PROVIDE)
        AclEntryDTO queryEntry = aclService.createAclEntry(new AclEntryCreateRequest(
                environment2Id, "SharedQueryService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.QUERY, AccessLevel.APPROVED));

        assertThat(queryEntry).isNotNull();
        assertThat(queryEntry.accessType()).isEqualTo(AccessType.QUERY);
    }

    /**
     * After deleting the PROVIDE ACL, another environment can become provider.
     * Requirements: 4.2, 4.3
     */
    @Test
    void afterDeletingProvideAcl_anotherEnvironmentCanProvide() {
        // First environment provides
        AclEntryDTO firstProvide = aclService.createAclEntry(new AclEntryCreateRequest(
                environment1Id, "TransferService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.PROVIDE, AccessLevel.APPROVED));

        // Delete the first provider's ACL entry
        aclService.deleteAclEntry(firstProvide.id());

        // Second environment can now provide
        AclEntryDTO secondProvide = aclService.createAclEntry(new AclEntryCreateRequest(
                environment2Id, "TransferService", hierarchy.getZoneId(), hierarchy.getContextId(),
                AccessType.PROVIDE, AccessLevel.APPROVED));

        assertThat(secondProvide).isNotNull();
        assertThat(secondProvide.environmentId()).isEqualTo(environment2Id);
        assertThat(secondProvide.accessType()).isEqualTo(AccessType.PROVIDE);
    }
}
