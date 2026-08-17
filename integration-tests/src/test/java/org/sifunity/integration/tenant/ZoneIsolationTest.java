// SPDX-License-Identifier: MIT
package org.sifunity.integration.tenant;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sifunity.integration.base.ServiceEnabledIntegrationTestBase;
import org.sifunity.integration.dto.DomainResponse;
import org.sifunity.integration.dto.SolutionResponse;
import org.sifunity.integration.dto.ZoneResponse;
import org.sifunity.integration.generator.DomainGenerator;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for zone isolation across solutions.
 *
 * <p>Verifies that the same {@code zoneId} string used in two different solutions
 * refers to independent zone records. Deleting one solution's zone (via solution
 * cascade deletion) does not affect the other solution's zone with the same ID.</p>
 *
 * <p><strong>Validates: Requirements 11.1, 11.2, 11.3</strong></p>
 *
 * @since 1.0.0
 */
@Slf4j
@Tag("multi-tenant")
@Tag("zone-isolation")
@DisplayName("Zone Isolation — Same Zone ID in Different Solutions Are Independent")
public class ZoneIsolationTest extends ServiceEnabledIntegrationTestBase {

    private static final String SHARED_ZONE_ID = "shared-zone";

    private DomainGenerator domainGenerator;

    private String domainId;
    private String solutionAId;
    private String solutionBId;
    private DomainResponse domain;
    private SolutionResponse solutionA;
    private SolutionResponse solutionB;
    private ZoneResponse zoneA;
    private ZoneResponse zoneB;

    /**
     * Sets up test data before each test.
     *
     * <ol>
     *   <li>Initializes DomainGenerator</li>
     *   <li>Creates one domain with two solutions (A and B)</li>
     *   <li>Creates a zone with the same {@code zoneId} ("shared-zone") in both solutions</li>
     * </ol>
     */
    @BeforeEach
    @Override
    public void setupTest() {
        super.setupTest();

        log.info("Setting up Zone Isolation test");

        String environmentServiceUrl = getEnvironmentServiceUrl();
        domainGenerator = new DomainGenerator(environmentServiceUrl);

        // Generate unique IDs to avoid collisions
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        domainId = "zone-iso-domain-" + uniqueSuffix;
        solutionAId = "solution-a-" + uniqueSuffix;
        solutionBId = "solution-b-" + uniqueSuffix;

        // Create domain
        domain = domainGenerator.createDomain(domainId, "Zone Isolation Test Domain");
        log.info("Domain created: domainId={}", domain.domainId());

        // Create Solution A with zone "shared-zone"
        solutionA = domainGenerator.createSolution(domainId, solutionAId, "Zone Isolation - Solution A");
        log.info("Solution A created: solutionId={} in domain={}", solutionA.solutionId(), domainId);

        zoneA = domainGenerator.createZone(domainId, solutionAId, SHARED_ZONE_ID, "Shared zone in Solution A");
        log.info("Zone A created: zoneId={} in solution={}/{}", zoneA.zoneId(), domainId, solutionAId);

        // Create Solution B with zone "shared-zone" (same zoneId)
        solutionB = domainGenerator.createSolution(domainId, solutionBId, "Zone Isolation - Solution B");
        log.info("Solution B created: solutionId={} in domain={}", solutionB.solutionId(), domainId);

        zoneB = domainGenerator.createZone(domainId, solutionBId, SHARED_ZONE_ID, "Shared zone in Solution B");
        log.info("Zone B created: zoneId={} in solution={}/{}", zoneB.zoneId(), domainId, solutionBId);
    }

    /**
     * Cleans up test data after each test.
     * Deletes domain (cascade cleans all solutions and zones).
     */
    @AfterEach
    @Override
    public void teardownTest() {
        log.info("Tearing down Zone Isolation test");

        try {
            if (domain != null) {
                domainGenerator.deleteDomain(domainId);
                log.info("Domain deleted: {}", domainId);
            }
        } catch (Exception e) {
            log.error("Error during test cleanup", e);
        }

        super.teardownTest();
    }

