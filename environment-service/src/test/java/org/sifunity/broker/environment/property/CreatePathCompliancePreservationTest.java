// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeTry;
import org.sifunity.broker.common.constant.AlertLevel;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.Exchange;
import org.sifunity.broker.environment.controller.AlertsController;
import org.sifunity.broker.environment.controller.CodeSetsController;
import org.sifunity.broker.environment.controller.EnvironmentController;
import org.sifunity.broker.environment.controller.GlobalExceptionHandler;
import org.sifunity.broker.environment.controller.NamespacesController;
import org.sifunity.broker.environment.controller.ProvisionRequestController;
import org.sifunity.broker.environment.controller.XQueryTemplateController;
import org.sifunity.broker.environment.controller.ZoneController;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.AlertDTO;
import org.sifunity.broker.environment.dto.CodeSetDTO;
import org.sifunity.broker.environment.dto.ContextDTO;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;
import org.sifunity.broker.environment.dto.InfrastructureServicesDTO;
import org.sifunity.broker.environment.dto.NamespaceDTO;
import org.sifunity.broker.environment.dto.ProvisionRequestResponseDTO;
import org.sifunity.broker.environment.dto.ProvisionedZonesDTO;
import org.sifunity.broker.environment.dto.SifEnvironmentResponse;
import org.sifunity.broker.environment.dto.XQueryParametersDTO;
import org.sifunity.broker.environment.dto.XQueryTemplateDTO;
import org.sifunity.broker.environment.dto.ZoneDTO;
import org.sifunity.broker.environment.service.AlertsService;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.sifunity.broker.environment.service.CodeSetsService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.NamespacesService;
import org.sifunity.broker.environment.service.ProvisionRequestService;
import org.sifunity.broker.environment.service.UserEnvironmentAccessService;
import org.sifunity.broker.environment.service.XQueryTemplateService;
import org.sifunity.broker.environment.service.ZoneService;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Preservation Property Tests for SIF 3.7 create-path compliance (environment-service).
 *
 * <p><strong>Property 2: Preservation — Non-create and already-compliant behavior unchanged.</strong></p>
 *
 * <p><strong>CRITICAL: These tests are EXPECTED TO PASS on the UNFIXED code.</strong> They capture the
 * baseline behavior (status codes / routing) for inputs where {@code isBugCondition} returns false, so
 * that the same behavior can be asserted unchanged after the fix (task 3.5).</p>
 *
 * <p><strong>Observation-first methodology:</strong> each assertion below records the actual status the
 * UNFIXED controllers return for a non-buggy input and asserts it. The fix (singular-subpath create +
 * 405 guard + provider rename) only touches the bare-collection create paths of the four affected
 * resources; none of the operations exercised here are on that path, so they must remain identical.</p>
 *
 * <p>Covered preservation surfaces (all using standalone {@link MockMvc} against the real controllers
 * with the real {@link GlobalExceptionHandler} so exception → status mapping matches production):</p>
 * <ul>
 *   <li>Retrieval/list of affected resources: {@code GET /environments/{id}}, {@code GET /environments},
 *       {@code GET /xquerys}, {@code GET /xquerys/{token}}, {@code GET .../provisionRequests/{id}}.</li>
 *   <li>Update/delete of affected resources: {@code PUT/DELETE /environments/{id}},
 *       {@code DELETE /xquerys/{token}}, {@code PUT/DELETE .../provisionRequests/{id}}.</li>
 *   <li>Already-compliant creates: {@code POST /namespaces/namespace} (201),
 *       {@code POST /alerts/alert} (201) + bare {@code POST /alerts} (405),
 *       {@code POST /codeSets/codeSet} (201) — these must not regress.</li>
 *   <li>Zones/contexts create on the SIF singular subpath: {@code POST /zones/zone} and
 *       {@code POST /zones/{zoneId}/contexts/context} are the create endpoints (201). The former
 *       bare-collection create paths ({@code POST /zones}, {@code POST /zones/{zoneId}/contexts})
 *       were removed and no longer create.</li>
 * </ul>
 *
 * <p><strong>Note on provider:</strong> the {@code provider-registrations} → {@code providers} collection
 * rename moves the provider paths, so provider list/lookup/delete cannot be pinned to a single literal
 * path that is stable across the fix. The provider rename and its create/405 behavior are validated by
 * {@link CreatePathComplianceBugConditionExplorationTest} (Property 1). The provider routing/auth
 * preservation (authenticate, no ACL) is covered by the gateway preservation test.</p>
 *
 * <p><strong>Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5</strong></p>
 */
