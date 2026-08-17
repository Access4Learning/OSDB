// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.MessageExchangePattern;
import org.sifunity.broker.common.exception.InvalidParameterException;
import org.sifunity.broker.common.exception.InvalidTimestampException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for utility classes.
 * 
 * <p>These tests validate universal properties that should hold across
 * all possible inputs, using jqwik for property-based testing.</p>
 * 
 * <p><strong>Properties Tested:</strong></p>
 * <ul>
 *   <li>Property 6: HMAC Computation Consistency</li>
 *   <li>Property 7: Timestamp Validation</li>
 *   <li>Property 8: Storage Key Format</li>
 *   <li>Property 9: Validation Utilities Consistency</li>
 * </ul>
 */
@Tag("Feature-sif-broker-common")
class UtilityProperties {

    // ========== Property 6: HMAC Computation Consistency ==========

    /**
     * Property 6: HMAC Computation Consistency
     * 
     * <p>For any sessionToken, timestamp, and sharedSecret, computing HMAC twice
     * should produce identical results.</p>
     * 
     * <p><strong>Validates: Requirements 5.3, 5.4</strong></p>
     */
    @Property(tries = 100)
    @Tag("Property-6-HMAC-Computation-Consistency")
    void hmacComputationIsConsistent(
            @ForAll("sessionTokens") String sessionToken,
            @ForAll("timestamps") String timestamp,
            @ForAll("sharedSecrets") String sharedSecret) {
        
        // Compute HMAC twice with the same inputs
        String hmac1 = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);
        String hmac2 = SifAuthenticationUtils.computeHmac(sessionToken, timestamp, sharedSecret);
        
