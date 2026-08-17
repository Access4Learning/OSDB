// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Self-test for the SIF conformance harness.
 *
 * <p>This runs the specification's own canonical examples through the validator. Every one must
 * validate clean. Without this, a bug in the harness would surface as a false conformance
 * finding against the broker, and we would "fix" production code to satisfy a broken check.
 * The harness has to be trustworthy before any finding it produces means anything.</p>
 */
@DisplayName("SIF conformance harness self-test")
class SifConformanceHarnessSelfTest {

    @Nested
    @DisplayName("Schema validation against canonical specification examples")
    class CanonicalExamples {

        /**
         * Every PESC-notation example in {@code examples_IN_3.7.yaml} must validate against the
         * authoritative create schema.
         *
         * <p>Only the PESC examples are used. The Goessner variants attribute-prefix their keys
         * ({@code @id}, {@code #text}) and the XML variants are not JSON, so neither is a valid
         * subject for JSON Schema validation.</p>
         */
        /**
         * Examples excluded from the clean-validation check, with the reason.
         *
         * <p>{@code pod}: the specification's own example violates the specification's own
         * schema. Several {@code format: uri} fields carry empty strings
         * ({@code trainingURL}, {@code standardURL}) or scheme-less hostnames
         * ({@code www.ceo.priv.policy.edu.au}), none of which are RFC 3986 URIs. This is a
         * defect in the published example, not in the harness, and {@code pod} is not an object
         * this broker exposes. The exclusion is deliberately limited to this one key so that a
         * future example regression elsewhere still fails the build.</p>
         */
        private static final Set<String> KNOWN_INVALID_EXAMPLES = Set.of("pod");

        @Test
        @DisplayName("all PESC examples validate clean against the create schema")
        void pescExamplesValidateClean() {
            JsonNode objectExamples = SifSchemaRegistry.examplesDocument().get("objectExamples");
            assertThat(objectExamples)
                    .as("examples_IN_3.7.yaml must contain an objectExamples block")
                    .isNotNull();

            List<String> failures = new ArrayList<>();
            int validated = 0;

            for (Map.Entry<String, JsonNode> entry : iterable(objectExamples)) {
                String definitionName = entry.getKey();
                if (KNOWN_INVALID_EXAMPLES.contains(definitionName)) {
                    continue;
                }
                JsonNode pescValue = entry.getValue().path("pesc").path("value");
                if (!pescValue.isObject()) {
                    continue;
                }
                if (!SifSchemaRegistry.hasDefinition(SifSchemaValidator.CREATE, definitionName)) {
                    // Some examples cover data-model objects that the infrastructure create
                    // schema does not define. Those are out of this harness's scope.
                    continue;
                }
                validated++;
                List<String> violations = SifSchemaValidator.validateEnvelope(definitionName, pescValue);
                if (!violations.isEmpty()) {
                    failures.add(definitionName + " -> " + violations);
                }
            }

            assertThat(validated)
                    .as("the harness must actually exercise a meaningful number of canonical "
                            + "examples; a near-zero count means the examples file is not being read")
                    .isGreaterThanOrEqualTo(5);
            assertThat(failures)
                    .as("canonical specification examples must validate clean; a failure here is "
                            + "a harness bug, not a broker defect")
                    .isEmpty();
        }

        @Test
        @DisplayName("the provider example exercises the nesting the broker gets wrong")
        void providerExampleHasTheExpectedNesting() {
            JsonNode provider = SifSchemaRegistry.examplesDocument()
                    .path("objectExamples").path("provider").path("pesc").path("value")
                    .path("provider");

            // Guards the harness against a silently-empty example: these are precisely the
            // shapes D1 is about, so if the canonical example ever stops carrying them the
            // provider assertions elsewhere would pass for the wrong reason.
            assertThat(provider.path("providerName").isTextual()).isTrue();
            assertThat(provider.path("querySupport").isObject()).isTrue();
            assertThat(provider.path("endPoint").path("location").isTextual()).isTrue();
            assertThat(provider.path("supportedRequestTypes").path("supportedRequestType").isArray())
                    .isTrue();
            assertThat(provider.path("supportedRequestTypes").path("supportedRequestType")
                    .get(0).path("operations").path("operation").isArray()).isTrue();
        }
    }

    @Nested
    @DisplayName("Envelope detection")
    class EnvelopeDetection {

        @Test
        @DisplayName("a body missing its envelope key is reported")
        void unwrappedBodyIsReported() {
            JsonNode unwrapped = SifSchemaValidator.parse(
                    "{\"consumerName\":\"Example\",\"type\":\"DIRECT\"}");

            List<String> violations = SifSchemaValidator.validateEnvelope("environment", unwrapped);

            assertThat(violations)
                    .anySatisfy(v -> assertThat(v).contains("required envelope key [environment]"));
        }

