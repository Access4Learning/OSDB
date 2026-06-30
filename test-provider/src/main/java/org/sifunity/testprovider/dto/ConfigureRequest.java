// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for {@code POST /test-control/configure}.
 *
 * <p>Carries the runtime configuration the test provider needs for the DELAYED choreography.
 * {@code sessionToken}, {@code sharedSecret}, {@code domainId}, and {@code solutionId} are
 * required; {@code gatewayUrl} and {@code queueId} are optional. When {@code gatewayUrl} is
 * omitted the existing value is retained; a blank or absent {@code queueId} disables delayed
 * polling.</p>
 *
 * <p>Unknown JSON fields are ignored so the suite can send a superset of fields without error.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigureRequest(
        String gatewayUrl,
        String sessionToken,
        String sharedSecret,
        String domainId,
        String solutionId,
        String queueId
) {
}