        // The results should be identical
        assertThat(hmac1)
            .as("HMAC computation should be deterministic")
            .isEqualTo(hmac2);
    }

    /**
     * Property 6 (Extended): HMAC changes with different inputs
     * 
     * <p>For any two different sessionTokens with the same timestamp and secret,
     * the HMAC values should be different.</p>
     */
    @Property(tries = 100)
    @Tag("Property-6-HMAC-Computation-Consistency")
    void hmacChangesWithDifferentInputs(
            @ForAll("sessionTokens") String sessionToken1,
            @ForAll("sessionTokens") String sessionToken2,
            @ForAll("timestamps") String timestamp,
            @ForAll("sharedSecrets") String sharedSecret) {
        
        Assume.that(!sessionToken1.equals(sessionToken2));
        
        String hmac1 = SifAuthenticationUtils.computeHmac(sessionToken1, timestamp, sharedSecret);
        String hmac2 = SifAuthenticationUtils.computeHmac(sessionToken2, timestamp, sharedSecret);
        
        assertThat(hmac1)
            .as("Different session tokens should produce different HMACs")
            .isNotEqualTo(hmac2);
    }

    // ========== Property 7: Timestamp Validation ==========

    /**
     * Property 7: Timestamp Validation
     * 
     * <p>For any timestamp within the tolerance window, validation should succeed;
     * for timestamps outside the window, validation should fail with InvalidTimestampException.</p>
     * 
     * <p><strong>Validates: Requirements 5.5</strong></p>
     */
    @Property(tries = 100)
    @Tag("Property-7-Timestamp-Validation")
    void timestampWithinToleranceIsValid(
            @ForAll @IntRange(min = -4, max = 4) int minutesOffset) {
        
        // Create a timestamp within the tolerance window (default 5 minutes)
        Instant timestamp = Instant.now().plus(minutesOffset, ChronoUnit.MINUTES);
        String timestampStr = timestamp.toString();
        
        // Validation should succeed (no exception thrown)
        SifAuthenticationUtils.validateTimestamp(timestampStr);
        
        // If we get here, the test passed
        assertThat(true).isTrue();
    }

    /**
     * Property 7 (Extended): Timestamp outside tolerance is invalid
     * 
     * <p>For any timestamp outside the tolerance window, validation should fail.</p>
     */
    @Property(tries = 100)
    @Tag("Property-7-Timestamp-Validation")
    void timestampOutsideToleranceIsInvalid(
            @ForAll @IntRange(min = 10, max = 1000) int minutesOffset,
            @ForAll boolean isPast) {
        
        // Create a timestamp outside the tolerance window (> 5 minutes)
        int offset = isPast ? -minutesOffset : minutesOffset;
        Instant timestamp = Instant.now().plus(offset, ChronoUnit.MINUTES);
        String timestampStr = timestamp.toString();
        
        // Validation should fail with InvalidTimestampException
        assertThatThrownBy(() -> SifAuthenticationUtils.validateTimestamp(timestampStr))
            .isInstanceOf(InvalidTimestampException.class)
            .hasMessageContaining("outside tolerance window");
    }

    /**
     * Property 7 (Extended): Custom tolerance window
     * 
     * <p>Timestamp validation should respect custom tolerance values.</p>
     */
    @Property(tries = 100)
    @Tag("Property-7-Timestamp-Validation")
    void timestampValidationRespectsCustomTolerance(
            @ForAll @LongRange(min = 1, max = 60) long toleranceMinutes,
            @ForAll @IntRange(min = -100, max = 100) int minutesOffset) {
        
        // Use fixed reference time to eliminate race condition
        Instant referenceTime = Instant.parse("2024-01-15T10:00:00Z");
        Instant timestamp = referenceTime.plus(minutesOffset, ChronoUnit.MINUTES);
        String timestampStr = timestamp.toString();
        
        long actualOffset = Math.abs(minutesOffset);
        
        if (actualOffset <= toleranceMinutes) {
            // Should succeed
            SifAuthenticationUtils.validateTimestampWithReference(timestampStr, toleranceMinutes, referenceTime);
            assertThat(true).isTrue();
        } else {
            // Should fail
            assertThatThrownBy(() -> SifAuthenticationUtils.validateTimestampWithReference(timestampStr, toleranceMinutes, referenceTime))
                .isInstanceOf(InvalidTimestampException.class);
        }
    }

    // ========== Property 8: Storage Key Format ==========

    /**
     * Property 8: Storage Key Format
     * 
     * <p>For any valid environment, zone, context, service, and messageId,
     * the generated storage key should match the hierarchical format:
     * {environment}/{zone}/{context}/{service}/{messageId}.json</p>
     * 
     * <p><strong>Validates: Requirements 5.6, 5.7</strong></p>
     */
    @Property(tries = 100)
    @Tag("Property-8-Storage-Key-Format")
    void storageKeyMatchesHierarchicalFormat(
            @ForAll("identifiers") String environment,
            @ForAll("identifiers") String zone,
            @ForAll("identifiers") String context,
            @ForAll("identifiers") String service,
            @ForAll("uuids") UUID messageId) {
        
        String key = StorageKeyGenerator.generateKey(environment, zone, context, service, messageId);
        
        // Key should match the expected format
        assertThat(key)
            .as("Storage key should match hierarchical format")
            .matches("^[^/]+/[^/]+/[^/]+/[^/]+/[a-f0-9-]+\\.json$");
        
        // Key should contain all components
        assertThat(key).contains(messageId.toString());
        assertThat(key).endsWith(".json");
        
        // Key should have exactly 5 segments (4 slashes)
        long slashCount = key.chars().filter(ch -> ch == '/').count();
        assertThat(slashCount).isEqualTo(4);
    }

    /**
     * Property 8 (Extended): Storage key without context uses "default"
     * 
     * <p>When context is null, the storage key should use "default" as the context.</p>
     */
    @Property(tries = 100)
    @Tag("Property-8-Storage-Key-Format")
    void storageKeyWithoutContextUsesDefault(
            @ForAll("identifiers") String environment,
            @ForAll("identifiers") String zone,
            @ForAll("identifiers") String service,
            @ForAll("uuids") UUID messageId) {
        
        String key = StorageKeyGenerator.generateKey(environment, zone, service, messageId);
        
        // Key should contain "default" as the context
        assertThat(key).contains("/default/");
        
        // Key should still match the format
        assertThat(key).matches("^[^/]+/[^/]+/default/[^/]+/[a-f0-9-]+\\.json$");
    }

    /**
     * Property 8 (Extended): Storage key sanitizes special characters
     * 
     * <p>Special characters in identifiers should be sanitized to underscores.</p>
     */
    @Property(tries = 100)
    @Tag("Property-8-Storage-Key-Format")
    void storageKeySanitizesSpecialCharacters(
            @ForAll("identifiersWithSpecialChars") String environment,
            @ForAll("identifiersWithSpecialChars") String zone,
            @ForAll("identifiersWithSpecialChars") String service,
            @ForAll("uuids") UUID messageId) {
        
        String key = StorageKeyGenerator.generateKey(environment, zone, null, service, messageId);
        
        // Key should not contain special characters (except slashes, hyphens in UUID, and dots)
        assertThat(key)
            .as("Storage key should not contain special characters")
            .matches("^[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+/[a-f0-9-]+\\.json$");
    }

    // ========== Property 9: Validation Utilities Consistency ==========

    /**
     * Property 9: Validation Utilities Consistency
     * 
     * <p>For any invalid input (malformed UUID, invalid service name, etc.),
     * validation utilities should throw appropriate InvalidParameterException
     * with descriptive message.</p>
     * 
     * <p><strong>Validates: Requirements 6.2, 6.3, 6.4, 6.5</strong></p>
     */
    @Property(tries = 100)
    @Tag("Property-9-Validation-Utilities-Consistency")
    void validUuidPassesValidation(@ForAll("uuids") UUID uuid) {
        // Valid UUIDs should not throw exceptions
        ValidationUtils.validateUuid(uuid.toString(), "testField");
        assertThat(true).isTrue();
    }

    /**
     * Property 9 (Extended): Invalid UUID throws exception
     */
    @Property(tries = 100)
    @Tag("Property-9-Validation-Utilities-Consistency")
    void invalidUuidThrowsException(@ForAll("invalidUuids") String invalidUuid) {
        assertThatThrownBy(() -> ValidationUtils.validateUuid(invalidUuid, "testField"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("testField")
            .hasMessageContaining("Invalid UUID format");
    }

    /**
     * Property 9 (Extended): Valid service names pass validation
     */
    @Property(tries = 100)
    @Tag("Property-9-Validation-Utilities-Consistency")
    void validServiceNamePassesValidation(@ForAll("validServiceNames") String serviceName) {
        // Valid service names should not throw exceptions
        ValidationUtils.validateServiceName(serviceName);
        assertThat(true).isTrue();
    }

    /**
     * Property 9 (Extended): Invalid service names throw exception
     */
    @Property(tries = 100)
    @Tag("Property-9-Validation-Utilities-Consistency")
    void invalidServiceNameThrowsException(@ForAll("invalidServiceNames") String serviceName) {
        assertThatThrownBy(() -> ValidationUtils.validateServiceName(serviceName))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("serviceName");
    }

    /**
     * Property 9 (Extended): Valid zone IDs pass validation
     */
    @Property(tries = 100)
    @Tag("Property-9-Validation-Utilities-Consistency")
    void validZoneIdPassesValidation(@ForAll("validZoneIds") String zoneId) {
        // Valid zone IDs should not throw exceptions
        ValidationUtils.validateZoneId(zoneId);
        assertThat(true).isTrue();
    }

    /**
     * Property 9 (Extended): Invalid zone IDs throw exception
     */
    @Property(tries = 100)
    @Tag("Property-9-Validation-Utilities-Consistency")
    void invalidZoneIdThrowsException(@ForAll("invalidZoneIds") String zoneId) {
        assertThatThrownBy(() -> ValidationUtils.validateZoneId(zoneId))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("zoneId");
    }

    /**
     * Property 9 (Extended): Valid message exchange patterns pass validation
     */
    @Property(tries = 100)
    @Tag("Property-9-Validation-Utilities-Consistency")
    void validMessageExchangePatternPassesValidation(@ForAll MessageExchangePattern pattern) {
        // Valid patterns should not throw exceptions
        ValidationUtils.validateMessageExchangePattern(pattern.name());
        assertThat(true).isTrue();
    }

    /**
     * Property 9 (Extended): Invalid message exchange patterns throw exception
     */
    @Property(tries = 100)
    @Tag("Property-9-Validation-Utilities-Consistency")
    void invalidMessageExchangePatternThrowsException(@ForAll("invalidPatterns") String pattern) {
        assertThatThrownBy(() -> ValidationUtils.validateMessageExchangePattern(pattern))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("messageExchangePattern");
    }

    /**
     * Property 9 (Extended): Valid access types pass validation
     */
    @Property(tries = 100)
    @Tag("Property-9-Validation-Utilities-Consistency")
    void validAccessTypePassesValidation(@ForAll AccessType accessType) {
        // Valid access types should not throw exceptions
        ValidationUtils.validateAccessType(accessType.name());
        assertThat(true).isTrue();
    }

    /**
     * Property 9 (Extended): Invalid access types throw exception
     */
    @Property(tries = 100)
    @Tag("Property-9-Validation-Utilities-Consistency")
    void invalidAccessTypeThrowsException(@ForAll("invalidAccessTypes") String accessType) {
        assertThatThrownBy(() -> ValidationUtils.validateAccessType(accessType))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("accessType");
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<String> sessionTokens() {
        return Arbitraries.create(() -> UUID.randomUUID().toString());
    }

    @Provide
    Arbitrary<String> timestamps() {
        return Arbitraries.create(() -> Instant.now().toString());
    }

    @Provide
    Arbitrary<String> sharedSecrets() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(16)
            .ofMaxLength(64);
    }

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<String> identifiers() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('-', '_')
            .ofMinLength(3)
            .ofMaxLength(30);
    }

    @Provide
    Arbitrary<String> identifiersWithSpecialChars() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('-', '_', '@', '#', '$', '%', '&', '*', '(', ')', ' ')
            .ofMinLength(3)
            .ofMaxLength(30);
    }

    @Provide
    Arbitrary<String> invalidUuids() {
        return Arbitraries.oneOf(
            Arbitraries.just("not-a-uuid"),
            Arbitraries.just("12345"),
            Arbitraries.just(""),
            Arbitraries.strings().alpha().ofLength(10)
        );
    }

    @Provide
    Arbitrary<String> validServiceNames() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('-', '_')
            .ofMinLength(3)
            .ofMaxLength(30)
            .filter(s -> Character.isLetter(s.charAt(0)));
    }

    @Provide
    Arbitrary<String> invalidServiceNames() {
        return Arbitraries.oneOf(
            Arbitraries.strings().numeric().ofLength(5), // Starts with number
            Arbitraries.just("_invalid"), // Starts with underscore
            Arbitraries.just("-invalid"), // Starts with hyphen
            Arbitraries.just("invalid@name"), // Contains special char
            Arbitraries.just("invalid name"), // Contains space
            Arbitraries.just("") // Empty string
        );
    }

    @Provide
    Arbitrary<String> validZoneIds() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('-', '_')
            .ofMinLength(3)
            .ofMaxLength(30)
            .filter(s -> Character.isLetterOrDigit(s.charAt(0)));
    }

    @Provide
    Arbitrary<String> invalidZoneIds() {
        return Arbitraries.oneOf(
            Arbitraries.just("_invalid"), // Starts with underscore
            Arbitraries.just("-invalid"), // Starts with hyphen
            Arbitraries.just("invalid@zone"), // Contains special char
            Arbitraries.just("invalid zone"), // Contains space
            Arbitraries.just("") // Empty string
        );
    }

    @Provide
    Arbitrary<String> invalidPatterns() {
        return Arbitraries.oneOf(
            Arbitraries.just("INVALID"),
            Arbitraries.just("immediate"), // lowercase
            Arbitraries.just("SYNC"),
            Arbitraries.just(""),
            Arbitraries.strings().alpha().ofLength(10)
        );
    }

    @Provide
    Arbitrary<String> invalidAccessTypes() {
        return Arbitraries.oneOf(
            Arbitraries.just("INVALID"),
            Arbitraries.just("provide"), // lowercase
            Arbitraries.just("READ"),
            Arbitraries.just("WRITE"),
            Arbitraries.just(""),
            Arbitraries.strings().alpha().ofLength(10)
        );
    }
}
