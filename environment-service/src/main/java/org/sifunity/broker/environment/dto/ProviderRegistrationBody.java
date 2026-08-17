// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;

import java.util.UUID;

/**
 * The body of a provider registration request — the object under the {@code provider} key (D1).
 *
 * <p>Registration is wrapped as {@code {"provider": { ... }}}, per the SIF 3.7 envelope convention
 * already used by {@code environment}, {@code subscription} and {@code xquery}:</p>
 *
 * <pre>{@code
 * {
 *   "provider": {
 *     "serviceName": "StudentPersonals",
 *     "serviceType": "OBJECT",
 *     "zoneId": "district-zone",
 *     "contextId": "DEFAULT",
 *     "endPoint": { "location": "https://sis.example.com/StudentPersonals" },
 *     "supportedRequestTypes": {
 *       "supportedRequestType": [
 *         { "requestType": "IMMEDIATE", "operations": { "operation": ["QUERY"] } }
 *       ]
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>The nested {@code endPoint} and {@code supportedRequestTypes} shapes deliberately match the
 * response {@link org.sifunity.broker.common.dto.ProviderRegistrationDTO}, so a client uses one
 * vocabulary in both directions. Accepting a flat {@code endpoint} string here while emitting an
 * {@code endPoint} object is exactly the inconsistency D1 exists to remove.</p>
 *
 * <h2>Two ways to identify the service being provided</h2>
 *
 * <p>A conforming SIF consumer identifies the service by the {@code (serviceName, zoneId, contextId)}
 * triple, which the broker resolves to its internal {@code context_service} row. It cannot use
 * {@link #contextServiceUuid()} — that is an <b>OSDB-internal foreign key</b> a client has no way to
 * know.</p>
 *
 * <p>{@code contextServiceUuid} is nonetheless still accepted, because internal callers already hold
 * the resolved UUID and re-deriving the triple at those call sites would add nothing. Exactly one of
 * the two forms must be supplied; {@link #isServiceIdentifiable()} enforces that as a cross-field
 * constraint rather than as {@code @NotBlank} on the triple, which would break the UUID form. This is
 * the same two-mode pattern used for the subscription ACL fields (requirement 2.10).</p>
 *
 * <h2>Known conformance gap: {@code providerName}</h2>
 *
 * <p>The SIF {@code provider} schema lists {@code providerName} as required, and this body does
 * <b>not</b> carry it. That is a deliberate, recorded decision: {@code provider_registry.provider_name}
 * was added by {@code V27} and dropped again by {@code V41}, so there is nowhere to persist a
 * client-supplied value. The response derives {@code providerName} from the owning environment's
 * {@code consumerName} instead — see the spec's DECIDED note. Accepting the field here and silently
 * discarding it would be worse than omitting it, because a client would get a different name back than
 * the one it sent. Closing the gap needs a column and a Flyway migration; see task 10.</p>
 *
 * @param environmentId         the environment providing the service — an OSDB extension, not a schema field
 * @param serviceName           the SIF service being provided
 * @param serviceType           the SIF service type classification
 * @param zoneId                the zone the service is provided in
 * @param contextId             the context qualifying the service
 * @param contextServiceUuid    OSDB-internal alternative to the triple above
 * @param endPoint              where requests are re-invoked; absent for DELAYED-only providers
 * @param supportedRequestTypes the supported request types, nested per the schema
 *
 * @see ProviderRegistrationRequest
 * @see org.sifunity.broker.common.dto.ProviderRegistrationDTO
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)  // Accepts providerName, querySupport etc. from conformant SIF clients without rejecting them
public record ProviderRegistrationBody(

    /**
     * The environment providing the service. Must exist and be of type BROKERED.
     *
     * <p>An OSDB extension rather than a schema field, retained because the broker needs to know which
     * environment a registration belongs to.</p>
     */
    @NotNull(message = "Environment ID is required")
    UUID environmentId,

    /** The SIF service being provided. Required by the schema; part of the identifying triple. */
    String serviceName,

    /**
     * The SIF service type classification.
     *
     * <p>UTILITY, OBJECT, FUNCTIONAL, SERVICEPATH, XQUERYTEMPLATE or SERVICE.</p>
     */
    @NotNull(message = "Service type is required")
    ServiceType serviceType,

    /** The zone the service is provided in. Required by the schema; part of the identifying triple. */
    String zoneId,

    /** The context qualifying the service. Required by the schema; part of the identifying triple. */
    String contextId,

    /**
     * OSDB-internal identifier for the {@code context_service} row, accepted in place of the triple.
     *
     * <p>Not part of the SIF schema and not usable by an external client — see the class javadoc.</p>
     */
    UUID contextServiceUuid,

    /**
     * Where requests for this provider are re-invoked.
     *
     * <p>Required in practice for providers with IMMEDIATE operations, since the broker calls back to
     * it. Optional for DELAYED-only providers, which poll their queue instead — which is why this is
     * not {@code @NotNull}.</p>
     */
    @Valid
    ProtocolDTO endPoint,

    /** The supported request types, nested per the schema. At least one entry is required. */
    @NotNull(message = "Supported request types are required")
    @Valid
    SupportedRequestsDTO supportedRequestTypes
) {

    /**
     * Cross-field check that the service being provided can actually be identified.
     *
     * <p>Either {@link #contextServiceUuid()} is supplied, or all three of {@code serviceName},
     * {@code zoneId} and {@code contextId} are. Expressed as {@code @AssertTrue} because
     * {@code @NotBlank} on the triple would reject the UUID form and vice versa.</p>
     *
     * @return true when the service is identifiable
     */
    @AssertTrue(message = "Provide either contextServiceUuid, or all of serviceName, zoneId and contextId")
    @JsonIgnore
    public boolean isServiceIdentifiable() {
        if (contextServiceUuid != null) {
            return true;
        }
        return isPresent(serviceName) && isPresent(zoneId) && isPresent(contextId);
    }

    /**
     * Checks that {@code endPoint.location} is a usable HTTP(S) URL.
     *
     * <p>Before D1 the endpoint was a flat {@code String} carrying Hibernate Validator's {@code @URL},
     * so a malformed URL was rejected at the controller with a 400 and the service was never reached.
     * Nesting it inside {@link ProtocolDTO} lost that, because {@code ProtocolDTO} lives in
     * {@code sif-broker-common} where {@code hibernate-validator} is only a <b>test</b>-scoped
     * dependency — {@code @URL} is not available on its {@code location} field. Rather than widen a
     * shared library's dependency scope for one annotation, the constraint is restated here, where the
     * request itself lives.</p>
     *
     * <p>Protocol restriction matches {@code ProviderService.validateEndpointUrl}, which remains the
     * service-layer backstop for callers that bypass Bean Validation.</p>
     *
     * @return true when the endpoint is absent or is a valid http/https URL
     */
    @AssertTrue(message = "Endpoint must be a valid HTTP or HTTPS URL")
    @JsonIgnore
    public boolean isEndPointLocationValid() {
        String location = ProtocolDTO.locationOf(endPoint);
        if (location == null || location.isBlank()) {
            return true;    // absence is legal for DELAYED-only providers; @NotBlank covers a blank object
        }
        try {
            String protocol = new java.net.URL(location).getProtocol().toLowerCase();
            return "http".equals(protocol) || "https".equals(protocol);
        } catch (java.net.MalformedURLException e) {
            return false;
        }
    }

    /**
     * @return true when at least one supported request type is present
     */
    @AssertTrue(message = "At least one supported request type is required")
    @JsonIgnore
    public boolean isAnyRequestTypeSupported() {
        return supportedRequestTypes == null
                || (supportedRequestTypes.supportedRequestType() != null
                    && !supportedRequestTypes.supportedRequestType().isEmpty());
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
