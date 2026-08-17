// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;

/**
 * The mutable part of a provider registration, wrapped under {@code provider} (D1).
 *
 * <p>Mirrors {@link ProviderRegistrationBody} for the fields an update may change. The two used to
 * disagree: registration took a wrapped body with a nested {@link ProtocolDTO} and
 * {@link SupportedRequestsDTO}, while update took a flat {@code endpoint} string and a bare
 * {@code List<SupportedRequestType>}. A client could not use one mental model for both, which is a
 * conformance problem in a reference implementation regardless of whether either shape "works".</p>
 *
 * <p>A provider's <em>identity</em> — the environment, service, zone and context it provides for — is
 * deliberately absent. Changing any of those is a different registration, not an update to this one, so
 * they are fixed at creation and the path {@code id} determines which registration is being amended.</p>
 *
 * @param endPoint              where requests are re-invoked; optional for DELAYED-only providers
 * @param supportedRequestTypes the supported request types, nested per the schema
 *
 * @see ProviderRegistrationBody
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderUpdateBody(

    /**
     * Endpoint where the provider service can be reached, as an object carrying a {@code location}.
     *
     * <p>Required in practice for providers exposing IMMEDIATE operations — enforced by
     * {@code ProviderService.updateProvider}, which knows the resulting operation set. Left optional
     * here because a DELAYED-only provider legitimately has none.</p>
     */
    @Valid
    ProtocolDTO endPoint,

    /** The supported request types, nested per the schema. Required. */
    @NotNull(message = "Supported request types are required")
    @Valid
    SupportedRequestsDTO supportedRequestTypes
) {

    /**
     * Rejects an endpoint that is present but not an http/https URL.
     *
     * <p>Matches {@link ProviderRegistrationBody#isEndPointLocationValid()} so create and update agree;
     * {@code ProviderService.validateEndpointUrl} remains the service-layer backstop for callers that
     * bypass Bean Validation.</p>
     *
     * @return true when the endpoint is absent or is a valid http/https URL
     */
    @AssertTrue(message = "Endpoint must be a valid HTTP or HTTPS URL")
    @JsonIgnore
    public boolean isEndPointLocationValid() {
        String location = ProtocolDTO.locationOf(endPoint);
        if (location == null || location.isBlank()) {
            return true;
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
}