    /**
     * Tests that the same zoneId in two different solutions creates independent zones,
     * and deleting one solution's zone leaves the other solution's zone unaffected.
     *
     * <p><strong>Validates: Requirements 11.1, 11.2, 11.3</strong></p>
     *
     * <p>Test scenario:
     * <ol>
     *   <li>Verify both solutions have a zone with zoneId "shared-zone" (Requirement 11.1)</li>
     *   <li>Delete Solution A (cascades to its zones) (Requirement 11.2)</li>
     *   <li>Query Solution B's zones → assert zone with "shared-zone" still exists (Requirement 11.3)</li>
     * </ol>
     */
    @Test
    @DisplayName("Same zoneId in different solutions are independent — deleting one leaves the other intact")
    public void testZoneIsolation_SameZoneIdInDifferentSolutionsAreIndependent() {
        log.info("Starting test: Zone Isolation — same zoneId in different solutions are independent");

        // Step 1: Verify both solutions have a zone with the same zoneId (Requirement 11.1)
        List<ZoneResponse> solutionAZones = domainGenerator.listZones(domainId, solutionAId);
        assertThat(solutionAZones)
            .as("Solution A should have a zone with zoneId '%s'", SHARED_ZONE_ID)
            .extracting(ZoneResponse::zoneId)
            .contains(SHARED_ZONE_ID);

        List<ZoneResponse> solutionBZones = domainGenerator.listZones(domainId, solutionBId);
        assertThat(solutionBZones)
            .as("Solution B should have a zone with zoneId '%s'", SHARED_ZONE_ID)
            .extracting(ZoneResponse::zoneId)
            .contains(SHARED_ZONE_ID);

        // Verify they are independent records (different internal UUIDs)
        UUID zoneAInternalId = solutionAZones.stream()
            .filter(z -> SHARED_ZONE_ID.equals(z.zoneId()))
            .findFirst()
            .orElseThrow()
            .id();

        UUID zoneBInternalId = solutionBZones.stream()
            .filter(z -> SHARED_ZONE_ID.equals(z.zoneId()))
            .findFirst()
            .orElseThrow()
            .id();

        assertThat(zoneAInternalId)
            .as("Zones with same zoneId in different solutions should have different internal UUIDs")
            .isNotEqualTo(zoneBInternalId);

        log.info("Both solutions have independent zones with zoneId='{}': A.id={}, B.id={}",
            SHARED_ZONE_ID, zoneAInternalId, zoneBInternalId);

        // Step 2: Delete Solution A — cascades to its zones (Requirement 11.2)
        domainGenerator.deleteSolution(domainId, solutionAId);
        log.info("Solution A deleted: {} (cascade removes its zones)", solutionAId);

        // Step 3: Query Solution B's zones → assert zone with "shared-zone" still exists (Requirement 11.3)
        List<ZoneResponse> solutionBZonesAfterDelete = domainGenerator.listZones(domainId, solutionBId);

        assertThat(solutionBZonesAfterDelete)
            .as("Solution B should still have its zone with zoneId '%s' after Solution A deletion", SHARED_ZONE_ID)
            .extracting(ZoneResponse::zoneId)
            .contains(SHARED_ZONE_ID);

        // Verify it's the same zone record (same internal UUID)
        UUID zoneBInternalIdAfterDelete = solutionBZonesAfterDelete.stream()
            .filter(z -> SHARED_ZONE_ID.equals(z.zoneId()))
            .findFirst()
            .orElseThrow()
            .id();

        assertThat(zoneBInternalIdAfterDelete)
            .as("Solution B's zone should retain the same internal UUID after Solution A deletion")
            .isEqualTo(zoneBInternalId);

        log.info("Solution B's zone with zoneId='{}' is intact after Solution A deletion: id={}",
            SHARED_ZONE_ID, zoneBInternalIdAfterDelete);

        log.info("Test completed: Zone Isolation — same zoneId in different solutions are independent");
    }
}
