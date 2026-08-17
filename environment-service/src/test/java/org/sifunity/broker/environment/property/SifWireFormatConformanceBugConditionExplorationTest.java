// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.conformance.SifSchemaValidator;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.XQueryParameterDTO;
import org.sifunity.broker.environment.dto.XQueryParametersDTO;
import org.sifunity.broker.environment.dto.XQueryTemplateDTO;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;

/**
 * Bug-condition exploration for the SIF 3.7 wire-format defects in environment-service.
 *
 * <p><b>These tests are expected to FAIL before the fix.</b> Each one asserts the behaviour the
 * authoritative schemas in {@code external_docs/3-7-open-api-JSON/3_1/} require, and the current
 * implementation does not provide it. They are the executable statement of the defect: when they
 * pass, D1, D2, D3, D5 and D6 are fixed.</p>
 *
 * <p>Conformance is judged by {@link SifSchemaValidator}, which reads the specification files
 * directly. No expected shape is hard-coded here beyond what the schema itself says, so these
 * assertions cannot drift from the specification.</p>
 *
 * <p>Serialization is exercised with {@link SifWireFormatTestMapper}, which mirrors Spring Boot's
 * configuration. That is deliberate: the defects are in the DTO declarations themselves — missing
 * {@code @JsonInclude}, wrong field names, flat instead of nested — so they reproduce without a
 * Spring context, and the tests stay fast enough to run on every build.</p>
 *
 * <p>The null-suppression properties (D5) live in
 * {@link SifNullSuppressionPropertyTest}. jqwik and Jupiter are separate test engines, and a
 * class holding both {@code @Property} and {@code @Test} methods has its properties silently
 * skipped when Surefire filters by class name — which would have made D5 look green.</p>
 */
@DisplayName("SIF wire-format conformance - bug conditions (environment-service)")
class SifWireFormatConformanceBugConditionExplorationTest {

    private static final ObjectMapper MAPPER = SifWireFormatTestMapper.create();

    // ---------------------------------------------------------------------
    // D2 - environment registration request: envelope and field names
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("D2: a spec-shaped environment create body populates the request DTO")
    void environmentCreateAcceptsSpecShapedBody() throws Exception {
        // Exactly the shape examples_IN_3.7.yaml shows: wrapped under 'environment',
        // consumerName rather than applicationName, defaultZone.id rather than defaultZoneId.
        String specBody = """
                {
                  "environment": {
                    "consumerName": "Example Consumer",
                    "type": "DIRECT",
                    "defaultZone": { "id": "District" },
                    "solutionId": "production"
                  }
                }
                """;

        EnvironmentRegistrationRequest request =
                MAPPER.readValue(specBody, EnvironmentRegistrationRequest.class);

        assertThat(request).isNotNull();
        assertThat(consumerNameOf(request))
                .as("the spec names the consumer 'consumerName' and wraps the body under "
                        + "'environment'; the request DTO must read it from there")
                .isEqualTo("Example Consumer");
        assertThat(defaultZoneIdOf(request))
                .as("the spec carries the default zone as defaultZone.id, not a flat defaultZoneId")
                .isEqualTo("District");
    }

    @Test
    @DisplayName("D2: the environment create body validates against the authoritative schema")
    void environmentCreateBodyIsSchemaConformant() {
        JsonNode body = SifSchemaValidator.parse("""
                {
                  "environment": {
                    "consumerName": "Example Consumer",
                    "type": "DIRECT",
                    "defaultZone": { "id": "District" }
                  }
                }
                """);

        assertThat(SifSchemaValidator.findViolations("environment", body)).isEmpty();
    }

    // ---------------------------------------------------------------------
    // D3 - defaultZone must be omitted, not emitted as { "id": null }
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("D3: a default zone with no id serializes without an explicit null")
    void defaultZoneWithoutIdEmitsNoNull() throws Exception {
        JsonNode node = MAPPER.valueToTree(new DefaultZoneDTO(null));

        assertThat(SifSchemaValidator.findExplicitNulls(node))
                .as("an absent default zone id must be omitted; the schema types 'id' as a "
                        + "string and does not declare it nullable")
                .isEmpty();
    }

    // ---------------------------------------------------------------------
    // D6 - xquery parameters nesting and the 'default' property name
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("D6: xquery parameters serialize as parameters.parameter[]")
    void xqueryParametersAreNested() {
        XQueryTemplateDTO template = new XQueryTemplateDTO(
                "StudentSnapshot", "SINGULAR", "APPROVED", null,
                "Snapshot of a student", "let $x := 1 return $x",
                new XQueryParametersDTO(List.of(new XQueryParameterDTO("studentId", "xs:string", "none", "The student", true))),
                "http://example.org/StudentSnapshot");

        JsonNode node = MAPPER.valueToTree(template);

        assertThat(node.path("parameters").path("parameter").isArray())
                .as("the schema nests parameters as an object with a 'parameter' array, "
                        + "not a bare array")
                .isTrue();
        assertThat(node.path("parameters").path("parameter").get(0).has("default"))
                .as("the schema names the parameter default value 'default', not 'defaultValue'")
                .isTrue();
    }