        @Test
        @DisplayName("a correctly wrapped body is accepted")
        void wrappedBodyIsAccepted() {
            JsonNode wrapped = SifSchemaValidator.parse(
                    "{\"environment\":{\"consumerName\":\"Example\",\"type\":\"DIRECT\"}}");

            assertThat(SifSchemaValidator.validateEnvelope("environment", wrapped)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Explicit null detection")
    class ExplicitNulls {

        @Test
        @DisplayName("nested and array-element nulls are all found")
        void findsNullsAtEveryDepth() {
            JsonNode body = SifSchemaValidator.parse("""
                    {
                      "codeSet": {
                        "id": "abc",
                        "source": null,
                        "codeItems": { "codeItem": [ { "code": "A", "description": null } ] }
                      }
                    }
                    """);

            List<String> nulls = SifSchemaValidator.findExplicitNulls(body);

            assertThat(nulls).containsExactlyInAnyOrder(
                    "#/codeSet/source",
                    "#/codeSet/codeItems/codeItem[0]/description");
        }

        @Test
        @DisplayName("a body with no nulls reports none")
        void reportsNothingWhenClean() {
            JsonNode body = SifSchemaValidator.parse("{\"codeSet\":{\"id\":\"abc\"}}");

            assertThat(SifSchemaValidator.findExplicitNulls(body)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Header and status declarations")
    class HeaderDeclarations {

        @Test
        @DisplayName("required-ness is read from the specification's own prose")
        void requirementsMatchTheSpecification() {
            Map<String, SifHeaderValidator.Requirement> requirements =
                    SifHeaderValidator.responseHeaderRequirements();

            assertThat(requirements.get("messageId")).isEqualTo(SifHeaderValidator.Requirement.ALWAYS);
            assertThat(requirements.get("messageType")).isEqualTo(SifHeaderValidator.Requirement.ALWAYS);
            assertThat(requirements.get("responseAction")).isEqualTo(SifHeaderValidator.Requirement.ALWAYS);
            assertThat(requirements.get("timestamp")).isEqualTo(SifHeaderValidator.Requirement.ALWAYS);

            assertThat(requirements.get("relativeServicePath"))
                    .isEqualTo(SifHeaderValidator.Requirement.DELAYED_ONLY);
            assertThat(requirements.get("requestId"))
                    .isEqualTo(SifHeaderValidator.Requirement.DELAYED_ONLY);

            assertThat(requirements.get("serviceType")).isEqualTo(SifHeaderValidator.Requirement.OPTIONAL);
            assertThat(requirements.get("vary")).isEqualTo(SifHeaderValidator.Requirement.OPTIONAL);
            assertThat(requirements.get("content-profile")).isEqualTo(SifHeaderValidator.Requirement.OPTIONAL);
        }

        @Test
        @DisplayName("queueMessageGet declares 410 but not 404")
        void queueMessageGetStatuses() {
            Set<Integer> statuses = SifHeaderValidator.declaredStatuses(SifHeaderValidator.QUEUE_MESSAGE_GET);

            assertThat(statuses).contains(200, 204, 410);
            assertThat(statuses)
                    .as("404 is not declared for queueMessageGet; this is the basis of D7")
                    .doesNotContain(404);
        }

        @Test
        @DisplayName("queueMessageDelete declares 204 and 410 but neither 404 nor 202")
        void queueMessageDeleteStatuses() {
            Set<Integer> statuses = SifHeaderValidator.declaredStatuses(SifHeaderValidator.QUEUE_MESSAGE_DELETE);

            assertThat(statuses).contains(204, 410);
            assertThat(statuses).doesNotContain(404, 202);
        }

        @Test
        @DisplayName("missing required headers are reported for declaring operations")
        void reportsMissingHeadersForQueueMessageGet() {
            List<String> missing = SifHeaderValidator.findMissingHeaders(
                    SifHeaderValidator.QUEUE_MESSAGE_GET, 200,
                    Set.of("content-type", "messageId", "messageType", "serviceType", "vary"),
                    false);

            assertThat(missing).containsExactlyInAnyOrder("responseAction", "timestamp");
        }

        @Test
        @DisplayName("nothing is reported for operations that declare no headers")
        void reportsNothingForNonDeclaringOperations() {
            List<String> missing = SifHeaderValidator.findMissingHeaders(
                    "operationThatDoesNotExist", 201, Set.of("content-type"), false);

            assertThat(missing)
                    .as("infrastructure object CRUD declares no response headers, so adding SIF "
                            + "message headers there would be invention rather than conformance")
                    .isEmpty();
        }
    }

    private static Iterable<Map.Entry<String, JsonNode>> iterable(JsonNode objectNode) {
        List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
        objectNode.fields().forEachRemaining(entries::add);
        return entries;
    }
}
