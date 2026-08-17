// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifErrorResponseEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

public final class ServletErrorResponseUtils {

    private static final Logger log = LoggerFactory.getLogger(ServletErrorResponseUtils.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.findAndRegisterModules();
    }

    private ServletErrorResponseUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void writeErrorResponse(
            HttpServletResponse response,
            int status,
            String errorCode,
            String message,
            String description) throws IOException {

        SifErrorResponse errorResponse = new SifErrorResponse(
                UUID.randomUUID().toString(), status, errorCode, message,
                description, "BROKER", Instant.now(), null, null);
        writeErrorResponse(response, status, errorResponse);
    }

    public static void writeErrorResponse(
            HttpServletResponse response,
            int status,
            SifErrorResponse errorResponse) throws IOException {

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        OBJECT_MAPPER.writeValue(response.getWriter(), SifErrorResponseEnvelope.of(errorResponse));
    }
}
