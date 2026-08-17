// SPDX-License-Identifier: MIT
package org.sifunity.responsesconnector.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /internal/responses/error}, used by {@code requests-connector} to
 * report proxy and paging failures so the consumer learns its DELAYED request will not be answered.
 *
 * <p>Wire shape:
 * <pre>{@code
 * {
 *   "error": {
 *     "code": 502,
 *     "subCode": "PROVIDER_ERROR",
 *     "message": "Paging failed on page 3 of 7: provider returned status 503",
 *     "scope": "PROVIDER"
 *   }
 * }
 * }</pre>
 *
 * @see InternalErrorDetail
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InternalErrorRequest(

    /** The error detail. Required. */
    @NotNull(message = "Error detail is required")
    @Valid
    InternalErrorDetail error
) {
}
