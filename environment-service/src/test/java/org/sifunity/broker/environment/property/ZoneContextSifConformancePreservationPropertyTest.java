// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
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
import org.sifunity.broker.environment.exception.ContextNotFoundException;
import org.sifunity.broker.environment.exception.DuplicateContextException;
import org.sifunity.broker.environment.exception.DuplicateZoneException;
import org.sifunity.broker.environment.exception.ZoneNotFoundException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Preservation Property Test for Zone/Context SIF object-service conformance (Property 2).
 *
 * <p><strong>These tests MUST PASS on the current UNFIXED code.</strong> They lock in the
 * behavior that the SIF-conformance fix must NOT change — the regression-prevention baseline
 * (bugfix.md clauses 3.1&ndash;3.8).</p>
 *
 * <p><strong>Property 2 (Preservation):</strong> for every Zone/Context interaction that does
 * NOT trigger the bug condition, the fixed implementation (F') produces the same observable
 * result as the original (F): identical HTTP status codes, admin/authorization guards,
 * protected-resource guards, not-found handling, cascade deletion, list-collection envelopes,
 * and Bean Validation behavior.</p>
 *
 * <p><strong>Observation-first methodology.</strong> The assertions encode the behavior observed
 * on the UNFIXED {@link ZoneController} (standalone MockMvc against a mocked {@link ZoneService}):
 * <ul>
 *   <li>Admin (global admin {@code GLOBAL_DOMAIN}/{@code GLOBAL_SOLUTION}, or
 *       {@code broker-services} fingerprint) create returns 201; delete returns 204.</li>
 *   <li>Non-admin create/delete returns 403.</li>
 *   <li>Deleting the protected {@code environment-global} zone / {@code DEFAULT} context
 *       returns 409.</li>
 *   <li>Deleting a non-existent zone/context returns 404 with a {@code code} field.</li>
 *   <li>Successful admin delete returns 204 and delegates cascade to the service.</li>
 *   <li>{@code GET /zones} and {@code GET /zones/{zoneId}/contexts} return the SIF collection
 *       envelope ({@code zones.zone} / {@code contexts.context}) with the same solution
 *       filtering / zone scoping.</li>
 *   <li>Blank / pattern-violating IDs return 400 from Bean Validation.</li>
 *   <li>Create against a non-existent parent zone returns 404; a duplicate zone/context ID
 *       returns 409.</li>
 * </ul>
 *
 * <p><strong>What is deliberately NOT asserted here</strong> (these DO change with the fix and
 * are covered by the Bug Condition exploration test, not preservation):
 * <ul>
 *   <li>The identifier field name inside list/response elements ({@code zoneId}/{@code contextId}
 *       &rarr; {@code id}).</li>
 *   <li>The error <em>body shape</em> of the admin-rejection (403) and protected-resource (409)
 *       branches ({@code Map.of("message", ...)} &rarr; {@code SifErrorResponse}). Only the
 *       <em>status code</em> of those branches is preserved and asserted here.</li>
 *   <li>The create request <em>path</em> and <em>body envelope</em>. The delete and list
 *       endpoints keep their paths under the fix, so those assertions are stable across F/F'.
 *       The create-status assertions target the SIF singular-subpath contract
 *       ({@code POST /zones/zone}, {@code POST /zones/{zoneId}/contexts/context} with the body
 *       wrapped under the object key) to document the preserved status-decision logic; only the
 *       reachable path/body shape changed with the fix, never the decision outcome.</li>
 * </ul>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8</strong></p>
 */
class ZoneContextSifConformancePreservationPropertyTest {

    private static final String ADMIN_FINGERPRINT = "broker-services";
    private static final String GLOBAL_DOMAIN = "GLOBAL_DOMAIN";
    private static final String GLOBAL_SOLUTION = "GLOBAL_SOLUTION";
    private static final String PROTECTED_ZONE = "environment-global";
    private static final String PROTECTED_CONTEXT = "DEFAULT";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private ZoneService zoneService;
    private MockMvc mockMvc;

    @BeforeTry
    void setUp() {
        zoneService = mock(ZoneService.class);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(new ZoneController(zoneService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    // ==================================================================================
    // Admin-guard preservation — create (3.1, 3.2)
    // ==================================================================================

    /**
     * An admin caller (global admin or {@code broker-services}) creating a zone SHALL receive
     * {@code 201 Created}. A non-admin caller SHALL be rejected with {@code 403 Forbidden}.
     *
     * <p><strong>Validates: Requirements 3.1, 3.2</strong></p>
     */
    @Property(tries = 20)
    @Label("Zone create: admin -> 201, non-admin -> 403 (guard decision preserved)")
    void zoneCreateAdminGuardStatusIsPreserved(@ForAll("callers") Caller caller) throws Exception {
        when(zoneService.createZone(anyString(), any(ZoneCreateRequest.class)))
                .thenReturn(new ZoneDTO(UUID.randomUUID(), caller.solutionId(), "district-01", "desc", Instant.now()));

        MockHttpServletResponse response = perform(createZoneRequest(caller, "district-01"));

        int expected = caller.isAdmin() ? 201 : 403;
        assertThat(response.getStatus())
                .as("zone create status for %s should be %d", caller, expected)
                .isEqualTo(expected);
    }

    /**
     * An admin caller creating a context SHALL receive {@code 201 Created}. A non-admin caller
     * SHALL be rejected with {@code 403 Forbidden}.
     *
     * <p><strong>Validates: Requirements 3.1, 3.2</strong></p>
     */
    @Property(tries = 20)
    @Label("Context create: admin -> 201, non-admin -> 403 (guard decision preserved)")
    void contextCreateAdminGuardStatusIsPreserved(@ForAll("callers") Caller caller) throws Exception {
        when(zoneService.createContext(anyString(), anyString(), anyString(), any(ContextCreateRequest.class)))
                .thenReturn(new ContextDTO(UUID.randomUUID(), "zone-a", "grade-5", "desc", Instant.now()));

        MockHttpServletResponse response = perform(createContextRequest(caller, "zone-a", "grade-5"));

        int expected = caller.isAdmin() ? 201 : 403;
        assertThat(response.getStatus())
                .as("context create status for %s should be %d", caller, expected)
                .isEqualTo(expected);
    }

    // ==================================================================================
    // Admin-guard + protected + not-found + cascade preservation — delete zone (3.2..3.5)
    // ==================================================================================

    /**
     * Zone deletion status is preserved across every (role, resource, existence) combination:
     * non-admin &rarr; 403; admin + protected {@code environment-global} &rarr; 409; admin +
     * non-existent &rarr; 404; admin + existing &rarr; 204 (with cascade delegated to the
     * service).
     *
     * <p><strong>Validates: Requirements 3.2, 3.3, 3.4, 3.5</strong></p>
     */
    @Property(tries = 20)
    @Label("Zone delete status preserved across (role, resource, existence)")
    void zoneDeleteStatusIsPreserved(
            @ForAll("callers") Caller caller,
            @ForAll("zoneDeleteScenarios") DeleteScenario scenario) throws Exception {

        String zoneId = switch (scenario) {
            case PROTECTED -> PROTECTED_ZONE;
            case NOT_FOUND -> "ghost-zone";
            case SUCCESS -> "district-01";
        };

        if (scenario == DeleteScenario.NOT_FOUND) {
            doThrow(new ZoneNotFoundException(zoneId)).when(zoneService).deleteZone(anyString(), anyString(), eq(zoneId));
        } else {
            doNothing().when(zoneService).deleteZone(anyString(), anyString(), anyString());
        }

        MockHttpServletResponse response = perform(
                withFingerprint(delete(zonesBase(caller) + "/{zoneId}", zoneId), caller));

        int expected = expectedDeleteStatus(caller, scenario);
        assertThat(response.getStatus())
                .as("zone delete status for %s scenario=%s should be %d", caller, scenario, expected)
                .isEqualTo(expected);

        // Cascade + success preservation: a successful admin delete delegates to the service,
        // which performs the cascade (contexts -> context_services -> ACLs -> ...).
        if (expected == 204) {
            verify(zoneService, times(1)).deleteZone(anyString(), anyString(), eq(zoneId));
        }
        // Not-found preservation: the 404 body carries a `code` field (SifErrorResponse path).
        if (expected == 404) {
            assertHasCodeField(response);
        }
    }

    // ==================================================================================
    // Admin-guard + protected + not-found + cascade preservation — delete context (3.2..3.5)
    // ==================================================================================

    /**
     * Context deletion status is preserved across every (role, resource, existence) combination:
     * non-admin &rarr; 403; admin + protected {@code DEFAULT} &rarr; 409; admin + non-existent
     * &rarr; 404; admin + existing &rarr; 204 (cascade delegated to the service).
     *
     * <p><strong>Validates: Requirements 3.2, 3.3, 3.4, 3.5</strong></p>
     */
    @Property(tries = 20)
    @Label("Context delete status preserved across (role, resource, existence)")
    void contextDeleteStatusIsPreserved(
            @ForAll("callers") Caller caller,
            @ForAll("contextDeleteScenarios") DeleteScenario scenario) throws Exception {

        String contextId = switch (scenario) {
            case PROTECTED -> PROTECTED_CONTEXT;
            case NOT_FOUND -> "ghost-context";
            case SUCCESS -> "grade-5";
        };

        if (scenario == DeleteScenario.NOT_FOUND) {
            doThrow(new ContextNotFoundException(contextId))
                    .when(zoneService).deleteContext(anyString(), anyString(), anyString(), eq(contextId));
        } else {
            doNothing().when(zoneService).deleteContext(anyString(), anyString(), anyString(), anyString());
        }

        MockHttpServletResponse response = perform(
                withFingerprint(delete(zonesBase(caller) + "/{zoneId}/contexts/{contextId}", "zone-a", contextId), caller));

        int expected = expectedDeleteStatus(caller, scenario);
        assertThat(response.getStatus())
                .as("context delete status for %s scenario=%s should be %d", caller, scenario, expected)
                .isEqualTo(expected);

        if (expected == 204) {
            verify(zoneService, times(1)).deleteContext(anyString(), anyString(), anyString(), eq(contextId));
        }
        if (expected == 404) {
            assertHasCodeField(response);
        }
    }

    // ==================================================================================
    // Parent / duplicate preservation — create (3.8)
    // ==================================================================================

    /**
     * Creating a context under a non-existent parent zone SHALL return {@code 404 Not Found}
     * (the service raises {@link ZoneNotFoundException}), and the body carries a {@code code}
     * field. Uses an admin caller so the guard passes and the request reaches the service.
     *
     * <p><strong>Validates: Requirements 3.8</strong></p>
     */
    @Property(tries = 20)
    @Label("Context create against a non-existent parent zone -> 404 with code")
    void contextCreateNonExistentParentReturns404(@ForAll("adminCallers") Caller admin) throws Exception {
        when(zoneService.createContext(anyString(), anyString(), anyString(), any(ContextCreateRequest.class)))
                .thenThrow(new ZoneNotFoundException("missing-zone"));

        MockHttpServletResponse response = perform(createContextRequest(admin, "missing-zone", "grade-5"));

        assertThat(response.getStatus())
                .as("context create against missing parent zone should return 404")
                .isEqualTo(404);
        assertHasCodeField(response);
    }

    /**
     * Creating a zone with a duplicate ID SHALL return {@code 409 Conflict} (the service raises
     * {@link DuplicateZoneException}).
     *
     * <p><strong>Validates: Requirements 3.8</strong></p>
     */
    @Property(tries = 20)
    @Label("Zone create with a duplicate ID -> 409")
    void zoneCreateDuplicateReturns409(@ForAll("adminCallers") Caller admin) throws Exception {
        when(zoneService.createZone(anyString(), any(ZoneCreateRequest.class)))
                .thenThrow(new DuplicateZoneException(admin.solutionId(), "district-01"));

        MockHttpServletResponse response = perform(createZoneRequest(admin, "district-01"));

        assertThat(response.getStatus())
                .as("duplicate zone create should return 409")
                .isEqualTo(409);
    }

    /**
     * Creating a context with a duplicate ID SHALL return {@code 409 Conflict} (the service
     * raises {@link DuplicateContextException}).
     *
     * <p><strong>Validates: Requirements 3.8</strong></p>
     */
    @Property(tries = 20)
    @Label("Context create with a duplicate ID -> 409")
    void contextCreateDuplicateReturns409(@ForAll("adminCallers") Caller admin) throws Exception {
        when(zoneService.createContext(anyString(), anyString(), anyString(), any(ContextCreateRequest.class)))
                .thenThrow(new DuplicateContextException("zone-a", "grade-5"));

        MockHttpServletResponse response = perform(createContextRequest(admin, "zone-a", "grade-5"));

        assertThat(response.getStatus())
                .as("duplicate context create should return 409")
                .isEqualTo(409);
    }

    // ==================================================================================
    // Validation preservation — create (3.7)
    // ==================================================================================

    /**
     * A blank or pattern-violating zone ID SHALL be rejected with {@code 400 Bad Request} by
     * Bean Validation, regardless of the reachable create path. Uses an admin caller so the
     * only rejection reason under test is validation.
     *
     * <p><strong>Validates: Requirements 3.7</strong></p>
     */
    @Property(tries = 20)
    @Label("Zone create with a blank / pattern-violating ID -> 400")
    void zoneCreateInvalidIdReturns400(
            @ForAll("adminCallers") Caller admin,
            @ForAll("invalidZoneIds") String invalidZoneId) throws Exception {

        // Even if execution reached the service, it would return normally; the 400 must come
        // from Bean Validation on the request DTO.
        when(zoneService.createZone(anyString(), any(ZoneCreateRequest.class)))
                .thenReturn(new ZoneDTO(UUID.randomUUID(), admin.solutionId(), "x", "desc", Instant.now()));

        // The invalid identifier is sent under the wrapped canonical `id` field, so the
        // @NotBlank/@Pattern constraints on ZoneCreateBody.id still trigger the 400.
        MockHttpServletResponse response = perform(createZoneRequest(admin, invalidZoneId));

        assertThat(response.getStatus())
                .as("blank/pattern-violating zone ID %s should return 400", jsonString(invalidZoneId))
                .isEqualTo(400);
    }

    /**
     * A blank or pattern-violating context ID SHALL be rejected with {@code 400 Bad Request} by
     * Bean Validation.
     *
     * <p><strong>Validates: Requirements 3.7</strong></p>
     */
    @Property(tries = 20)
    @Label("Context create with a blank / pattern-violating ID -> 400")
    void contextCreateInvalidIdReturns400(
            @ForAll("adminCallers") Caller admin,
            @ForAll("invalidZoneIds") String invalidContextId) throws Exception {

        when(zoneService.createContext(anyString(), anyString(), anyString(), any(ContextCreateRequest.class)))
                .thenReturn(new ContextDTO(UUID.randomUUID(), "zone-a", "x", "desc", Instant.now()));

        // The invalid identifier is sent under the wrapped canonical `id` field, so the
        // @NotBlank/@Pattern constraints on ContextCreateBody.id still trigger the 400.
        MockHttpServletResponse response = perform(createContextRequest(admin, "zone-a", invalidContextId));

        assertThat(response.getStatus())
                .as("blank/pattern-violating context ID %s should return 400", jsonString(invalidContextId))
                .isEqualTo(400);
    }

    // ==================================================================================
    // List-envelope preservation — GET (3.6)
    // ==================================================================================

    /**
     * {@code GET /{d}/{s}/zones} SHALL return {@code 200} with the SIF collection envelope
     * {@code { "zones": { "zone": [ ... ] } }}, and SHALL scope the listing to the path
     * solution (the service is queried with the path solution ID).
     *
     * <p><strong>Validates: Requirements 3.6</strong></p>
     */
    @Property(tries = 20)
    @Label("GET /zones preserves the { zones: { zone: [] } } envelope and solution scoping")
    void listZonesPreservesEnvelopeAndScoping(@ForAll("callers") Caller caller) throws Exception {
        when(zoneService.listZonesBySolution(anyString(), anyString()))
                .thenReturn(List.of(new ZoneDTO(UUID.randomUUID(), caller.solutionId(), "district-01", "desc", Instant.now())));

        MockHttpServletResponse response = perform(get(zonesBase(caller)));

        assertThat(response.getStatus()).as("GET zones should return 200").isEqualTo(200);

        JsonNode root = objectMapper.readTree(response.getContentAsString());
        assertThat(root.has("zones")).as("response wrapped under plural 'zones'").isTrue();
        assertThat(root.path("zones").path("zone").isArray())
                .as("'zones.zone' should be a JSON array (SIF collection envelope)").isTrue();
        assertThat(root.path("zones").path("zone")).hasSize(1);

        // Zone scoping preserved: the listing is filtered by the path solution ID.
        verify(zoneService, times(1)).listZonesBySolution(eq(caller.domainId()), eq(caller.solutionId()));
    }

    /**
     * {@code GET /{d}/{s}/zones/{zoneId}/contexts} SHALL return {@code 200} with the SIF
     * collection envelope {@code { "contexts": { "context": [ ... ] } }}, scoped to the path
     * solution and zone.
     *
     * <p><strong>Validates: Requirements 3.6</strong></p>
     */
    @Property(tries = 20)
    @Label("GET /zones/{zoneId}/contexts preserves the { contexts: { context: [] } } envelope and scoping")
    void listContextsPreservesEnvelopeAndScoping(@ForAll("callers") Caller caller) throws Exception {
        when(zoneService.listContexts(anyString(), anyString(), anyString()))
                .thenReturn(List.of(new ContextDTO(UUID.randomUUID(), "zone-a", "grade-5", "desc", Instant.now())));

        MockHttpServletResponse response = perform(get(zonesBase(caller) + "/{zoneId}/contexts", "zone-a"));

        assertThat(response.getStatus()).as("GET contexts should return 200").isEqualTo(200);

        JsonNode root = objectMapper.readTree(response.getContentAsString());
        assertThat(root.has("contexts")).as("response wrapped under plural 'contexts'").isTrue();
        assertThat(root.path("contexts").path("context").isArray())
                .as("'contexts.context' should be a JSON array (SIF collection envelope)").isTrue();
        assertThat(root.path("contexts").path("context")).hasSize(1);

        // Zone scoping preserved: contexts are queried for the path solution + zone.
        verify(zoneService, times(1)).listContexts(eq(caller.domainId()), eq(caller.solutionId()), eq("zone-a"));
    }

    // ========== Helpers ==========

    private String zonesBase(Caller caller) {
        return "/" + caller.domainId() + "/" + caller.solutionId() + "/zones";
    }

    /**
     * Builds a zone create request on the SIF singular subpath contract:
     * {@code POST /{d}/{s}/zones/zone} with the body wrapped under the {@code zone} key and the
     * identifier supplied only under the canonical {@code id} field
     * ({@code { "zone": { "id": ..., "description": ... } }}). Only an admin caller carries the
     * fingerprint header; a null fingerprint is simply omitted.
     */
    private MockHttpServletRequestBuilder createZoneRequest(Caller caller, String zoneId) {
        String wrappedBody = "{\"zone\":{\"id\":" + jsonString(zoneId) + ",\"description\":\"desc\"}}";
        return withFingerprint(post(zonesBase(caller) + "/zone")
                .contentType(MediaType.APPLICATION_JSON)
                .content(wrappedBody), caller);
    }

    /**
     * Builds a context create request on the SIF singular subpath contract:
     * {@code POST /{d}/{s}/zones/{zoneId}/contexts/context} with the body wrapped under the
     * {@code context} key and the identifier supplied only under the canonical {@code id} field
     * ({@code { "context": { "id": ..., "description": ... } }}).
     */
    private MockHttpServletRequestBuilder createContextRequest(Caller caller, String zoneId, String contextId) {
        String wrappedBody = "{\"context\":{\"id\":" + jsonString(contextId) + ",\"description\":\"desc\"}}";
        return withFingerprint(post(zonesBase(caller) + "/{zoneId}/contexts/context", zoneId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(wrappedBody), caller);
    }

    /**
     * Adds the {@code X-Environment-Fingerprint} header only when the caller carries one.
     * MockMvc rejects a null header value, and the global-admin caller intentionally has none.
     */
    private MockHttpServletRequestBuilder withFingerprint(MockHttpServletRequestBuilder builder, Caller caller) {
        if (caller.fingerprint() != null) {
            return builder.header("X-Environment-Fingerprint", caller.fingerprint());
        }
        return builder;
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder).andReturn().getResponse();
    }

    private void assertHasCodeField(MockHttpServletResponse response) throws Exception {
        JsonNode root = objectMapper.readTree(response.getContentAsString());
        assertThat(root.has("error"))
                .as("not-found error body must be wrapped under a top-level 'error' key")
                .isTrue();
        assertThat(root.path("error").has("code"))
                .as("not-found error body must include a 'code' field")
                .isTrue();
    }

    private static int expectedDeleteStatus(Caller caller, DeleteScenario scenario) {
        if (!caller.isAdmin()) {
            return 403; // admin guard runs before any resource check
        }
        return switch (scenario) {
            case PROTECTED -> 409;
            case NOT_FOUND -> 404;
            case SUCCESS -> 204;
        };
    }

    /** Escapes a raw string into a JSON string literal (handles the blank/edge inputs). */
    private static String jsonString(String raw) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : raw.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append("\"").toString();
    }

    // ========== Model ==========

    /** A caller identity: the path {@code domainId}/{@code solutionId} and optional fingerprint. */
    record Caller(String domainId, String solutionId, String fingerprint) {
        boolean isAdmin() {
            boolean globalAdmin = GLOBAL_DOMAIN.equals(domainId) && GLOBAL_SOLUTION.equals(solutionId);
            return globalAdmin || ADMIN_FINGERPRINT.equals(fingerprint);
        }
    }

    enum DeleteScenario { PROTECTED, NOT_FOUND, SUCCESS }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<Caller> callers() {
        return Arbitraries.oneOf(adminCallers(), nonAdminCallers());
    }

    @Provide
    Arbitrary<Caller> adminCallers() {
        // Variant 1: global-admin path (no fingerprint needed).
        Arbitrary<Caller> globalAdmin = Arbitraries.just(new Caller(GLOBAL_DOMAIN, GLOBAL_SOLUTION, null));
        // Variant 2: any non-global path but with the broker-services fingerprint.
        Arbitrary<Caller> brokerServices = Combinators.combine(nonGlobalDomains(), nonGlobalSolutions())
                .as((d, s) -> new Caller(d, s, ADMIN_FINGERPRINT));
        return Arbitraries.oneOf(globalAdmin, brokerServices);
    }

    @Provide
    Arbitrary<Caller> nonAdminCallers() {
        // Non-global path AND no admin fingerprint (absent, or an unrelated fingerprint).
        Arbitrary<String> fp = Arbitraries.of("some-tenant-env", "provider-01").injectNull(0.34);
        return Combinators.combine(nonGlobalDomains(), nonGlobalSolutions(), fp)
                .as(Caller::new);
    }

    @Provide
    Arbitrary<String> nonGlobalDomains() {
        return Arbitraries.of("test-domain", "school-district-1", "state-edu", "prod-domain");
    }

    @Provide
    Arbitrary<String> nonGlobalSolutions() {
        return Arbitraries.of("prod-solution", "test-solution", "staging", "sol-42");
    }

    @Provide
    Arbitrary<DeleteScenario> zoneDeleteScenarios() {
        return Arbitraries.of(DeleteScenario.PROTECTED, DeleteScenario.NOT_FOUND, DeleteScenario.SUCCESS);
    }

    @Provide
    Arbitrary<DeleteScenario> contextDeleteScenarios() {
        return Arbitraries.of(DeleteScenario.PROTECTED, DeleteScenario.NOT_FOUND, DeleteScenario.SUCCESS);
    }

    @Provide
    Arbitrary<String> invalidZoneIds() {
        // Blank / whitespace-only violate @NotBlank; leading/trailing punctuation and illegal
        // characters violate the naming @Pattern.
        return Arbitraries.of("", "   ", "-leading-hyphen", "_leading-underscore",
                "trailing-hyphen-", "has space", "bad$char", "bad/char");
    }
}
