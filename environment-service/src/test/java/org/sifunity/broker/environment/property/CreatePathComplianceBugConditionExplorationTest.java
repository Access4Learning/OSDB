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
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.controller.EnvironmentController;
import org.sifunity.broker.environment.controller.ProviderController;
import org.sifunity.broker.environment.controller.ProvisionRequestController;
import org.sifunity.broker.environment.controller.XQueryTemplateController;
import org.sifunity.broker.environment.domain.Environment;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.InfrastructureServicesDTO;
import org.sifunity.broker.environment.dto.ProviderRegistrationRequest;
import org.sifunity.broker.environment.dto.ProvisionRequestCreateDTO;
import org.sifunity.broker.environment.dto.ProvisionRequestResponseDTO;
import org.sifunity.broker.environment.dto.ProvisionedZonesDTO;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.SifEnvironmentResponse;
import org.sifunity.broker.environment.dto.XQueryParametersDTO;
import org.sifunity.broker.environment.dto.XQueryTemplateBody;
import org.sifunity.broker.environment.dto.XQueryTemplateCreateRequest;
import org.sifunity.broker.environment.dto.XQueryTemplateDTO;
import org.sifunity.broker.environment.service.AuthorizationService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.ProviderService;
import org.sifunity.broker.environment.service.ProvisionRequestService;
import org.sifunity.broker.environment.service.UserEnvironmentAccessService;
import org.sifunity.broker.environment.service.XQueryTemplateService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.ProviderRegistrationBody;

/**
 * Bug Condition Exploration Test for SIF 3.7 create-path compliance.
 *
 * <p><strong>CRITICAL: These tests MUST FAIL on unfixed code — failure confirms the bug exists.</strong></p>
 *
 * <p>Bug: Four environment-service create endpoints violate the SIF 3.7
 * {@code POST /{collection}/{singular}} convention. They map {@code @PostMapping} on the
 * bare collection path, so the create lands on {@code POST /{collection}} and the
 * singular-subpath create does not exist. Additionally the provider resource is exposed
 * under the legacy collection name {@code provider-registrations} instead of the SIF name
 * {@code providers}.</p>
 *
 * <p>This test encodes the EXPECTED (post-fix) behavior using a standalone MockMvc against
 * the UNFIXED controllers, scoped over the finite set of affected resources
 * {@code {environment, xquery, provisionRequest, provider}} ({@code isBugCondition} in design):</p>
 * <ul>
 *   <li>The singular-subpath create is accepted (HTTP 201).</li>
 *   <li>The bare-collection POST is rejected (HTTP 405 Method Not Allowed).</li>
 * </ul>
 *
 * <p><strong>Expected on UNFIXED code:</strong> FAILS. Singular-subpath POSTs return 405/404
 * (no create mapping there), bare-collection POSTs return 201 (still accepted as create), and
 * the {@code providers} collection returns 404 (controller still mapped at
 * {@code provider-registrations}).</p>
 *
 * <p><strong>Expected on FIXED code:</strong> PASSES. Singular create returns 201 and bare
 * collection returns 405.</p>
 *
 * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2, 2.3, 2.4, 2.5</strong></p>
 */
class CreatePathComplianceBugConditionExplorationTest {

    private static final UUID ENV_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeTry
    void setUp() {
        // --- Mock services so that, IF the SIF-compliant mappings existed, create returns 201 ---
        EnvironmentService environmentService = mock(EnvironmentService.class);
        UserEnvironmentAccessService userEnvironmentAccessService = mock(UserEnvironmentAccessService.class);
        XQueryTemplateService xQueryTemplateService = mock(XQueryTemplateService.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        ProvisionRequestService provisionRequestService = mock(ProvisionRequestService.class);
        ProviderService providerService = mock(ProviderService.class);

        when(environmentService.registerEnvironment(any(), any(EnvironmentRegistrationRequest.class)))
                .thenReturn(sampleRegistrationResponse());

        when(authorizationService.isAdmin(any())).thenReturn(true);
        when(xQueryTemplateService.create(any(), any(), any(), any(XQueryTemplateCreateRequest.class)))
                .thenReturn(sampleXQueryDto());

        when(provisionRequestService.createProvisionRequest(
                any(), any(ProvisionRequestCreateDTO.class), any(Environment.class)))
                .thenReturn(sampleProvisionResponse());

        when(providerService.registerProvider(any(), any(), any(ProviderRegistrationRequest.class)))
                .thenReturn(sampleProviderDto());

        // --- Standalone MockMvc so Spring MVC routing resolves 404 / 405 correctly ---
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new EnvironmentController(environmentService, userEnvironmentAccessService),
                        new XQueryTemplateController(xQueryTemplateService, authorizationService),
                        new ProvisionRequestController(provisionRequestService),
                        new ProviderController(providerService, null, null))
                .build();
    }

    /**
     * Property 1 (a): the singular-subpath create SHALL be accepted with HTTP 201.
     *
     * <p>For each affected resource, POST to {@code /{collection}/{singular}} must return 201.</p>
     *
     * <p><strong>Expected on UNFIXED code:</strong> FAILS — the singular subpath has no create
     * mapping (returns 405 for environment/xquery/provisionRequest, 404 for provider).</p>
     *
     * <p><strong>Validates: Requirements 2.1, 2.2, 2.3, 2.4</strong></p>
     */
    @Property(tries = 20)
    @Label("Singular create POST /{collection}/{singular} should return 201 for affected resources")
    void singularSubpathCreateShouldReturn201(
            @ForAll("affectedResources") String resource,
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        int status = perform(singularPath(resource, domainId, solutionId), bodyFor(resource, solutionId));

        assertThat(status)
                .as("SIF 3.7 singular create POST %s for resource '%s' should return 201",
                        singularPath(resource, domainId, solutionId), resource)
                .isEqualTo(201);
    }

