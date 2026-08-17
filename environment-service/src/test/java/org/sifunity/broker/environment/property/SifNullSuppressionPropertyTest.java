// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.sifunity.broker.common.conformance.SifSchemaValidator;
import org.sifunity.broker.common.constant.AlertLevel;
import org.sifunity.broker.common.constant.Exchange;
import org.sifunity.broker.common.constant.Operation;
import org.sifunity.broker.common.constant.RequestType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;
import org.sifunity.broker.common.dto.SupportedRequestType;
import org.sifunity.broker.environment.dto.AlertDTO;
import org.sifunity.broker.environment.dto.CodeItemDTO;
import org.sifunity.broker.environment.dto.CodeItemsDTO;
import org.sifunity.broker.environment.dto.CodeSetDTO;
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
 * D5: no SIF-facing response object may serialize an explicit JSON {@code null}.
 *
 * <p>The SIF schemas type their optional properties ({@code type: string}, {@code type: object},
 * {@code type: integer}) without {@code nullable}. An absent optional value must therefore be
 * omitted from the JSON, not rendered as {@code null}.</p>
 *
 * <p><b>Expected to FAIL before the fix.</b> None of these DTOs currently declares
 * {@code @JsonInclude(NON_NULL)}.</p>
 *
 * <p>Kept separate from {@link SifWireFormatConformanceBugConditionExplorationTest} because jqwik
 * and Jupiter are different engines: in a class holding both, Surefire class-name filtering
 * silently skips the {@code @Property} methods, which would report D5 as green without having run
 * anything.</p>
 *
 * <p>Every generator below produces objects whose optional fields are <em>absent</em>. That is the
 * case the certification run exposed, and the only case where the defect is observable.</p>
 */
class SifNullSuppressionPropertyTest {

    private static final ObjectMapper MAPPER = SifWireFormatTestMapper.create();

    @Property(tries = 20)
    void alertsNeverSerializeNulls(@ForAll("alerts") AlertDTO alert) {
        JsonNode node = MAPPER.valueToTree(alert);

        assertThat(SifSchemaValidator.findExplicitNulls(node))
                .as("optional alert fields must be omitted when absent")
                .isEmpty();
    }

    @Property(tries = 20)
    void codeSetsNeverSerializeNulls(@ForAll("codeSets") CodeSetDTO codeSet) {
        JsonNode node = MAPPER.valueToTree(codeSet);

        // The nulls seen in the certification run were a level deeper than the code set itself,
        // inside codeItems.codeItem[]. Annotating only CodeSetDTO would leave them in place.
        assertThat(SifSchemaValidator.findExplicitNulls(node))
                .as("optional code set and code item fields must be omitted when absent")
                .isEmpty();
    }

    @Property(tries = 20)
    void xqueryTemplatesNeverSerializeNulls(@ForAll("xqueryTemplates") XQueryTemplateDTO template) {
        JsonNode node = MAPPER.valueToTree(template);

        assertThat(SifSchemaValidator.findExplicitNulls(node))
                .as("optional xquery fields such as qualifier must be omitted when absent")
                .isEmpty();
    }

    @Property(tries = 20)
    void providerRegistrationsNeverSerializeNulls(
            @ForAll("providerRegistrations") ProviderRegistrationDTO provider) {
        JsonNode node = MAPPER.valueToTree(provider);

        assertThat(SifSchemaValidator.findExplicitNulls(node))
                .as("optional provider fields must be omitted when absent")
                .isEmpty();
    }

    @Provide
    Arbitrary<AlertDTO> alerts() {
        return Arbitraries.of(AlertLevel.values()).flatMap(level ->
                Arbitraries.of(Exchange.values()).map(exchange ->
                        new AlertDTO(
                                UUID.randomUUID(),
                                "CertReporter",
                                null,          // cause
                                exchange,
                                level,
                                null,          // description
                                null,          // messageID
                                null,          // body
                                null,          // error
                                null,          // xpath
                                null,          // category
                                null,          // code
                                null,          // internal
                                null)));       // SIF_ExtendedElements
    }

    @Provide
    Arbitrary<CodeSetDTO> codeSets() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(12).map(id ->
                new CodeSetDTO(
                        id,
                        "CamasSchoolDistrict",
                        "1.0",
                        "2026-01-01T00:00:00Z",
                        null,          // source
                        new CodeItemsDTO(List.of(new CodeItemDTO(
                                "A",
                                null,  // source
                                null,  // namespace
                                "Value A",
                                null,  // description
                                null,  // definition
                                null,  // aliases
                                null,  // action
                                "2026-01-01T00:00:00Z")))));
    }

    @Provide
    Arbitrary<XQueryTemplateDTO> xqueryTemplates() {
        return Arbitraries.of("SINGULAR", "FORMULA", "EXTENDED").map(type ->
                new XQueryTemplateDTO(
                        "StudentSnapshot",
                        type,
                        "APPROVED",
                        null,          // qualifier
                        "A description",
                        "let $x := 1 return $x",
                        new XQueryParametersDTO(List.of(new XQueryParameterDTO("studentId", "xs:string", null, null, null))),
                        "http://example.org/StudentSnapshot"));
    }

    @Provide
    Arbitrary<ProviderRegistrationDTO> providerRegistrations() {
        return Arbitraries.of(ServiceType.values()).map(serviceType ->
                new ProviderRegistrationDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "StudentPersonal",
                "CamasSchoolDistrict",
                null,
                "LovellSIS",
                QuerySupportDTO.unspecified(),
                ProtocolDTO.of("http://www.example.com/provider/"),
                serviceType,
                SupportedRequestsDTO.of(List.of(new SupportedRequestType(RequestType.DELAYED, OperationsDTO.of(Set.of(Operation.CREATE))))),
                Instant.parse("2026-01-01T00:00:00Z")
        ));
    }
}
