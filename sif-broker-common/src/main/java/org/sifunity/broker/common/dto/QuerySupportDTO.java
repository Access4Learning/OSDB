// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The SIF 3.7 {@code querySupport} definition — what query features a provider can honour (D1).
 *
 * <p>Schema: {@code required: []} — every field is optional, but the {@code querySupport} <em>key
 * itself</em> is required on {@code provider}. That is the distinction D1 turns on: the broker was
 * omitting the key entirely, which is a schema violation even though its contents are all
 * optional.</p>
 *
 * <h2>Deliberate omission: applicationProduct / adapterProduct</h2>
 *
 * <p>The schema also permits {@code applicationProduct} and {@code adapterProduct}, both of type
 * {@code productIdentity}. They are <b>not</b> modelled here, for two reasons:</p>
 *
 * <ol>
 *   <li>Neither is required by the schema, so omitting them is conformant.</li>
 *   <li>{@code ProductIdentityDTO} currently lives in {@code environment-service}, and
 *       {@code sif-broker-common} cannot depend on it. Modelling them would mean relocating that type
 *       — a separate shared-contract move, not part of D1.</li>
 * </ol>
 *
 * <p>More to the point, {@code ProviderRegistrationDTO} holds no source data for either, so emitting
 * them would mean inventing values. If a future change starts capturing vendor identity on provider
 * registration, relocate {@code ProductIdentityDTO} to this module and add the two fields then.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuerySupportDTO(

    /** True if the service honours the {@code where} parameter. */
    Boolean dynamicQuery,

    /** True if the service honours query-by-example payloads. */
    Boolean queryByExample,

    /** True if the service supports Changes Since queries. */
    Boolean changesSinceMarker,

    /** True if the service responds to paged query requests, interactive or batch. */
    Boolean paged,

    /** If paged queries are supported, the maximum objects returned per page. */
    Integer maxPageSize,

    /** True if the service reports a total count. */
    Boolean totalCount
) {

    /**
     * The capability set the broker can state truthfully today.
     *
     * <p>The broker relays requests to providers rather than answering queries itself, so it cannot
     * assert a provider's query capabilities on the provider's behalf. This returns an <b>empty</b>
     * {@code querySupport} object — which satisfies the schema's requirement that the key be present
     * without fabricating capability claims. Every field being absent is the honest answer, and is
     * distinct from claiming {@code false}.</p>
     *
     * @return a querySupport with no asserted capabilities
     */
    public static QuerySupportDTO unspecified() {
        return new QuerySupportDTO(null, null, null, null, null, null);
    }
}