    /**
     * Property 1 (b): the bare-collection POST SHALL be rejected with HTTP 405.
     *
     * <p>For each affected resource, POST to the bare {@code /{collection}} must return 405.</p>
     *
     * <p><strong>Expected on UNFIXED code:</strong> FAILS — the bare collection still accepts
     * the create (returns 201 for environment/xquery/provisionRequest, 404 for the
     * not-yet-renamed providers collection).</p>
     *
     * <p><strong>Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 2.5</strong></p>
     */
    @Property(tries = 20)
    @Label("Bare-collection POST /{collection} should return 405 for affected resources")
    void bareCollectionPostShouldReturn405(
            @ForAll("affectedResources") String resource,
            @ForAll("domainIds") String domainId,
            @ForAll("solutionIds") String solutionId) throws Exception {

        int status = perform(barePath(resource, domainId, solutionId), bodyFor(resource, solutionId));

        assertThat(status)
                .as("SIF 3.7 bare-collection POST %s for resource '%s' should return 405 (Method Not Allowed)",
                        barePath(resource, domainId, solutionId), resource)
                .isEqualTo(405);
    }

    // ========== Path construction ==========

    private String base(String resource, String domainId, String solutionId) {
        String prefix = "/" + domainId + "/" + solutionId;
        return switch (resource) {
            case "environment" -> prefix + "/environments";
            case "xquery" -> prefix + "/xquerys";
            case "provisionRequest" -> prefix + "/environments/" + ENV_ID + "/provisionRequests";
            case "provider" -> prefix + "/providers";
            default -> throw new IllegalArgumentException("Unknown resource: " + resource);
        };
    }

    private String singularPath(String resource, String domainId, String solutionId) {
        String singular = switch (resource) {
            case "environment" -> "/environment";
            case "xquery" -> "/xquery";
            case "provisionRequest" -> "/provisionRequest";
            case "provider" -> "/provider";
            default -> throw new IllegalArgumentException("Unknown resource: " + resource);
        };
        return base(resource, domainId, solutionId) + singular;
    }

    private String barePath(String resource, String domainId, String solutionId) {
        return base(resource, domainId, solutionId);
    }

    // ========== Request execution ==========

    private int perform(String path, String body) throws Exception {
        MockHttpServletRequestBuilder builder = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                // requester is required by the xquery and provisionRequest create handlers;
                // harmless for the others.
                .requestAttr("requester", requesterEnvironment());
        return mockMvc.perform(builder).andReturn().getResponse().getStatus();
    }

    private Environment requesterEnvironment() {
        Environment env = new Environment();
        env.setId(ENV_ID);
        env.setDefaultZoneId("default-zone");
        return env;
    }

    private String bodyFor(String resource, String solutionId) {
        try {
            return switch (resource) {
                case "environment" -> objectMapper.writeValueAsString(new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody("TestApp", EnvironmentType.BROKERED, new DefaultZoneDTO("default-zone"), solutionId, null)));
                case "xquery" -> objectMapper.writeValueAsString(new XQueryTemplateCreateRequest(new XQueryTemplateBody(
                        "StudentSnapshot", "SINGULAR", "APPROVED", null,
                        "Retrieve student snapshot", "SELECT * FROM students", new XQueryParametersDTO(List.of()), "urn:sif:student")));
                case "provisionRequest" -> objectMapper.writeValueAsString(
                        new ProvisionRequestCreateDTO(new ProvisionedZonesDTO(List.of())));
                case "provider" -> objectMapper.writeValueAsString(new ProviderRegistrationRequest(new ProviderRegistrationBody(
                UUID.randomUUID(),
                null,
                ServiceType.OBJECT,
                null,
                null,
                UUID.randomUUID(),
                ProtocolDTO.of("https://api.example.com/students"),
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY))))))));
                default -> throw new IllegalArgumentException("Unknown resource: " + resource);
            };
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ========== Sample service responses (so a compliant mapping would return 201) ==========

    private SifEnvironmentRegistrationResponse sampleRegistrationResponse() {
        SifEnvironmentResponse env = new SifEnvironmentResponse(
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
        return SifEnvironmentRegistrationResponse.of(env, "base64-encoded-shared-secret");
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

    private ProviderRegistrationDTO sampleProviderDto() {
        return new ProviderRegistrationDTO(
                UUID.randomUUID(),
                ENV_ID,
                "StudentPersonal",
                "default",
                "context1",
                null,
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("https://api.example.com/students"),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY))))),
                Instant.now()
        );
    }

    // ========== Arbitraries ==========

    @Provide
    Arbitrary<String> affectedResources() {
        return Arbitraries.of("environment", "xquery", "provisionRequest", "provider");
    }

    @Provide
    Arbitrary<String> domainIds() {
        return Arbitraries.of("test-domain", "school-district-1", "state-edu", "prod-domain");
    }

    @Provide
    Arbitrary<String> solutionIds() {
        return Arbitraries.of("prod-solution", "test-solution", "staging", "dev-env");
    }
}
