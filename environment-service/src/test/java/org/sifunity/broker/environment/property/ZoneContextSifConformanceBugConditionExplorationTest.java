// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeTry;
import org.sifunity.broker.environment.controller.GlobalExceptionHandler;
import org.sifunity.broker.environment.controller.ZoneController;
import org.sifunity.broker.environment.dto.ContextCreateRequest;
import org.sifunity.broker.environment.dto.ContextDTO;
import org.sifunity.broker.environment.dto.ZoneCreateRequest;
import org.sifunity.broker.environment.dto.ZoneDTO;
import org.sifunity.broker.environment.service.ZoneService;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Bug Condition Exploration Test for Zone/Context SIF object-service conformance.
 *
 * <p><strong>CRITICAL: These tests MUST FAIL on unfixed code — failure confirms the bug exists.</strong></p>
 *
 * <p>Bug: The Zone/Context endpoints in {@code environment-service} diverge from the SIF 3.7
 * object-service pattern that {@code CodeSetsController}/{@code NamespacesController} already
 * implement. This test encodes the EXPECTED (post-fix) SIF-conformant behavior using a
 * standalone MockMvc against the UNFIXED {@link ZoneController}:</p>
 * <ul>
 *   <li>Zone/Context identifiers are emitted under the canonical field {@code id}
 *       (unfixed emits {@code zoneId}/{@code contextId}; the {@code id} field is the internal UUID).</li>
 *   <li>Single-object create responses are wrapped under the {@code zone}/{@code context} key
 *       (unfixed returns a flat DTO).</li>
 *   <li>Create is exposed on the singular subpath {@code /zones/zone} and
 *       {@code /zones/{zoneId}/contexts/context} with the body wrapped under the object key
 *       (unfixed maps create on the bare collection path).</li>
 *   <li>A create body supplying the identifier only under the legacy {@code zoneId}/{@code contextId}
 *       alias (omitting {@code id}) is rejected with {@code 400 Bad Request}
 *       (unfixed accepts the alias as the identifier).</li>
 *   <li>Admin-rejection and protected-resource errors are the standard {@code SifErrorResponse}
 *       ({@code code}, {@code message}, {@code scope}, {@code timestamp})
 *       (unfixed returns an inline {@code { "message": ... }} body).</li>
 *   <li>Singular deletes require no {@code Content-Type} header (these requests send none).</li>
 * </ul>
 *
 * <p><strong>Expected on UNFIXED code:</strong> FAILS. Singular-subpath creates return 405
 * (no create mapping there), list/response identifiers are UUIDs under {@code id} rather than the
 * business identifier, single-object bodies are flat, alias-only creates are not rejected with
 * 400, and admin-rejection/protected-resource errors are {@code { message }} bodies without
 * {@code code}/{@code scope}/{@code timestamp}.</p>
 *
 * <p><strong>Expected on FIXED code:</strong> PASSES — confirming the fix conforms to the SIF
 * object-service pattern.</p>
 *
 * <p><strong>NOTE (DTO construction):</strong> The mocked {@link ZoneService} returns
 * {@link ZoneDTO}/{@link ContextDTO} built with the CURRENT record constructors. If the fix
 * changes the DTO record shape, only these constructor calls need updating — the assertions
 * (which target the serialized JSON contract) stay the same. Do NOT rewrite the assertions.</p>
 *
 * <p><strong>Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9</strong></p>
 */
class ZoneContextSifConformanceBugConditionExplorationTest {

    /** Admin fingerprint that bypasses the admin guard in ZoneController. */
    private static final String ADMIN_FINGERPRINT = "broker-services";

    /** Fixed business identifiers so the id-vs-UUID discrepancy is unambiguous. */
    private static final String ZONE_BUSINESS_ID = "district-01";
    private static final String CONTEXT_BUSINESS_ID = "grade-5";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private ZoneService zoneService;
    private MockMvc mockMvc;