class CreatePathCompliancePreservationTest {

    private static final UUID ENV_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RESOURCE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BROKER_FINGERPRINT = "broker-services";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeTry
    void setUp() {
        EnvironmentService environmentService = mock(EnvironmentService.class);
        UserEnvironmentAccessService userEnvironmentAccessService = mock(UserEnvironmentAccessService.class);
        XQueryTemplateService xQueryTemplateService = mock(XQueryTemplateService.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        ProvisionRequestService provisionRequestService = mock(ProvisionRequestService.class);
        NamespacesService namespacesService = mock(NamespacesService.class);
        AlertsService alertsService = mock(AlertsService.class);
        CodeSetsService codeSetsService = mock(CodeSetsService.class);
        ZoneService zoneService = mock(ZoneService.class);

        // Affected-resource non-create operations (retrieval/list/update/delete) succeed.
        when(environmentService.getEnvironment(any())).thenReturn(sampleEnvResponse());
        when(environmentService.listEnvironments(any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleEnvResponse())));
        when(environmentService.updateEnvironment(any(), any())).thenReturn(sampleEnvResponse());

        when(authorizationService.isAdmin(any())).thenReturn(true);
        when(xQueryTemplateService.list(any(), any())).thenReturn(List.of(sampleXQueryDto()));
        when(xQueryTemplateService.getByToken(any(), any(), any())).thenReturn(sampleXQueryDto());

        when(provisionRequestService.getProvisionRequest(any(), any())).thenReturn(sampleProvisionResponse());
        when(provisionRequestService.resolveProvisionRequest(any(), any(), any()))
                .thenReturn(sampleProvisionResponse());

        // Already-compliant create services.
        when(namespacesService.createNamespace(any(), any(), any())).thenReturn(sampleNamespaceDto());
        when(alertsService.createAlert(any(), any(), any(), any(), anyBoolean())).thenReturn(sampleAlertDto());
        when(codeSetsService.createCodeSet(any(), any(), any())).thenReturn(sampleCodeSetDto());

        // Out-of-scope create services.
        when(zoneService.createZone(any(), any())).thenReturn(sampleZoneDto());
        when(zoneService.createContext(any(), any(), any(), any())).thenReturn(sampleContextDto());

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new EnvironmentController(environmentService, userEnvironmentAccessService),
                        new XQueryTemplateController(xQueryTemplateService, authorizationService),
                        new ProvisionRequestController(provisionRequestService),
                        new NamespacesController(namespacesService, authorizationService),
                        new AlertsController(alertsService, authorizationService),
                        new CodeSetsController(codeSetsService, authorizationService),
                        new ZoneController(zoneService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ===================== Retrieval / list preservation (affected resources) =====================

    /**
     * Preservation: retrieval and listing of affected resources are unchanged.
     *
     * <p><strong>Validates: Requirement 3.1</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: GET by-id and list for affected resources return unchanged statuses")
    void retrievalAndListPreserved(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        String prefix = "/" + domainId + "/" + solutionId;

        assertStatus(get(prefix + "/environments/" + RESOURCE_ID), 200,
                "GET environment by id");
        assertStatus(get(prefix + "/environments"), 200,
                "GET environments list");
        assertStatus(get(prefix + "/xquerys"), 200,
                "GET xquerys list (non-empty)");
        assertStatus(get(prefix + "/xquerys/StudentSnapshot"), 200,
                "GET xquery by token");
        assertStatus(get(prefix + "/environments/" + ENV_ID + "/provisionRequests/" + RESOURCE_ID), 200,
                "GET provisionRequest by id (resolved)");
    }

    // ===================== Update / delete preservation (affected resources) =====================

    /**
     * Preservation: update and delete of affected resources are unchanged.
     *
     * <p><strong>Validates: Requirement 3.1</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: PUT/DELETE for affected resources return unchanged statuses")
    void updateAndDeletePreserved(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        String prefix = "/" + domainId + "/" + solutionId;

        assertStatus(put(prefix + "/environments/" + RESOURCE_ID).content("{}"), 200,
                "PUT environment update");
        assertStatus(delete(prefix + "/environments/" + RESOURCE_ID), 204,
                "DELETE environment");
        assertStatus(delete(prefix + "/xquerys/StudentSnapshot"), 204,
                "DELETE xquery");
        assertStatus(put(prefix + "/environments/" + ENV_ID + "/provisionRequests/" + RESOURCE_ID).content("{}"),
                200, "PUT provisionRequest resolve");
        assertStatus(delete(prefix + "/environments/" + ENV_ID + "/provisionRequests/" + RESOURCE_ID), 204,
                "DELETE provisionRequest");
    }

    // ===================== Already-compliant create preservation =====================

    /**
     * Preservation: already-compliant singular creates still return 201 and must not regress.
     *
     * <p><strong>Validates: Requirements 3.2, 3.3, 3.4</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: already-compliant singular-subpath creates return 201")
    void alreadyCompliantSingularCreatesPreserved(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        String prefix = "/" + domainId + "/" + solutionId;

        assertStatus(post(prefix + "/namespaces/namespace").content(namespaceBody()), 201,
                "POST namespaces/namespace");
        assertStatus(post(prefix + "/alerts/alert").content(alertBody()), 201,
                "POST alerts/alert");
        assertStatus(post(prefix + "/codeSets/codeSet").content(codeSetBody()), 201,
                "POST codeSets/codeSet");
    }

    /**
     * Preservation: the bare-collection POST for an already-compliant resource that explicitly
     * guards it (alerts) keeps returning 405, and other already-compliant bare-collection POSTs
     * are rejected (not accepted as a create).
     *
     * <p><strong>Validates: Requirements 3.2, 3.3, 3.4</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: bare-collection POST on already-compliant resources is rejected (not a create)")
    void alreadyCompliantBareCollectionRejectionPreserved(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        String prefix = "/" + domainId + "/" + solutionId;

        // AlertsController explicitly maps a bare @PostMapping that returns 405.
        assertStatus(post(prefix + "/alerts").content(alertBody()), 405,
                "bare POST alerts → 405");

        // Namespaces/CodeSets have no bare-collection create handler; the bare POST is rejected
        // (never a successful 2xx create). Assert robustly that it is not accepted as a create.
        assertRejected(post(prefix + "/namespaces").content(namespaceBody()),
                "bare POST namespaces is rejected");
        assertRejected(post(prefix + "/codeSets").content(codeSetBody()),
                "bare POST codeSets is rejected");
    }

    // ===================== Out-of-scope resource preservation =====================

    /**
     * Preservation: zones/contexts create on the SIF singular subpath returns 201, and the former
     * bare-collection create paths no longer create (they were removed by the conformance fix).
     *
     * <p>The SIF singular subpath ({@code POST /zones/zone},
     * {@code POST /zones/{zoneId}/contexts/context}) with the wrapped body is the create endpoint
     * and returns 201. The bare-collection POST ({@code POST /zones},
     * {@code POST /zones/{zoneId}/contexts}) has no create handler anymore, so it is rejected
     * (never a 2xx create).</p>
     *
     * <p><strong>Validates: Requirement 3.5</strong></p>
     */
    @Property(tries = 20)
    @Label("Preservation: zones/contexts create lives on the singular subpath (201); bare-collection POST no longer creates")
    void outOfScopeResourcesPreserved(
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        String prefix = "/" + domainId + "/" + solutionId;

        // Singular subpath is the create path (wrapped body, identifier under id) → 201.
        assertStatus(post(prefix + "/zones/zone").content(zoneBody())
                        .header("X-Environment-Fingerprint", BROKER_FINGERPRINT),
                201, "POST zones/zone (SIF singular subpath create)");
        assertStatus(post(prefix + "/zones/zone1/contexts/context").content(contextBody())
                        .header("X-Environment-Fingerprint", BROKER_FINGERPRINT),
                201, "POST zones/{zoneId}/contexts/context (SIF singular subpath create)");

        // Bare-collection create paths were removed → they no longer create (not a 2xx).
        assertRejected(post(prefix + "/zones").content(zoneBody())
                        .header("X-Environment-Fingerprint", BROKER_FINGERPRINT),
                "bare POST zones no longer creates");
        assertRejected(post(prefix + "/zones/zone1/contexts").content(contextBody())
                        .header("X-Environment-Fingerprint", BROKER_FINGERPRINT),
                "bare POST zones/{zoneId}/contexts no longer creates");
    }

    // ===================== Helpers =====================

    private void assertStatus(MockHttpServletRequestBuilder builder, int expected, String description)
            throws Exception {
        int status = mockMvc.perform(prepare(builder)).andReturn().getResponse().getStatus();
        assertThat(status)
                .as("Preservation: %s should return %d (unchanged on unfixed code)", description, expected)
                .isEqualTo(expected);
    }

    private void assertRejected(MockHttpServletRequestBuilder builder, String description) throws Exception {
        int status = mockMvc.perform(prepare(builder)).andReturn().getResponse().getStatus();
        assertThat(status)
                .as("Preservation: %s — bare-collection POST must not be accepted as a create (no 2xx)",
                        description)
                .isGreaterThanOrEqualTo(400);
    }

    private MockHttpServletRequestBuilder prepare(MockHttpServletRequestBuilder builder) {
        return builder
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("requester", requesterEnvironment());
    }

    private Environment requesterEnvironment() {
        Environment env = new Environment();
        env.setId(ENV_ID);
        env.setDefaultZoneId("default-zone");
        return env;
    }

    // ===================== Request bodies =====================

    private String namespaceBody() {
        return "{\"namespace\":{\"zone\":\"default-zone\",\"uri\":\"urn:sif:ns\","
                + "\"url\":\"https://example.com/schema.xsd\"}}";
    }

    private String alertBody() {
        return "{\"alert\":{\"reporter\":\"TestApp\",\"exchange\":\"REQUEST\",\"level\":\"ERROR\"}}";
    }

    private String codeSetBody() {
        return "{\"codeSet\":{\"zone\":\"default-zone\",\"version\":\"1.0\","
                + "\"timestamp\":\"2024-01-01T00:00:00Z\"}}";
    }

    private String zoneBody() {
        return "{\"zone\":{\"id\":\"zone1\",\"description\":\"test zone\"}}";
    }

    private String contextBody() {
        return "{\"context\":{\"id\":\"ctx1\",\"description\":\"test context\"}}";
    }

    // ===================== Sample service responses =====================

    private SifEnvironmentResponse sampleEnvResponse() {
        return new SifEnvironmentResponse(
                UUID.randomUUID(),
                EnvironmentType.BROKERED,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                null,
                new DefaultZoneDTO("default-zone"),
                "SIF_HMACSHA256",
                null,
                null,
                "TestApp",
                null,
                new InfrastructureServicesDTO(List.of()),
                new ProvisionedZonesDTO(List.of()));
    }

    private XQueryTemplateDTO sampleXQueryDto() {
        return new XQueryTemplateDTO(
                "StudentSnapshot", "SINGULAR", "APPROVED", null,
                "Retrieve student snapshot", "SELECT * FROM students", new XQueryParametersDTO(List.of()), "urn:sif:student");
    }

    private ProvisionRequestResponseDTO sampleProvisionResponse() {
        return new ProvisionRequestResponseDTO(UUID.randomUUID(), "ACCEPTED",
                new ProvisionedZonesDTO(List.of()));
    }

    private NamespaceDTO sampleNamespaceDto() {
        return new NamespaceDTO(UUID.randomUUID().toString(), "default-zone", "urn:sif:ns",
                "https://example.com/schema.xsd");
    }

    private AlertDTO sampleAlertDto() {
        return new AlertDTO(UUID.randomUUID(), "TestApp", null, Exchange.REQUEST, AlertLevel.ERROR,
                null, null, null, null, null, null, null, null, null);
    }

    private CodeSetDTO sampleCodeSetDto() {
        return new CodeSetDTO(UUID.randomUUID().toString(), "default-zone", "1.0",
                "2024-01-01T00:00:00Z", null, null);
    }

    private ZoneDTO sampleZoneDto() {
        return new ZoneDTO(UUID.randomUUID(), "test-solution", "zone1", "test zone", Instant.now());
    }

    private ContextDTO sampleContextDto() {
        return new ContextDTO(UUID.randomUUID(), "zone1", "ctx1", "test context", Instant.now());
    }

    // ===================== Arbitraries =====================

    @Provide
    Arbitrary<String> domainIds() {
        return Arbitraries.of("test-domain", "school-district-1", "state-edu", "prod-domain");
    }

    @Provide
    Arbitrary<String> solutionIds() {
        return Arbitraries.of("prod-solution", "test-solution", "staging", "dev-env");
    }
}
