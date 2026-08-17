// SPDX-License-Identifier: MIT
package org.sifunity.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sifunity.broker.common.constant.EnvironmentType;

/**
 * Client-side body of an environment registration request — the object under {@code environment} (D2).
 *
 * <p>Two renames relative to the pre-D2 flat form, both of which the broker enforces rather than
 * tolerates ({@code FAIL_ON_UNKNOWN_PROPERTIES} is enabled, so the old names are a 400):</p>
 *
 * <ul>
 *   <li>{@code applicationName} → {@code consumerName}</li>
 *   <li>{@code defaultZoneId} (a flat string) → {@code defaultZone} (an object carrying {@code id})</li>
 * </ul>
 *
 * <p>Build {@code defaultZone} with {@link DefaultZone#of(String)} rather than the constructor: D3
 * requires the key to be <b>absent entirely</b> when there is no zone, not present with a null id.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentRegistrationBody {
    private String consumerName;
    private EnvironmentType type;
    private DefaultZone defaultZone;
    private String solutionId;

    /**
     * The {@code defaultZone} object — a zone reference carrying only its id.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DefaultZone {
        private String id;

        /**
         * @param id the zone id, may be {@code null}
         * @return a zone reference, or {@code null} when {@code id} is null so the key is omitted (D3)
         */
        public static DefaultZone of(String id) {
            return id == null ? null : new DefaultZone(id);
        }
    }
}