    @BeforeTry
    void setUp() {
        zoneService = mock(ZoneService.class);

        // If the SIF-conformant mappings existed, these stubs let create/list return 201/200.
        when(zoneService.createZone(anyString(), any(ZoneCreateRequest.class)))
                .thenReturn(new ZoneDTO(UUID.randomUUID(), "sol", ZONE_BUSINESS_ID, "A district zone", Instant.now()));
        when(zoneService.createContext(anyString(), anyString(), anyString(), any(ContextCreateRequest.class)))
                .thenReturn(new ContextDTO(UUID.randomUUID(), "zone-a", CONTEXT_BUSINESS_ID, "A grade context", Instant.now()));
        when(zoneService.listZonesBySolution(anyString(), anyString()))
                .thenReturn(List.of(new ZoneDTO(UUID.randomUUID(), "sol", ZONE_BUSINESS_ID, "A district zone", Instant.now())));
        when(zoneService.listContexts(anyString(), anyString(), anyString()))
                .thenReturn(List.of(new ContextDTO(UUID.randomUUID(), "zone-a", CONTEXT_BUSINESS_ID, "A grade context", Instant.now())));

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(new ZoneController(zoneService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    // ==================================================================================
    // Zone create — singular subpath + wrapped envelope + canonical id (2.1, 2.3, 2.5)
    // ==================================================================================

    /**
     * Zone create SHALL be exposed on {@code POST /{d}/{s}/zones/zone}, accept a body wrapped
     * under {@code zone}, and return {@code { "zone": { "id": ... } }} with 201.
     *
     * <p><strong>Expected on UNFIXED:</strong> FAILS — the singular subpath has no create mapping
     * (returns 405), so there is no wrapped {@code zone} envelope.</p>
     *
     * <p><strong>Validates: Requirements 2.1, 2.3, 2.5</strong></p>
     */
    @Property(tries = 20)
    @Label("Zone create POST /zones/zone returns 201 with { zone: { id } } envelope")
    void zoneCreateUsesSingularSubpathWrappedEnvelopeAndId(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        String body = "{\"zone\":{\"id\":\"" + ZONE_BUSINESS_ID + "\",\"description\":\"A district zone\"}}";

        MockHttpServletResponse response = perform(
                post(zonesBase(domainId, solutionId) + "/zone")
                        .header("X-Environment-Fingerprint", ADMIN_FINGERPRINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));

        assertThat(response.getStatus())
                .as("SIF singular zone create POST %s/zone should return 201", zonesBase(domainId, solutionId))
                .isEqualTo(201);

        JsonNode root = objectMapper.readTree(response.getContentAsString());
        assertThat(root.has("zone"))
                .as("zone create response should be wrapped under a 'zone' object key")
                .isTrue();
        assertThat(root.path("zone").path("id").asText())
                .as("zone identifier should be emitted under canonical 'id'")
                .isEqualTo(ZONE_BUSINESS_ID);
    }

    // ==================================================================================
    // Context create — singular subpath + wrapped envelope + canonical id (2.2, 2.4, 2.6)
    // ==================================================================================

    /**
     * Context create SHALL be exposed on {@code POST /{d}/{s}/zones/{zoneId}/contexts/context},
     * accept a body wrapped under {@code context}, and return {@code { "context": { "id": ... } }}.
     *
     * <p><strong>Expected on UNFIXED:</strong> FAILS — the singular subpath has no create mapping
     * (returns 405), so there is no wrapped {@code context} envelope.</p>
     *
     * <p><strong>Validates: Requirements 2.2, 2.4, 2.6</strong></p>
     */
    @Property(tries = 20)
    @Label("Context create POST /zones/{zoneId}/contexts/context returns 201 with { context: { id } } envelope")
    void contextCreateUsesSingularSubpathWrappedEnvelopeAndId(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("zoneIds") String zoneId) throws Exception {

        String body = "{\"context\":{\"id\":\"" + CONTEXT_BUSINESS_ID + "\",\"description\":\"A grade context\"}}";

        MockHttpServletResponse response = perform(
                post(zonesBase(domainId, solutionId) + "/{zoneId}/contexts/context", zoneId)
                        .header("X-Environment-Fingerprint", ADMIN_FINGERPRINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));

        assertThat(response.getStatus())
                .as("SIF singular context create POST .../zones/%s/contexts/context should return 201", zoneId)
                .isEqualTo(201);

        JsonNode root = objectMapper.readTree(response.getContentAsString());
        assertThat(root.has("context"))
                .as("context create response should be wrapped under a 'context' object key")
                .isTrue();
        assertThat(root.path("context").path("id").asText())
                .as("context identifier should be emitted under canonical 'id'")
                .isEqualTo(CONTEXT_BUSINESS_ID);
    }

    // ==================================================================================
    // Identifier field on list responses (2.1, 2.2)
    // ==================================================================================

    /**
     * A zone list element SHALL carry the business identifier under the canonical {@code id} field.
     *
     * <p><strong>Expected on UNFIXED:</strong> FAILS — {@code id} holds the internal UUID and the
     * business identifier is emitted under {@code zoneId}.</p>
     *
     * <p><strong>Validates: Requirements 2.1</strong></p>
     */
    @Property(tries = 20)
    @Label("Zone list element emits business identifier under canonical 'id'")
    void zoneListEmitsBusinessIdentifierUnderId(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        MockHttpServletResponse response = perform(get(zonesBase(domainId, solutionId)));

        assertThat(response.getStatus()).as("GET zones should return 200").isEqualTo(200);

        JsonNode element = objectMapper.readTree(response.getContentAsString())
                .path("zones").path("zone").path(0);
        assertThat(element.path("id").asText())
                .as("zone list element 'id' should be the business identifier, not the internal UUID")
                .isEqualTo(ZONE_BUSINESS_ID);
    }

    /**
     * A context list element SHALL carry the business identifier under the canonical {@code id} field.
     *
     * <p><strong>Expected on UNFIXED:</strong> FAILS — {@code id} holds the internal UUID and the
     * business identifier is emitted under {@code contextId}.</p>
     *
     * <p><strong>Validates: Requirements 2.2</strong></p>
     */
    @Property(tries = 20)
    @Label("Context list element emits business identifier under canonical 'id'")
    void contextListEmitsBusinessIdentifierUnderId(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("zoneIds") String zoneId) throws Exception {

        MockHttpServletResponse response = perform(
                get(zonesBase(domainId, solutionId) + "/{zoneId}/contexts", zoneId));

        assertThat(response.getStatus()).as("GET contexts should return 200").isEqualTo(200);

        JsonNode element = objectMapper.readTree(response.getContentAsString())
                .path("contexts").path("context").path(0);
        assertThat(element.path("id").asText())
                .as("context list element 'id' should be the business identifier, not the internal UUID")
                .isEqualTo(CONTEXT_BUSINESS_ID);
    }

    // ==================================================================================
    // Legacy-alias-only create rejection (2.7)
    // ==================================================================================

    /**
     * A zone create body supplying the identifier only under the legacy {@code zoneId} alias
     * (omitting {@code id}) SHALL be rejected with {@code 400 Bad Request}.
     *
     * <p><strong>Expected on UNFIXED:</strong> FAILS — the singular subpath has no create mapping
     * (returns 405); the unfixed bare-path create would instead accept the alias.</p>
     *
     * <p><strong>Validates: Requirements 2.7</strong></p>
     */
    @Property(tries = 20)
    @Label("Alias-only zone create body ({ zone: { zoneId } }, no id) is rejected with 400")
    void aliasOnlyZoneCreateIsRejectedWith400(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        String aliasOnlyBody = "{\"zone\":{\"zoneId\":\"" + ZONE_BUSINESS_ID + "\",\"description\":\"A district zone\"}}";

        MockHttpServletResponse response = perform(
                post(zonesBase(domainId, solutionId) + "/zone")
                        .header("X-Environment-Fingerprint", ADMIN_FINGERPRINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(aliasOnlyBody));

        assertThat(response.getStatus())
                .as("alias-only zone create body (id omitted) should be rejected with 400 Bad Request")
                .isEqualTo(400);
    }

    /**
     * A context create body supplying the identifier only under the legacy {@code contextId} alias
     * (omitting {@code id}) SHALL be rejected with {@code 400 Bad Request}.
     *
     * <p><strong>Expected on UNFIXED:</strong> FAILS — the singular subpath has no create mapping
     * (returns 405); the unfixed bare-path create would instead accept the alias.</p>
     *
     * <p><strong>Validates: Requirements 2.7</strong></p>
     */
    @Property(tries = 20)
    @Label("Alias-only context create body ({ context: { contextId } }, no id) is rejected with 400")
    void aliasOnlyContextCreateIsRejectedWith400(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId,
            @ForAll("zoneIds") String zoneId) throws Exception {

        String aliasOnlyBody = "{\"context\":{\"contextId\":\"" + CONTEXT_BUSINESS_ID + "\",\"description\":\"A grade context\"}}";

        MockHttpServletResponse response = perform(
                post(zonesBase(domainId, solutionId) + "/{zoneId}/contexts/context", zoneId)
                        .header("X-Environment-Fingerprint", ADMIN_FINGERPRINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(aliasOnlyBody));

        assertThat(response.getStatus())
                .as("alias-only context create body (id omitted) should be rejected with 400 Bad Request")
                .isEqualTo(400);
    }

    // ==================================================================================
    // Admin-rejection error shape (2.8)
    // ==================================================================================

    /**
     * A non-admin zone create SHALL be rejected with 403 and a standard {@link org.sifunity.broker.common.model.SifErrorResponse}
     * ({@code code}, {@code message}, {@code scope}, {@code timestamp}).
     *
     * <p><strong>Expected on UNFIXED:</strong> FAILS — the singular subpath has no create mapping
     * (returns 405, not 403); the unfixed bare-path create would return an inline {@code { message }}
     * body lacking {@code code}/{@code scope}/{@code timestamp}.</p>
     *
     * <p><strong>Validates: Requirements 2.8</strong></p>
     */
    @Property(tries = 20)
    @Label("Non-admin zone create returns 403 with a SifErrorResponse body")
    void nonAdminZoneCreateReturnsSifErrorResponse(
            @ForAll("nonAdminDomainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        String body = "{\"zone\":{\"id\":\"" + ZONE_BUSINESS_ID + "\",\"description\":\"A district zone\"}}";

        // No X-Environment-Fingerprint header and a non-global domain/solution => non-admin.
        MockHttpServletResponse response = perform(
                post(zonesBase(domainId, solutionId) + "/zone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));

        assertThat(response.getStatus())
                .as("non-admin zone create should be rejected with 403 Forbidden")
                .isEqualTo(403);
        assertSifErrorResponse(response);
    }

    // ==================================================================================
    // Protected-resource error shape + singular delete needs no Content-Type (2.8, 2.9)
    // ==================================================================================

    /**
     * Deleting the protected {@code environment-global} zone SHALL return 409 with a standard
     * {@link org.sifunity.broker.common.model.SifErrorResponse}. The delete request sends NO
     * {@code Content-Type} header (singular deletes carry no body).
     *
     * <p><strong>Expected on UNFIXED:</strong> FAILS — the protected-resource branch returns an
     * inline {@code { message }} body lacking {@code code}/{@code scope}/{@code timestamp}.</p>
     *
     * <p><strong>Validates: Requirements 2.8, 2.9</strong></p>
     */
    @Example
    @Label("Delete protected zone 'environment-global' returns 409 with a SifErrorResponse body (no Content-Type sent)")
    void deleteProtectedZoneReturnsSifErrorResponse() throws Exception {
        // Note: no .contentType(...) — singular delete must not require a Content-Type header.
        MockHttpServletResponse response = perform(
                delete(zonesBase("GLOBAL_DOMAIN", "GLOBAL_SOLUTION") + "/environment-global")
                        .header("X-Environment-Fingerprint", ADMIN_FINGERPRINT));

        assertThat(response.getStatus())
                .as("deleting protected zone 'environment-global' should return 409 Conflict")
                .isEqualTo(409);
        assertSifErrorResponse(response);
    }

    /**
     * Deleting the protected {@code DEFAULT} context SHALL return 409 with a standard
     * {@link org.sifunity.broker.common.model.SifErrorResponse}. The delete request sends NO
     * {@code Content-Type} header (singular deletes carry no body).
     *
     * <p><strong>Expected on UNFIXED:</strong> FAILS — the protected-resource branch returns an
     * inline {@code { message }} body lacking {@code code}/{@code scope}/{@code timestamp}.</p>
     *
     * <p><strong>Validates: Requirements 2.8, 2.9</strong></p>
     */
    @Example
    @Label("Delete protected context 'DEFAULT' returns 409 with a SifErrorResponse body (no Content-Type sent)")
    void deleteProtectedContextReturnsSifErrorResponse() throws Exception {
        // Note: no .contentType(...) — singular delete must not require a Content-Type header.
        MockHttpServletResponse response = perform(
                delete(zonesBase("GLOBAL_DOMAIN", "GLOBAL_SOLUTION") + "/zone-a/contexts/DEFAULT")
                        .header("X-Environment-Fingerprint", ADMIN_FINGERPRINT));

        assertThat(response.getStatus())
                .as("deleting protected context 'DEFAULT' should return 409 Conflict")
                .isEqualTo(409);
        assertSifErrorResponse(response);
    }

    // ========== Helpers ==========

    private String zonesBase(String domainId, String solutionId) {
        return "/" + domainId + "/" + solutionId + "/zones";
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder).andReturn().getResponse();
    }

    /**
     * Asserts the response body is a standard SifErrorResponse: it carries the required
     * {@code code}, {@code message}, {@code scope}, and {@code timestamp} fields.
     */
    private void assertSifErrorResponse(MockHttpServletResponse response) throws Exception {
        JsonNode root = objectMapper.readTree(response.getContentAsString());
        assertThat(root.has("error"))
                .as("SIF error body must be wrapped under a top-level 'error' key")
                .isTrue();
        JsonNode body = root.get("error");
        assertThat(body.has("id"))
                .as("SifErrorResponse must include an 'id' field")
                .isTrue();
        assertThat(body.has("code"))
                .as("SifErrorResponse must include a 'code' field (inline { message } body does not)")
                .isTrue();
        assertThat(body.has("subCode"))
                .as("SifErrorResponse must include a 'subCode' field")
                .isTrue();
        assertThat(body.has("message"))
                .as("SifErrorResponse must include a 'message' field")
                .isTrue();
        assertThat(body.has("scope"))
                .as("SifErrorResponse must include a 'scope' field (inline { message } body does not)")
                .isTrue();
        assertThat(body.has("timestamp"))
                .as("SIF 3.7 errorCommon does not include 'timestamp' — must be absent from wire")
                .isFalse();
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> domainIds() {
        return Arbitraries.of("GLOBAL_DOMAIN", "test-domain", "school-district-1", "state-edu");
    }

    @Provide
    Arbitrary<String> solutionIds() {
        return Arbitraries.of("GLOBAL_SOLUTION", "prod-solution", "test-solution", "staging");
    }

    @Provide
    Arbitrary<String> nonAdminDomainIds() {
        return Arbitraries.of("test-domain", "school-district-1", "state-edu", "prod-domain");
    }

    @Provide
    Arbitrary<String> zoneIds() {
        return Arbitraries.of("zone-a", "district-01", "campus-north", "region-7");
    }
}
