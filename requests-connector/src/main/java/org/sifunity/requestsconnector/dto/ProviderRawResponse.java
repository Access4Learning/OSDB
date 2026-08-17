// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * Captures the full HTTP response from a provider call:
 * body, response headers, and status code.
 * Used by the paging loop to inspect navigation headers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderRawResponse {

    /** Deserialized response body (same as current sendRequestRaw return). */
    private Object body;

    /** HTTP response headers from the provider. */
    private Map<String, String> headers;

    /** HTTP status code from the provider. */
    private int statusCode;
}
