// SPDX-License-Identifier: MIT
package org.sifunity.requestsconnector.property;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Property-based test: Disallowed class rejection.
 *
 * <p><strong>Property 6: Disallowed class rejection</strong></p>
 *
 * <p>For any JSON payload containing a {@code @class} type identifier referencing a class
 * outside the {@code org.sifunity.broker}, {@code org.sifunity.requestsconnector}, and
 * {@code java.util} packages, deserialization with the Redis ObjectMapper SHALL throw
 * an {@code InvalidTypeIdException}.</p>
 *
 * <p><strong>Validates: Requirements 4.6</strong></p>
 */
@Tag("Feature: n-plus-one-and-cache-fixes, Property 6: Disallowed class rejection")
class DisallowedClassRejectionPropertyTest {

    private final ObjectMapper redisObjectMapper;

    DisallowedClassRejectionPropertyTest() {
        // Configure identically to production redisObjectMapper() bean
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType("org.sifunity.broker")
            .allowIfSubType("org.sifunity.broker")
            .allowIfBaseType("org.sifunity.requestsconnector")
            .allowIfSubType("org.sifunity.requestsconnector")
            .allowIfBaseType("java.util.")
            .allowIfSubType("java.util.")
            .build();

        mapper.activateDefaultTyping(ptv,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY);

        this.redisObjectMapper = mapper;
    }

    /**
     * Property: JSON payloads with @class referencing disallowed packages are rejected.
     *
     * <p>Validates: Requirements 4.6</p>
     */
    @Property(tries = 20)
    void disallowedClassTypeIdThrowsInvalidTypeIdException(
            @ForAll("disallowedClassNames") String disallowedClassName) {

        // Construct JSON payload with disallowed @class type identifier
        String json = "{\"@class\":\"" + disallowedClassName + "\",\"field\":\"value\"}";

        // Attempt to deserialize with restricted ObjectMapper
        Throwable thrown = catchThrowable(() ->
            redisObjectMapper.readValue(json, Object.class)
        );

        // Assert InvalidTypeIdException is thrown (directly or as cause)
        assertThat(thrown).isNotNull();
        assertThat(findInvalidTypeIdException(thrown))
            .as("Expected InvalidTypeIdException for disallowed class: %s", disallowedClassName)
            .isTrue();
    }

    // ==================== Arbitraries ====================

    @Provide
    Arbitrary<String> disallowedClassNames() {
        // Generate class names from packages NOT in the allowlist:
        // - org.sifunity.broker.* is allowed
        // - org.sifunity.requestsconnector.* is allowed
        // - java.util.* is allowed
        // Everything else must be rejected.
        Arbitrary<String> knownDangerous = Arbitraries.of(
            "com.evil.Exploit",
            "java.lang.Runtime",
            "javax.script.ScriptEngine",
            "org.apache.commons.Evil",
            "org.springframework.context.ApplicationContext",
            "com.sun.rowset.JdbcRowSetImpl",
            "java.lang.ProcessBuilder",
            "javax.naming.InitialContext",
            "org.hibernate.jmx.StatisticsService",
            "ch.qos.logback.core.db.DriverManagerConnectionSource"
        );

        Arbitrary<String> randomPackages = Combinators.combine(
            Arbitraries.of("com", "org", "net", "io", "javax", "java.lang", "java.io", "java.net"),
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10),
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15)
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
        ).as((prefix, pkg, className) -> prefix + "." + pkg.toLowerCase() + "." + className);

        return Arbitraries.oneOf(knownDangerous, randomPackages);
    }

    // ==================== Helpers ====================

    /**
     * Checks if the throwable or any of its causes is an InvalidTypeIdException.
     */
    private boolean findInvalidTypeIdException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InvalidTypeIdException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