    @Test
    @DisplayName("D6: a full xquery body validates against the authoritative schema")
    void xqueryBodyIsSchemaConformant() {
        XQueryTemplateDTO template = new XQueryTemplateDTO(
                "StudentSnapshot", "SINGULAR", "APPROVED", null,
                "Snapshot of a student", "let $x := 1 return $x",
                new XQueryParametersDTO(List.of(new XQueryParameterDTO("studentId", "xs:string", null, null, null))),
                "http://example.org/StudentSnapshot");

        JsonNode body = MAPPER.createObjectNode().set("xquery", MAPPER.valueToTree(template));

        assertThat(SifSchemaValidator.findViolations("xquery", body)).isEmpty();
    }

    // ---------------------------------------------------------------------
    // D1 - provider registration response shape
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("D1: a provider response validates against the authoritative schema")
    void providerResponseIsSchemaConformant() {
        // Built through the production DTO on purpose: the assertion is about what the DTO can
        // express, not about a hand-written JSON string.
        ProviderRegistrationDTO provider = sampleProvider();

        JsonNode body = MAPPER.createObjectNode().set("provider", MAPPER.valueToTree(provider));

        // The schema requires contextId, providerName, querySupport, serviceName, serviceType
        // and zoneId, with endPoint.location and supportedRequestTypes.supportedRequestType[].
        assertThat(SifSchemaValidator.findViolations("provider", body)).isEmpty();
    }

    @Test
    @DisplayName("D1: a provider response carries providerName, querySupport and endPoint.location")
    void providerResponseCarriesRequiredFields() {
        JsonNode node = MAPPER.valueToTree(sampleProvider());

        assertThat(node.has("providerName"))
                .as("providerName is required by the schema and is a different concept from the "
                        + "owning environment's consumerName")
                .isTrue();
        assertThat(node.has("querySupport"))
                .as("querySupport is required by the schema")
                .isTrue();
        assertThat(node.path("endPoint").path("location").isTextual())
                .as("the endpoint is an object with a required 'location', not a flat string")
                .isTrue();
        assertThat(node.path("supportedRequestTypes").path("supportedRequestType").isArray())
                .as("supportedRequestTypes nests a 'supportedRequestType' array")
                .isTrue();
        assertThat(node.path("supportedRequestTypes").path("supportedRequestType")
                .get(0).path("operations").path("operation").isArray())
                .as("operations nests an 'operation' array")
                .isTrue();
        assertThat(node.has("consumerName"))
                .as("consumerName is not part of the SIF provider schema and must not be emitted")
                .isFalse();
    }

    // ---------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------

    /**
     * A provider registration with every optional value absent.
     *
     * <p>Absent values are the interesting case for both D1 and D5: they are what the current
     * implementation renders as explicit nulls, and what the schema requires be omitted.</p>
     */
    private static ProviderRegistrationDTO sampleProvider() {
        return new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "StudentPersonal",
                "CamasSchoolDistrict",
                "DEFAULT",
                "LovellSIS",
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://www.example.com/provider/"),
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.IMMEDIATE, OperationsDTO.of(Set.of(Operation.QUERY))))),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    /**
     * Reads the consumer name off the request DTO without compiling against a specific accessor
     * name, so this test states the required behaviour rather than presupposing the shape of the
     * fix. Before the fix the DTO exposes {@code applicationName}; after it, {@code consumerName}
     * under an {@code environment} wrapper.
     */
    private static String consumerNameOf(Object request) {
        return firstNonNullString(request, "consumerName", "applicationName");
    }

    private static String defaultZoneIdOf(Object request) {
        JsonNode node = MAPPER.valueToTree(request);
        JsonNode wrapped = node.has("environment") ? node.get("environment") : node;
        if (wrapped.path("defaultZone").path("id").isTextual()) {
            return wrapped.path("defaultZone").path("id").asText();
        }
        return wrapped.path("defaultZoneId").isTextual() ? wrapped.path("defaultZoneId").asText() : null;
    }

    private static String firstNonNullString(Object request, String... candidateFields) {
        JsonNode node = MAPPER.valueToTree(request);
        JsonNode wrapped = node.has("environment") ? node.get("environment") : node;
        for (String field : candidateFields) {
            if (wrapped.path(field).isTextual()) {
                return wrapped.path(field).asText();
            }
        }
        return null;
    }
}
