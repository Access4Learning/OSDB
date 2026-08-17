// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Builds an {@link ObjectMapper} configured the way Spring Boot configures the one that actually
 * serializes responses.
 *
 * <p>Conformance tests are only meaningful if they serialize the way production does. A bare
 * {@code new ObjectMapper()} differs in two ways that matter here: it cannot handle
 * {@code java.time.Instant} at all, and when given a module it would write timestamps as numeric
 * epochs rather than the ISO-8601 strings the SIF schemas require.</p>
 *
 * <p>Deliberately does <em>not</em> set a global null-inclusion policy. The whole point of D5 is
 * that null suppression must be declared on the DTOs themselves; configuring it on the mapper
 * here would mask the defect and let it ship.</p>
 */
final class SifWireFormatTestMapper {

    private SifWireFormatTestMapper() {
    }

    static ObjectMapper create() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
