// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import org.sifunity.broker.environment.dto.AliasCodeDTO;
import org.sifunity.broker.environment.dto.AliasDTO;
import org.sifunity.broker.environment.dto.AliasesDTO;
import org.sifunity.broker.environment.dto.CodeItemDTO;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;
import org.sifunity.broker.environment.dto.XQueryParameterDTO;
import org.sifunity.broker.environment.dto.XQueryParametersDTO;
import org.sifunity.broker.environment.dto.XQueryTemplateDTO;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preservation properties for {@code sif-wire-format-conformance-fix}.
 *
 * <p><strong>These MUST pass both before and after the fixes.</strong> They are written
 * observation-first against <em>unfixed</em> code, which is why they have to exist before wave 2
 * lands: the moment {@code @JsonInclude(NON_NULL)} is applied, the pre-fix serialization is
 * unobservable and there is nothing left to baseline against.</p>
 *
 * <h2>What these guard, and why it is the real risk</h2>
 *
 * <p>Wave 2's D5 fix suppresses <em>null</em> fields. The plausible over-application is
 * {@code NON_EMPTY} instead of {@code NON_NULL} — it looks tidier and passes every conformance test,
 * because omitting <em>more</em> never violates a schema that only constrains required keys and
 * types. But it silently drops <strong>present</strong> values: an empty string is a value the
 * consumer asked for, and an empty collection is a meaningful "none" distinct from "unknown".
 * Nothing in the bug-condition suite would catch that, since those tests only assert absence of
 * nulls. {@link #emptyStringsAndEmptyCollectionsSurviveSerialization()} is the guard.</p>
 *
 * <p>The second risk is scope creep on D3: {@code defaultZone} must be omitted only when there is
 * <em>no</em> default zone. {@link #defaultZoneWithAnIdIsStillEmitted()} pins the positive case, so a
 * fix that omits the key unconditionally fails here rather than silently dropping a field every
 * client reads.</p>
 *
 * <p>Uses a plain {@link ObjectMapper} deliberately: these assert the DTOs' own serialization
 * contract, independent of any service-level mapper configuration.</p>
 *
 * <p><strong>Validates: Requirements 3.1-3.10 (present-value preservation)</strong></p>
 */
@Tag("sif-wire-format-conformance-fix")
class SifWireFormatConformancePreservationPropertyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ==================== Present-value preservation ====================

    /**
     * A fully-populated XQuery template must round-trip every field. This is the direct guard on
     * D5/D6: suppressing absent fields must not disturb present ones, and renesting
     * {@code parameters} must not lose their contents.
     */
    @Property(tries = 20)
    @Label("Every populated XQuery template field survives serialization")
    void populatedXQueryTemplateFieldsSurvive(
            @ForAll("nonBlank") String id,
            @ForAll("nonBlank") String qualifier,
            @ForAll("nonBlank") String script,
            @ForAll("nonBlank") String paramName,
            @ForAll("nonBlank") String paramDefault) throws Exception {

        XQueryParameterDTO param =
                new XQueryParameterDTO(paramName, "xs:string", paramDefault, "desc", Boolean.TRUE);
        XQueryTemplateDTO template = new XQueryTemplateDTO(
                id, "SINGULAR", "APPROVED", qualifier, "a description", script, new XQueryParametersDTO(List.of(param)), "xs:string");

        JsonNode json = MAPPER.valueToTree(template);

        assertThat(json.path("id").asText()).isEqualTo(id);
        assertThat(json.path("qualifier").asText())
                .as("qualifier is one of the fields D5 suppresses when null — when PRESENT it must remain")
                .isEqualTo(qualifier);
        assertThat(json.path("script").asText()).isEqualTo(script);
        assertThat(json.path("description").asText()).isEqualTo("a description");

        // Located structurally rather than by a fixed path, so this property survives wave 3's
        // renesting of parameters into { "parameter": [ ... ] } (D6).
        JsonNode paramNode = findFirstObjectContaining(json, "name");
        assertThat(paramNode).as("the parameter must still be reachable after any renesting").isNotNull();
        assertThat(paramNode.path("name").asText()).isEqualTo(paramName);
        // D6 (2.13): the wire name is "default" (a Java reserved word, hence @JsonProperty on the
        // component). The preserved property is that a PRESENT default survives; only its key moved.
        assertThat(paramNode.path("default").asText())
                .as("the parameter default is D5-suppressed when null — when PRESENT it must remain, "
                        + "under its schema name 'default'")
                .isEqualTo(paramDefault);
        assertThat(paramNode.has("defaultValue"))
                .as("the non-spec name must not also be emitted — that would be a duplicate field")
                .isFalse();
    }

    /**
     * A fully-populated code item must round-trip, including its nested aliases. {@code codeItem} sits
     * a level deeper than {@code CodeSetDTO}, which the spec calls out as easy to miss.
     */
    @Property(tries = 20)
    @Label("Every populated code item field survives serialization, including nested aliases")
    void populatedCodeItemFieldsSurvive(
            @ForAll("nonBlank") String code,
            @ForAll("nonBlank") String value,
            @ForAll("nonBlank") String namespace,
            @ForAll("nonBlank") String aliasValue) throws Exception {

        AliasesDTO aliases = new AliasesDTO(List.of(
                new AliasDTO(new AliasCodeDTO(Boolean.FALSE, Boolean.TRUE, aliasValue), "a-source", "a-ns")));
        CodeItemDTO item = new CodeItemDTO(
                code, "a-source", namespace, value, "a description", "a definition",
                aliases, null, "2026-01-01T00:00:00Z");

        JsonNode json = MAPPER.valueToTree(item);

        assertThat(json.path("code").asText()).isEqualTo(code);
        assertThat(json.path("value").asText()).isEqualTo(value);
        assertThat(json.path("namespace").asText())
                .as("namespace is D5-suppressed when null — when PRESENT it must remain")
                .isEqualTo(namespace);
        assertThat(json.path("description").asText()).isEqualTo("a description");
        assertThat(json.path("definition").asText()).isEqualTo("a definition");

        JsonNode aliasNode = findFirstObjectContaining(json, "source");
        assertThat(json.path("aliases").isMissingNode())
                .as("aliases is D5-suppressed when null — when PRESENT it must remain")
                .isFalse();
        assertThat(aliasNode).isNotNull();
    }

    // ==================== The NON_NULL vs NON_EMPTY guard ====================

    /**
     * <strong>The most valuable property in this file.</strong>
     *
     * <p>An empty string and an empty list are <em>present</em> values, not absent ones.
     * {@code NON_NULL} keeps them; {@code NON_EMPTY} would drop them — and no conformance test would
     * notice, because omitting more fields never violates the schema. This is what fails if wave 2
     * reaches for the wrong {@code JsonInclude} value.</p>
     */
    @Property(tries = 20)
    @Label("Empty strings and empty collections are retained — NON_NULL must not become NON_EMPTY")
    void emptyStringsAndEmptyCollectionsSurviveSerialization(@ForAll("nonBlank") String id) throws Exception {

        XQueryTemplateDTO template = new XQueryTemplateDTO(
                id, "SINGULAR", "APPROVED", "", "", "", new XQueryParametersDTO(List.of()), "xs:string");

        JsonNode json = MAPPER.valueToTree(template);

        assertThat(json.has("qualifier"))
                .as("an EMPTY qualifier is a present value; NON_EMPTY would wrongly drop it")
                .isTrue();
        assertThat(json.path("qualifier").asText()).isEmpty();
        assertThat(json.has("description")).isTrue();
        assertThat(json.path("description").asText()).isEmpty();
        // D6 (2.13) renested this as an object carrying a "parameter" array. The property being
        // preserved is unchanged — an EMPTY list must survive, because "takes no parameters" is a
        // different statement from "parameters unknown" — only the shape it is asserted through moved.
        assertThat(json.has("parameters"))
                .as("an EMPTY parameter list means 'no parameters', which is not the same as 'unknown'")
                .isTrue();
        assertThat(json.path("parameters").has("parameter"))
                .as("the schema nests parameters as an object with a 'parameter' array")
                .isTrue();
        assertThat(json.path("parameters").path("parameter").isArray()).isTrue();
        assertThat(json.path("parameters").path("parameter")).isEmpty();
    }

    /**
     * The same guard one level down, on the type whose empty-collection case is easiest to lose.
     */
    @Property(tries = 20)
    @Label("An empty alias list is retained rather than suppressed")
    void emptyAliasListIsRetained(@ForAll("nonBlank") String code) throws Exception {
        CodeItemDTO item = new CodeItemDTO(
                code, "s", "ns", "v", "", "", new AliasesDTO(List.of()), null, "2026-01-01T00:00:00Z");

        JsonNode json = MAPPER.valueToTree(item);

        assertThat(json.has("aliases")).isTrue();
        assertThat(json.path("aliases").has("alias")).isTrue();
        assertThat(json.path("aliases").path("alias").isArray()).isTrue();
    }

    // ==================== D3 scope guard ====================

    /**
     * D3 omits {@code defaultZone} when there is no default zone. It must NOT omit it when there is
     * one — a fix that drops the key unconditionally would satisfy every conformance assertion while
     * removing a field clients depend on.
     */
    @Property(tries = 20)
    @Label("A default zone WITH an id is still emitted, id intact")
    void defaultZoneWithAnIdIsStillEmitted(@ForAll("nonBlank") String zoneId) throws Exception {
        JsonNode json = MAPPER.valueToTree(DefaultZoneDTO.of(zoneId));

        assertThat(json.has("id"))
                .as("D3 suppresses the ABSENT case only; a present id must survive")
                .isTrue();
        assertThat(json.path("id").asText()).isEqualTo(zoneId);
    }

    // ==================== Helpers ====================

    /**
     * Finds the first object node anywhere in the tree that carries the given field. Used so the
     * present-value properties locate nested content structurally and keep passing across wave 3's
     * renesting, instead of hard-coding a path that the reshape is going to change.
     */
    private static JsonNode findFirstObjectContaining(JsonNode root, String fieldName) {
        if (root.isObject() && root.has(fieldName)) {
            return root;
        }
        for (JsonNode child : root) {
            JsonNode found = findFirstObjectContaining(child, fieldName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Provide
    Arbitrary<String> nonBlank() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(24);
    }
}
