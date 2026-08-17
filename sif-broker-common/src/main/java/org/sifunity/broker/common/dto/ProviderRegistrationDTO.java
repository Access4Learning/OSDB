// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.sifunity.broker.common.constant.ServiceType;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object representing a provider service registration.
 *
 * <p>A provider registration records that an environment is providing a specific service in a
 * zone/context, aligned with the SIF 3.7 {@code provider} schema.</p>
 *
 * <h2>D1 reshape (requirements 2.1-2.6)</h2>
 *
 * <p>The schema requires {@code contextId}, {@code providerName}, {@code querySupport},
 * {@code serviceName}, {@code serviceType} and {@code zoneId}. Four mismatches are corrected:</p>
 *
 * <ul>
 *   <li><b>{@code providerName} added.</b> It is <em>not</em> the same concept as the owning
 *       environment's {@code consumerName}: the schema describes it as "the name of the Service
 *       Provider as it would be referred to by the administrator of the Zone". One environment can
 *       provide several services under distinct provider names.</li>
 *   <li><b>{@code consumerName} removed from the wire.</b> It is not part of the provider schema at
 *       all. It was previously emitted, which is a violation regardless of its value.</li>
 *   <li><b>{@code endpoint} (flat string) → {@code endPoint}</b> ({@link ProtocolDTO}, with a required
 *       {@code location}). Note the capital P — the schema spells it {@code endPoint}.</li>
 *   <li><b>{@code supportedRequestTypes} nested.</b> Now {@link SupportedRequestsDTO}, whose
 *       {@code supportedRequestType} array holds entries whose {@code operations} are themselves
 *       nested — see {@link SupportedRequestType}.</li>
 *   <li><b>{@code querySupport} added.</b> The key is required even though all its contents are
 *       optional; see {@link QuerySupportDTO#unspecified()} for why the broker emits it empty rather
 *       than asserting capabilities on a provider's behalf.</li>
 * </ul>
 *
 * <p>{@code id}, {@code environmentId} and {@code registeredAt} are broker bookkeeping rather than
 * schema fields. {@code id} is in the schema; the other two are retained because internal callers
 * depend on them and the schema does not forbid extra properties on this definition.</p>
 *
 * <p>There is no flat alternative constructor or {@code endpoint}/{@code consumerName} accessor, so a
 * caller cannot accidentally reconstruct the pre-D1 wire shape. To read the endpoint URL use
 * {@link ProtocolDTO#locationOf(ProtocolDTO)} rather than {@code endPoint().location()}:
 * {@code endPoint} is genuinely optional — the schema does not require it and the column is
 * nullable — so it can be null.</p>
 *
 * @see ServiceType
 * @see SupportedRequestType
 * @see ProtocolDTO
 * @see QuerySupportDTO
 * @see <a href="https://specification.sifassociation.org/Implementation/Infrastructure/3.7/">SIF 3.7 Specification</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderRegistrationDTO(

    /** Unique identifier for the provider registration. */
    @NotNull
    UUID id,

    /**
     * ID of the environment providing the service.
     *
     * <p>Broker bookkeeping, not a SIF schema field; retained for internal callers.</p>
     */
    @NotNull
    UUID environmentId,

    /** The SIF service being provided. Required by the schema. */
    @NotBlank
    String serviceName,

    /** The zone the service is provided in. Required by the schema. */
    @NotBlank
    String zoneId,

    /** The context qualifying the service. Required by the schema. */
    @NotBlank
    String contextId,

    /**
     * The provider's name as the zone administrator would refer to it. Required by the schema.
     *
     * <p>Distinct from the owning environment's {@code consumerName} — see the class javadoc.</p>
     */
    @NotBlank
    String providerName,

    /** The query features the provider supports. The key is required; its contents are not. */
    @NotNull
    @Valid
    QuerySupportDTO querySupport,

    /** Where requests for this provider are re-invoked. An object with a required {@code location}. */
    @Valid
    ProtocolDTO endPoint,

    /** The SIF service type. Required by the schema. */
    @NotNull
    ServiceType serviceType,

    /** The supported request types, nested per the schema. */
    @NotNull
    @Valid
    SupportedRequestsDTO supportedRequestTypes,

    /**
     * When the registration was created.
     *
     * <p>Broker bookkeeping, not a SIF schema field; retained for internal callers.</p>
     */
    @NotNull
    Instant registeredAt
) {

}
