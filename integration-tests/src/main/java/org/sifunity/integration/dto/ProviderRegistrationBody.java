// SPDX-License-Identifier: MIT
package org.sifunity.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.ProtocolDTO;
import org.sifunity.broker.common.dto.SupportedRequestsDTO;

import java.util.UUID;

/**
 * Client-side body of a provider registration request — the object under the {@code provider} key (D1).
 *
 * <p>Mirrors the broker's {@code ProviderRegistrationBody}. This module talks to the broker over real
 * HTTP and the broker enables {@code FAIL_ON_UNKNOWN_PROPERTIES}, so the shape has to match exactly:
 * a stale field name is a 400, not a silently ignored extra.</p>
 *
 * <p>Deliberately carries <b>no {@code providerName}</b>. The broker does not accept one — there is
 * nowhere to persist it, since {@code V41__drop_provider_name_column.sql} removed the column — and it
 * derives the value from the owning environment's {@code consumerName} instead. Sending it would be
 * rejected as an unknown property.</p>
 *
 * <p>{@code endPoint} and {@code supportedRequestTypes} use the nested schema shapes, matching the
 * response, rather than a flat URL string and a bare array.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderRegistrationBody {
    private UUID environmentId;
    private String serviceName;
    private ServiceType serviceType;
    private String zoneId;
    private String contextId;
    private ProtocolDTO endPoint;
    private SupportedRequestsDTO supportedRequestTypes;
}
