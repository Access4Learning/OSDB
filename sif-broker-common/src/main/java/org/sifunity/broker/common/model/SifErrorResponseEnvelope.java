// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.sifunity.broker.common.exception.SifBrokerException;

/**
 * Envelope wrapper for SIF 3.7 errorCommon compliant error responses.
 * <p>
 * This record wraps a {@link SifErrorResponse} under an "error" key to conform
 * to the SIF 3.7 specification requirement that error response bodies have a
 * single top-level key "error" containing the error object.
 * </p>
 * <p>
 * Serializes to: {@code {"error": {"id": "...", "code": 400, "subCode": "VAL_001", ...}}}
 * </p>
 *
 * @see SifErrorResponse
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SifErrorResponseEnvelope(SifErrorResponse error) {

    /**
     * Creates an envelope wrapping the given error response.
     */
    public static SifErrorResponseEnvelope of(SifErrorResponse inner) {
        return new SifErrorResponseEnvelope(inner);
    }

    /**
     * Creates an envelope directly from a SifBrokerException.
     */
    public static SifErrorResponseEnvelope fromException(SifBrokerException ex) {
        return new SifErrorResponseEnvelope(SifErrorResponse.fromException(ex));
    }
}
