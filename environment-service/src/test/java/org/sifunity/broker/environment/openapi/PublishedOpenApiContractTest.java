// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts that the <em>published</em> OpenAPI document matches the reshaped implementation.
 *
 * <p>Requirement 2.20 is about the published contract, and nothing else in the suite reads it. The
 * controllers carry no {@code @Schema} overrides, so springdoc derives every schema from the DTO
 * records by reflection — which means the wrapped shapes are published automatically <em>and</em> that
 * a future flattening of a DTO would silently change the contract with no test objecting. This reads
 * {@code /v3/api-docs} and pins the parts this spec changed.</p>
 *
 * <p>The springdoc maven plugin that would generate the document at build time is disabled
 * ({@code springdoc.generate.skip=true}), so the document only exists at runtime. MockMvc reaches it
 * without binding a socket.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Published OpenAPI contract")
class PublishedOpenApiContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode apiDocs;

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    static void resetCache() {
        apiDocs = null;
    }

    private JsonNode docs() throws Exception {
        if (apiDocs == null) {
            String body = mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            apiDocs = MAPPER.readTree(body);
        }
        return apiDocs;
    }

    /** Resolves a $ref like "#/components/schemas/ProviderResponse" to its schema node. */
    private JsonNode schema(String name) throws Exception {
        JsonNode node = docs().path("components").path("schemas").path(name);
        assertThat(node.isMissingNode())
                .as("schema %s should be published; springdoc derives it from the record", name)
                .isFalse();
        return node;
    }

    private JsonNode properties(String schemaName) throws Exception {
        return schema(schemaName).path("properties");
    }

    @Test
    @DisplayName("the document is served and contains the SIF endpoints")
    void documentIsServed() throws Exception {
        assertThat(docs().path("paths").isObject()).isTrue();
        assertThat(docs().path("paths").fieldNames()).isNotNull();
    }

    @Test
    @DisplayName("D1: the provider request is wrapped and its endpoint is an object")
    void providerRequestIsWrapped() throws Exception {
        assertThat(properties("ProviderRegistrationRequest").fieldNames())
                .toIterable()
                .as("the request must expose only the `provider` envelope")
                .containsExactly("provider");

        JsonNode body = properties("ProviderRegistrationBody");
        assertThat(body.fieldNames()).toIterable()
                .as("endPoint replaces the flat endpoint string, and providerName is not settable")
                .contains("endPoint", "supportedRequestTypes")
                .doesNotContain("endpoint", "providerName", "querySupport");
    }

    @Test
    @DisplayName("D1/task 10a: the provider update request is wrapped the same way as create")
    void providerUpdateRequestIsWrapped() throws Exception {
        assertThat(properties("ProviderUpdateRequest").fieldNames())
                .toIterable()
                .as("update and create must publish the same envelope")
                .containsExactly("provider");

        assertThat(properties("ProviderUpdateBody").fieldNames())
                .toIterable()
                .contains("endPoint", "supportedRequestTypes")
                .doesNotContain("endpoint");
    }

    @Test
    @DisplayName("D1: operations and supportedRequestTypes are nested objects, not bare arrays")
    void nestedValueTypesArePublished() throws Exception {
        assertThat(properties("SupportedRequestsDTO").fieldNames())
                .toIterable().containsExactly("supportedRequestType");
        assertThat(schema("SupportedRequestsDTO").path("properties")
                .path("supportedRequestType").path("type").asText())
                .isEqualTo("array");

        assertThat(properties("OperationsDTO").fieldNames())
                .toIterable().containsExactly("operation");

        assertThat(properties("SupportedRequestType").fieldNames())
                .toIterable()
                .as("operations must reference the wrapper, not be an inline array")
                .contains("operations");
        assertThat(properties("SupportedRequestType").path("operations").path("type").asText())
                .as("an inline `array` here would mean the bare pre-D1 form is published")
                .isNotEqualTo("array");
    }

    @Test
    @DisplayName("D1: the provider response carries providerName and endPoint, never consumerName")
    void providerResponseShape() throws Exception {
        JsonNode props = properties("ProviderRegistrationDTO");
        assertThat(props.fieldNames()).toIterable()
                .contains("providerName", "endPoint", "querySupport", "supportedRequestTypes")
                .doesNotContain("consumerName", "endpoint");
    }

    @Test
    @DisplayName("D2/D3: the environment request is wrapped and its zone is an object")
    void environmentRequestIsWrapped() throws Exception {
        assertThat(properties("EnvironmentRegistrationRequest").fieldNames())
                .toIterable().containsExactly("environment");

        JsonNode body = properties("EnvironmentRegistrationBody");
        assertThat(body.fieldNames()).toIterable()
                .contains("consumerName", "defaultZone")
                .as("applicationName was renamed and defaultZoneId became an object")
                .doesNotContain("applicationName", "defaultZoneId");
    }

    @Test
    @DisplayName("D6: the xquery template request is wrapped")
    void xqueryRequestIsWrapped() throws Exception {
        assertThat(properties("XQueryTemplateCreateRequest").fieldNames())
                .toIterable().containsExactly("xquery");
    }

    /** The published response codes for one path+method. */
    private java.util.Set<String> declaredStatuses(String path, String method) throws Exception {
        JsonNode operation = docs().path("paths").path(path).path(method);
        assertThat(operation.isMissingNode())
                .as("%s %s should be published", method.toUpperCase(), path)
                .isFalse();
        java.util.Set<String> codes = new java.util.TreeSet<>();
        operation.path("responses").fieldNames().forEachRemaining(codes::add);
        return codes;
    }

    @Test
    @DisplayName("2.20: every status the provider endpoints can return is declared")
    void providerStatusesAreDeclared() throws Exception {
        String base = "/{domainId}/{solutionId}/providers";

        assertThat(declaredStatuses(base + "/provider", "post"))
                .as("the statuses ProviderController's own javadoc documents")
                .containsExactly("201", "400", "404", "409", "500");

        assertThat(declaredStatuses(base + "/lookup", "get"))
                .containsExactly("200", "400", "404", "500");

        assertThat(declaredStatuses(base + "/{id}", "put"))
                .containsExactly("200", "400", "404", "500");

        assertThat(declaredStatuses(base + "/{id}", "delete"))
                .containsExactly("204", "400", "404", "500");

        assertThat(declaredStatuses(base, "get"))
                .containsExactly("200", "400", "500");

        assertThat(declaredStatuses(base + "/available-options", "get"))
                .containsExactly("200", "400", "500");

        assertThat(declaredStatuses(base, "post"))
                .as("POST to the collection URL is a 405 by design, not a 201")
                .containsExactly("405");
    }

    @Test
    @DisplayName("2.20: every status the environment endpoints can return is declared")
    void environmentStatusesAreDeclared() throws Exception {
        String base = "/{domainId}/{solutionId}/environments";

        assertThat(declaredStatuses(base + "/environment", "post"))
                .as("404 is reachable — confirmed by "
                    + "ForeignKeyConstraintIntegrationTest.testRegisterEnvironmentWithNonExistentSolutionReturnsNotFound")
                .containsExactly("201", "400", "404", "500");

        assertThat(declaredStatuses(base + "/{id}", "get"))
                .containsExactly("200", "400", "404", "500");
        assertThat(declaredStatuses(base + "/{id}", "put"))
                .containsExactly("200", "400", "404", "500");
        assertThat(declaredStatuses(base + "/{id}", "delete"))
                .containsExactly("204", "400", "404", "500");
        assertThat(declaredStatuses(base, "get"))
                .containsExactly("200", "400", "500");
        assertThat(declaredStatuses(base, "post"))
                .containsExactly("405");
    }

    @Test
    @DisplayName("D6: every status the xquery endpoints can return is declared")
    void xqueryStatusesAreDeclared() throws Exception {
        String base = "/{domainId}/{solutionId}/xquerys";

        assertThat(declaredStatuses(base + "/xquery", "post"))
                .as("409 covers a duplicate query token")
                .containsExactly("201", "400", "404", "409", "500");

        assertThat(declaredStatuses(base, "get"))
                .as("this endpoint really does return 204 for an empty list")
                .containsExactly("200", "204", "400", "500");

        assertThat(declaredStatuses(base + "/{queryToken}", "delete"))
                .containsExactly("204", "400", "403", "404", "500");
    }

    @Test
    @DisplayName("2.20: declared error responses publish the real error envelope")
    void errorResponsesPublishTheErrorEnvelope() throws Exception {
        JsonNode notFound = docs().path("paths")
                .path("/{domainId}/{solutionId}/providers/provider").path("post")
                .path("responses").path("404")
                .path("content").path("application/json").path("schema").path("$ref");

        assertThat(notFound.asText())
                .as("an error response with no schema would document a shape callers cannot rely on")
                .endsWith("SifErrorResponseEnvelope");
    }
}
