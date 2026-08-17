// SPDX-License-Identifier: MIT
package org.sifunity.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.sifunity.broker.common.dto.ProviderRegistrationDTO;

import java.util.List;

/**
 * Client-side view of the broker's provider collection envelope (D1).
 *
 * <p>{@code GET /providers?environmentId=…} returns the SIF double-nested collection form
 * {@code {"providers": {"provider": [ … ]}}} rather than a bare JSON array, matching domains,
 * solutions, zones, environments and admin users.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderCollectionResponse {

    private Providers providers;

    /**
     * The inner wrapper — note the schema's singular/plural asymmetry: the outer key is
     * {@code providers}, the array inside it is {@code provider}.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Providers {
        private List<ProviderRegistrationDTO> provider;
    }

    /**
     * @return the provider list, or an empty list when the envelope or its array is absent
     */
    public List<ProviderRegistrationDTO> providerList() {
        if (providers == null || providers.getProvider() == null) {
            return List.of();
        }
        return providers.getProvider();
    }
}
