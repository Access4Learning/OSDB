// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import net.jqwik.api.Arbitrary;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.SifEnvironmentResponse;
import org.sifunity.broker.environment.repository.EnvironmentRepository;
import org.sifunity.broker.environment.service.EnvironmentService;

import java.time.Instant;
import java.util.Random;
import java.util.UUID;

import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.test.TestEntityHierarchy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 7: Backward Compatibility Fingerprint Generation.
 *
 * <p>For any environment entity without fingerprint, retrieval generates one
 * and subsequent retrievals return the same value.</p>
 *
 * <p>Tag: Feature: sif-environment-response, Property 7: Backward Compat Fingerprint</p>
 *
 * <p><strong>Validates: Requirements 11.2, 11.3</strong></p>
 */
@SpringBootTest
@ActiveProfiles("test")
class BackwardCompatFingerprintPropertyTest {

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private DomainService domainService;

    @Autowired
    private SolutionService solutionService;

    private final Random random = new Random();

    @RepeatedTest(10)
    @Tag("Feature: sif-environment-response, Property 7: Backward Compat Fingerprint")
    @Transactional
    void environmentWithoutFingerprintGetsOneGeneratedOnRetrieval() {
        // Create hierarchy so solutionUuid is available
        TestEntityHierarchy hierarchy = TestEntityHierarchy.create(domainService, solutionService);

        // Create an environment entity directly in DB without fingerprint
        Environment entity = new Environment();
        entity.setApplicationName("LegacyApp-" + UUID.randomUUID().toString().substring(0, 8));
        entity.setSessionToken(UUID.randomUUID().toString());
        entity.setSharedSecret("dGVzdFNlY3JldA==");
        entity.setCreatedAt(Instant.now());
        entity.setDefaultZoneId("zone-legacy-" + random.nextInt(100));
        entity.setType(random.nextBoolean() ? EnvironmentType.DIRECT : EnvironmentType.BROKERED);
        entity.setSolutionUuid(hierarchy.getSolutionUuid());
        // Deliberately leave fingerprint and authenticationMethod null
        entity.setFingerprint(null);
        entity.setAuthenticationMethod(null);

        Environment saved = environmentRepository.saveAndFlush(entity);
        UUID envId = saved.getId();

        // First retrieval should generate fingerprint
        SifEnvironmentResponse first = environmentService.getEnvironment(envId);
        assertThat(first.fingerprint()).isNotNull().isNotBlank();
        assertThat(first.authenticationMethod()).isEqualTo("SIF_HMACSHA256");

        // Second retrieval should return the same fingerprint
        SifEnvironmentResponse second = environmentService.getEnvironment(envId);
        assertThat(second.fingerprint()).isEqualTo(first.fingerprint());
        assertThat(second.authenticationMethod()).isEqualTo("SIF_HMACSHA256");
    }
}
