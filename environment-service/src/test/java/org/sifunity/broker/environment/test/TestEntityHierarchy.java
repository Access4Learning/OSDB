// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.DomainDTO;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.dto.SolutionDTO;
import org.sifunity.broker.environment.dto.ZoneCreateBody;
import org.sifunity.broker.environment.dto.ZoneCreateRequest;
import org.sifunity.broker.environment.dto.ZoneCreateRequestWrapper;
import org.sifunity.broker.environment.dto.ZoneDTO;
import org.sifunity.broker.environment.dto.ContextDTO;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.SolutionService;
import org.sifunity.broker.environment.service.ZoneService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Non-Spring utility class that creates a Domain → Solution → Zone → Context
 * hierarchy for use in test setup.
 *
 * <p>Provides three factory methods:</p>
 * <ul>
 *   <li>{@link #create(DomainService, SolutionService)} — service-layer factory for property tests</li>
 *   <li>{@link #createFull(DomainService, SolutionService, ZoneService)} — full hierarchy including zone + context</li>
 *   <li>{@link #createViaMockMvc(MockMvc, ObjectMapper)} — HTTP-based factory for integration tests</li>
 * </ul>
 */
public class TestEntityHierarchy {

    private final String domainId;
    private final String solutionId;
    private final String zoneId;
    private final String contextId;
    private final UUID domainUuid;
    private final UUID solutionUuid;
    private final UUID zoneUuid;
    private final UUID contextUuid;

    private TestEntityHierarchy(String domainId, String solutionId,
                                String zoneId, String contextId,
                                UUID domainUuid, UUID solutionUuid,
                                UUID zoneUuid, UUID contextUuid) {
        this.domainId = domainId;
        this.solutionId = solutionId;
        this.zoneId = zoneId;
        this.contextId = contextId;
        this.domainUuid = domainUuid;
        this.solutionUuid = solutionUuid;
        this.zoneUuid = zoneUuid;
        this.contextUuid = contextUuid;
    }

    /**
     * Generates a unique ID using System.nanoTime() + random suffix to avoid
     * collisions across {@code @RepeatedTest} iterations.
     */
    private static String uniqueId(String prefix) {
        long nano = System.nanoTime();
        int rand = ThreadLocalRandom.current().nextInt(10000);
        return prefix + nano + "r" + rand;
    }

    // ========== Service-layer factory (for property tests / @SpringBootTest) ==========

    /**
     * Creates a Domain + Solution hierarchy via the service layer.
     *
     * @param domainService  the domain service bean
     * @param solutionService the solution service bean
     * @return hierarchy with {@code getDomainId()}, {@code getSolutionId()}, {@code getSolutionUuid()}
     */
    public static TestEntityHierarchy create(DomainService domainService,
                                             SolutionService solutionService) {
        String domainId = uniqueId("d");
        String solutionId = uniqueId("s");

        DomainDTO domain = domainService.createDomain(
                new DomainCreateRequest(domainId, "Test domain"));
        SolutionDTO solution = solutionService.createSolution(
                new SolutionCreateRequest(domainId, solutionId, "Test solution"));

        return new TestEntityHierarchy(
                domainId, solutionId, null, null,
                domain.id(), solution.id(), null, null);
    }

    // ========== Full hierarchy factory ==========

    /**
     * Creates a Domain + Solution + Zone + Context hierarchy via the service layer.
     *
     * @param domainService   the domain service bean
     * @param solutionService the solution service bean
     * @param zoneService     the zone service bean
     * @return hierarchy with all accessors including {@code getZoneId()}, {@code getContextId()}
     */
    public static TestEntityHierarchy createFull(DomainService domainService,
                                                 SolutionService solutionService,
                                                 ZoneService zoneService) {
        String domainId = uniqueId("d");
        String solutionId = uniqueId("s");
        String zoneId = uniqueId("z");

        DomainDTO domain = domainService.createDomain(
                new DomainCreateRequest(domainId, "Test domain"));
        SolutionDTO solution = solutionService.createSolution(
                new SolutionCreateRequest(domainId, solutionId, "Test solution"));
        ZoneDTO zone = zoneService.createZone(domainId,
                new ZoneCreateRequest(solutionId, zoneId, "Test zone"));

        // Zone creation auto-creates a DEFAULT context per SIF spec
        List<ContextDTO> contexts = zoneService.listContexts(domainId, solutionId, zoneId);
        ContextDTO defaultContext = contexts.stream()
                .filter(c -> "DEFAULT".equals(c.contextId()))
                .findFirst()
                .orElse(contexts.isEmpty() ? null : contexts.get(0));

        String contextId = defaultContext != null ? defaultContext.contextId() : null;
        UUID contextUuid = defaultContext != null ? defaultContext.id() : null;

        return new TestEntityHierarchy(
                domainId, solutionId, zoneId, contextId,
                domain.id(), solution.id(), zone.id(), contextUuid);
    }

    // ========== MockMvc factory (for integration tests) ==========

    /**
     * Creates a Domain + Solution + Zone hierarchy via HTTP POST calls.
     *
     * @param mockMvc      the MockMvc instance
     * @param objectMapper the Jackson ObjectMapper
     * @return hierarchy with all accessors populated from JSON responses
     * @throws Exception if any HTTP call fails
     */
    public static TestEntityHierarchy createViaMockMvc(MockMvc mockMvc,
                                                       ObjectMapper objectMapper) throws Exception {
        String domainId = uniqueId("d");
        String solutionId = uniqueId("s");
        String zoneId = uniqueId("z");

        // Create Domain
        MvcResult domainResult = mockMvc.perform(post("/domains")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DomainCreateRequest(domainId, "Test domain"))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode domainJson = objectMapper.readTree(
                domainResult.getResponse().getContentAsString());
        UUID domainUuid = UUID.fromString(domainJson.get("id").asText());

        // Create Solution
        MvcResult solutionResult = mockMvc.perform(post("/" + domainId + "/solutions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SolutionCreateRequest(domainId, solutionId, "Test solution"))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode solutionJson = objectMapper.readTree(
                solutionResult.getResponse().getContentAsString());
        UUID solutionUuid = UUID.fromString(solutionJson.get("id").asText());

        // Create Zone (requires admin fingerprint after security enforcement)
        // SIF object-service contract: singular subpath, wrapped body, response wrapped
        // under "zone" with the internal UUID serialized as "uuid".
        MvcResult zoneResult = mockMvc.perform(post("/" + domainId + "/" + solutionId + "/zones/zone")
                        .header("X-Environment-Fingerprint", "broker-services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ZoneCreateRequestWrapper(new ZoneCreateBody(zoneId, "Test zone")))))
                .andExpect(status().isCreated())
                .andReturn();

        // Zone UUID is no longer exposed in the API response (SIF spec compliance).
        // Tests that need the zone UUID should use createFull() which has service-layer access.
        UUID zoneUuid = null;

        // Zone creation auto-creates DEFAULT context; extract contextId from zone response if available
        String contextId = "DEFAULT";
        UUID contextUuid = null;

        return new TestEntityHierarchy(
                domainId, solutionId, zoneId, contextId,
                domainUuid, solutionUuid, zoneUuid, contextUuid);
    }

    // ========== Accessors ==========

    public String getDomainId() {
        return domainId;
    }

    public String getSolutionId() {
        return solutionId;
    }

    public UUID getSolutionUuid() {
        return solutionUuid;
    }

    public UUID getDomainUuid() {
        return domainUuid;
    }

    public String getZoneId() {
        return zoneId;
    }

    public String getContextId() {
        return contextId;
    }

    public UUID getZoneUuid() {
        return zoneUuid;
    }

    public UUID getContextUuid() {
        return contextUuid;
    }

    /**
     * Returns the URL base path for resource endpoints:
     * {@code /{domainId}/{solutionId}}.
     */
    public String resourceBasePath() {
        return "/" + domainId + "/" + solutionId;
    }
}
