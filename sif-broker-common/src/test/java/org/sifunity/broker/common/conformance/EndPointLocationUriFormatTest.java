// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.OperationsDTO;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.QuerySupportDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@code endPoint.location}'s {@code format: uri} conformance directly against the schema.
 *
 * <p>Task 20 requires this as a unit test rather than as part of the certification re-audit: Bruno
 * redacts the dotenv-sourced gateway URL, so the value never appears in the report and the audit cannot
 * see whether it is a valid URI. Nothing else in the suite checks it.</p>
 *
 * <p>The SIF {@code protocol} definition types {@code location} as
 * {@code {"type": "string", "format": "uri"}} and requires it. {@code SifSchemaRegistry} sets
 * {@code formatAssertionsEnabled(false)}, but the draft-07 metaschema asserts {@code format}
 * unconditionally, so format violations <em>are</em> reported — which is what makes this test possible
 * at all. The negative cases below exist to prove that; without them a change to the validator's
 * configuration could silently turn every assertion here into a no-op.</p>
 */
@DisplayName("endPoint.location URI-format conformance")
class EndPointLocationUriFormatTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules();

    /** A provider carrying every schema-required field, so only `location` is under test. */
    private static ProviderRegistrationDTO providerWithEndpoint(ProtocolDTO endPoint) {
        return new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "StudentPersonal",
                "district-zone",
                "DEFAULT",
                "RamseySIS",
                QuerySupportDTO.unspecified(),
                endPoint,
                ServiceType.OBJECT,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(
                        RequestType.IMMEDIATE, OperationsDTO.of(List.of(Operation.QUERY))))),
                Instant.parse("2026-01-15T10:00:00Z"));
    }

    private static List<String> violationsFor(ProtocolDTO endPoint) {
        JsonNode node = MAPPER.valueToTree(providerWithEndpoint(endPoint));
        return SifSchemaValidator.validate("provider", node);
    }

    @Nested
    @DisplayName("conformant locations")
    class Conformant {

        @Test
        @DisplayName("an absolute https URL is accepted — this is what the broker emits")
        void absoluteHttpsUrl() {
            assertThat(violationsFor(ProtocolDTO.of("https://sis.example.org/StudentPersonals")))
                    .isEmpty();
        }

        @Test
        @DisplayName("an absolute http URL with a port and path is accepted")
        void absoluteHttpUrlWithPort() {
            assertThat(violationsFor(ProtocolDTO.of("http://localhost:9091/StudentPersonals")))
                    .as("the form the Bruno suites and the test provider actually use")
                    .isEmpty();
        }

        @Test
        @DisplayName("KNOWN GAP: a scheme-less host:port/path also passes, because the colon reads as a scheme")
        void schemeLessHostPortIsNotCaught() {
            // Recorded as a limitation, not asserted as desirable. "localhost:9091/StudentPersonals"
            // is a syntactically valid URI under RFC 3986 -- "localhost" parses as the scheme and
            // "9091/StudentPersonals" as an opaque path -- so `format: uri` cannot reject it. Anyone
            // expecting the schema to catch a missing http:// will not get that from this field.
            // ProviderRegistrationBody.isEndPointLocationValid() is what actually enforces http/https
            // on the way in; the schema alone is not sufficient.
            assertThat(violationsFor(new ProtocolDTO("localhost:9091/StudentPersonals", null)))
                    .as("if this ever becomes non-empty the schema got stricter, which is fine -- "
                        + "update this test rather than assume a regression")
                    .isEmpty();
        }

        @Test
        @DisplayName("an omitted endPoint is accepted — DELAYED-only providers have none")
        void omittedEndPoint() {
            assertThat(violationsFor(null))
                    .as("endPoint is not in the provider definition's required list")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("nonconformant locations (these also prove format is asserted)")
    class Nonconformant {

        @Test
        @DisplayName("a value that is not a URI at all is rejected")
        void notAUri() {
            List<String> violations = violationsFor(new ProtocolDTO("not a uri at all", null));
            assertThat(violations)
                    .as("if this passes, format assertions are off and every positive case above "
                        + "is vacuous")
                    .isNotEmpty();
            assertThat(String.join(" | ", violations)).containsIgnoringCase("location");
        }

        @Test
        @DisplayName("an empty location is rejected")
        void emptyLocation() {
            assertThat(violationsFor(new ProtocolDTO("", null)))
                    .as("the specification's own pod example fails its schema for exactly this reason")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("a bare relative reference with no scheme is rejected")
        void relativeReferenceRejected() {
            assertThat(violationsFor(new ProtocolDTO("sis.example.org/StudentPersonals", null)))
                    .as("no scheme and no colon makes this a relative reference, not a URI")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("a protocol object with no location at all is rejected")
        void missingLocation() {
            List<String> violations = violationsFor(new ProtocolDTO(null, null));
            assertThat(violations)
                    .as("location is in the protocol definition's required list")
                    .isNotEmpty();
        }
    }

    @Nested
    @DisplayName("what ProtocolDTO.of guarantees")
    class FactoryBehaviour {

        @Test
        @DisplayName("of(null) yields no protocol object, so no location can be missing")
        void ofNullYieldsNull() {
            assertThat(ProtocolDTO.of(null)).isNull();
        }

        @Test
        @DisplayName("of() does not validate the URI — that is the schema's job, and it is asserted above")
        void ofDoesNotValidate() {
            // Recorded deliberately: ProtocolDTO.of accepts any string. The guard against a
            // nonconformant location is ProviderRegistrationBody.isEndPointLocationValid() on the way
            // in, plus these schema assertions. of() is not a validation boundary and should not be
            // mistaken for one.
            assertThat(ProtocolDTO.of("not a uri").location()).isEqualTo("not a uri");
        }
    }
}
