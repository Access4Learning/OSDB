// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.generator;

import net.jqwik.api.*;
import org.sifunity.broker.common.constant.AccessLevel;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.MessageExchangePattern;
import org.sifunity.broker.common.dto.AclEntryDTO;
import org.sifunity.broker.common.dto.EnvironmentDTO;
import org.sifunity.broker.common.dto.QueueDTO;

import java.time.Instant;
import java.util.UUID;

/**
 * Test data generators for property-based testing using jqwik.
 * 
 * <p>This class provides jqwik Arbitrary instances for generating random
 * test data for DTOs, enums, and common data types used throughout the
 * SIF Broker Common library.</p>
 * 
 * <p>These generators are designed to produce valid data conforming to
 * SIF 3.7 specification constraints and can be used in property-based tests
 * to verify universal properties across randomized inputs.</p>
 * 
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * @Property
 * void myPropertyTest(@ForAll("environments") EnvironmentDTO env) {
 *     // Test logic using randomly generated environment
 * }
 * }</pre>
 * 
 * @see net.jqwik.api.Arbitrary
 * @see net.jqwik.api.Provide
 */
public class TestDataGenerators {

    // ========== Basic Type Generators ==========

    /**
     * Generates random UUIDs.
     * 
     * @return Arbitrary that generates random UUIDs
     */
    @Provide
    public static Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    /**
     * Generates random Instant timestamps.
     * 
     * <p>Generates timestamps between epoch (1970-01-01) and current time.</p>
     * 
     * @return Arbitrary that generates random Instant values
     */
    @Provide
    public static Arbitrary<Instant> instants() {
        return Arbitraries.longs()
            .between(0, System.currentTimeMillis())
            .map(Instant::ofEpochMilli);
    }

    /**
     * Generates random non-blank strings.
     * 
     * <p>Generates alphanumeric strings between 1 and 50 characters in length.</p>
     * 
     * @return Arbitrary that generates random non-blank strings
     */
    @Provide
    public static Arbitrary<String> nonBlankStrings() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(1)
            .ofMaxLength(50);
    }

    // ========== Enumeration Generators ==========

    /**
     * Generates random MessageExchangePattern values.
     * 
     * <p>Generates one of: IMMEDIATE, DELAYED, or EVENT.</p>
     * 
     * @return Arbitrary that generates random MessageExchangePattern values
     */
    @Provide
    public static Arbitrary<MessageExchangePattern> messageExchangePatterns() {
        return Arbitraries.of(MessageExchangePattern.values());
    }

    /**
     * Generates random EnvironmentType values.
     * 
     * <p>Generates one of: DIRECT or BROKERED.</p>
     * 
     * @return Arbitrary that generates random EnvironmentType values
     */
    @Provide
    public static Arbitrary<EnvironmentType> environmentTypes() {
        return Arbitraries.of(EnvironmentType.DIRECT, EnvironmentType.BROKERED);
    }

    /**
     * Generates random AccessType values.
     * 
     * <p>Generates one of: QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, or PROVIDE.</p>
     * 
     * @return Arbitrary that generates random AccessType values
     */
    @Provide
    public static Arbitrary<AccessType> accessTypes() {
        return Arbitraries.of(AccessType.values());
    }

    /**
     * Generates random AccessLevel values.
     * 
     * <p>Generates one of: APPROVED, SUPPORTED, REJECTED, UNSUPPORTED, or REQUESTED.</p>
     * 
     * @return Arbitrary that generates random AccessLevel values
     */
    @Provide
    public static Arbitrary<AccessLevel> accessLevels() {
        return Arbitraries.of(AccessLevel.values());
    }

    // ========== SIF-Specific String Generators ==========

    /**
     * Generates valid SIF service names.
     * 
     * <p>Service names must start with a letter and contain only alphanumeric
     * characters, underscores, or hyphens. Length is between 3 and 30 characters.</p>
     * 
     * <p>Examples: "StudentPersonal", "SchoolInfo", "my_service", "test-service-1"</p>
     * 
     * @return Arbitrary that generates valid service names
     */
    @Provide
    public static Arbitrary<String> serviceNames() {
        // First character must be a letter
        Arbitrary<Character> firstChar = Arbitraries.chars().range('a', 'z');
        
        // Remaining characters can be alphanumeric, underscore, or hyphen
        Arbitrary<String> remainingChars = Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('_', '-')
            .ofMinLength(2)
            .ofMaxLength(29);
        
        return Combinators.combine(firstChar, remainingChars)
            .as((first, rest) -> first + rest);
    }

    /**
     * Generates valid SIF zone IDs.
     * 
     * <p>Zone IDs must start with an alphanumeric character and contain only
     * alphanumeric characters, underscores, or hyphens. Length is between 3 and 30 characters.</p>
     * 
     * <p>Examples: "default", "zone1", "test_zone", "prod-zone-01"</p>
     * 
     * @return Arbitrary that generates valid zone IDs
     */
    @Provide
    public static Arbitrary<String> zoneIds() {
        // First character must be alphanumeric
        Arbitrary<Character> firstChar = Arbitraries.chars()
            .range('a', 'z');
        
        // Remaining characters can be alphanumeric, underscore, or hyphen
        Arbitrary<String> remainingChars = Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('_', '-')
            .ofMinLength(2)
            .ofMaxLength(29);
        
        return Combinators.combine(firstChar, remainingChars)
            .as((first, rest) -> first + rest);
    }

    // ========== DTO Generators ==========

    /**
     * Generates random EnvironmentDTO instances.
     * 
     * <p>Generates valid environment DTOs with all required fields populated.
     * Optional fields (expiresAt) are set to null.</p>
     * 
     * @return Arbitrary that generates random EnvironmentDTO instances
     */
    @Provide
    public static Arbitrary<EnvironmentDTO> environments() {
        return Combinators.combine(
            uuids(),
            nonBlankStrings(),
            nonBlankStrings(),
            instants(),
            zoneIds(),
            environmentTypes()
        ).as((id, appName, token, created, zoneId, type) ->
            new EnvironmentDTO(id, appName, token, created, null, zoneId, type, null)
        );
    }

    /**
     * Generates random QueueDTO instances.
     * 
     * <p>Generates valid queue DTOs with all required fields populated.
     * Max retries is between 1 and 10, retry delay is between 1 and 60 seconds,
     * and message count is set to 0.</p>
     * 
     * @return Arbitrary that generates random QueueDTO instances
     */
    @Provide
    public static Arbitrary<QueueDTO> queues() {
        return Combinators.combine(
            uuids(),
            nonBlankStrings(),
            uuids(),
            instants(),
            Arbitraries.integers().between(1, 10),
            Arbitraries.longs().between(1000L, 60000L)
        ).as((id, name, ownerId, created, retries, delay) ->
            new QueueDTO(id, name, ownerId, "IMMEDIATE", null, 1,
                        created, null, null, retries, delay, 0L)
        );
    }

    /**
     * Generates random AclEntryDTO instances.
     * 
     * <p>Generates valid ACL entry DTOs with all required fields populated.
     * Optional contextId field is set to null.</p>
     * 
     * @return Arbitrary that generates random AclEntryDTO instances
     */
    @Provide
    public static Arbitrary<AclEntryDTO> aclEntries() {
        return Combinators.combine(
            uuids(),
            uuids(),
            serviceNames(),
            accessTypes(),
            accessLevels()
        ).as((id, envId, serviceName, accessType, accessLevel) ->
            new AclEntryDTO(id, envId, serviceName, null, null, accessType, accessLevel)
        );
    }
}
