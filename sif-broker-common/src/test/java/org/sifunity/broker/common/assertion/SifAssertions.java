// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.assertion;

import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifMessageEnvelope;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Custom assertions for testing SIF compliance.
 * <p>
 * This utility class provides assertion methods to validate that SIF message envelopes,
 * error responses, and storage keys conform to the SIF 3.7 specification and broker
 * implementation standards.
 * </p>
 * <p>
 * These assertions are designed to be used in unit and property-based tests to ensure
 * consistent validation of SIF-compliant data structures across all microservices.
 * </p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * SifMessageEnvelope<?> envelope = createTestEnvelope();
 * SifAssertions.assertValidSifEnvelope(envelope);
 * 
 * SifErrorResponse error = SifErrorResponse.fromException(exception);
 * SifAssertions.assertValidSifError(error);
 * 
 * String key = StorageKeyGenerator.generateKey(env, zone, context, service, messageId);
 * SifAssertions.assertStorageKeyFormat(key);
 * }</pre>
 */
public class SifAssertions {
    
    /**
     * Asserts that a SIF message envelope is valid according to SIF 3.7 specification.
     * <p>
     * This method validates that all required fields are present and non-null:
     * </p>
     * <ul>
     *   <li>The envelope itself is not null</li>
     *   <li>messageId is not null</li>
     *   <li>timestamp is not null</li>
     *   <li>messageType is not null</li>
     *   <li>serviceName is not blank</li>
     *   <li>serviceType is not null</li>
     * </ul>
     *
     * @param envelope the SIF message envelope to validate
     * @throws AssertionError if any required field is missing or invalid
     */
    public static void assertValidSifEnvelope(SifMessageEnvelope<?> envelope) {
        assertThat(envelope).isNotNull();
        assertThat(envelope.getMessageId()).isNotNull();
        assertThat(envelope.getTimestamp()).isNotNull();
        assertThat(envelope.getMessageType()).isNotNull();
        assertThat(envelope.getServiceName()).isNotBlank();
        assertThat(envelope.getServiceType()).isNotNull();
    }
    
    /**
     * Asserts that a SIF error response is valid according to SIF 3.7 specification.
     * <p>
     * This method validates that all required fields are present and properly formatted:
     * </p>
     * <ul>
     *   <li>The error response itself is not null</li>
     *   <li>code is not blank</li>
     *   <li>message is not blank</li>
     *   <li>scope is "BROKER"</li>
     *   <li>timestamp is not null</li>
     * </ul>
     *
     * @param error the SIF error response to validate
     * @throws AssertionError if any required field is missing or invalid
     */
    public static void assertValidSifError(SifErrorResponse error) {
        assertThat(error).isNotNull();
        assertThat(error.id()).isNotBlank();
        assertThat(error.code()).isGreaterThanOrEqualTo(400);
        assertThat(error.subCode()).isNotBlank();
        assertThat(error.message()).isNotBlank();
        assertThat(error.scope()).isEqualTo("BROKER");
        assertThat(error.timestamp()).isNotNull();
    }
    
    /**
     * Asserts that a storage key follows the expected hierarchical format.
     * <p>
     * The storage key must match the format:
     * <code>{environment}/{zone}/{context}/{service}/{messageId}.json</code>
     * </p>
     * <p>
     * This method validates:
     * </p>
     * <ul>
     *   <li>The key is not blank</li>
     *   <li>The key matches the hierarchical pattern with 5 segments separated by slashes</li>
     *   <li>The last segment is a UUID followed by .json extension</li>
     * </ul>
     *
     * @param key the storage key to validate
     * @throws AssertionError if the key format is invalid
     */
    public static void assertStorageKeyFormat(String key) {
        assertThat(key).isNotBlank();
        assertThat(key).matches("^[^/]+/[^/]+/[^/]+/[^/]+/[a-f0-9-]+\\.json$");
    }
    
    /**
     * Private constructor to prevent instantiation of this utility class.
     *
     * @throws UnsupportedOperationException if instantiation is attempted
     */
    private SifAssertions() {
        throw new UnsupportedOperationException("Utility class");
    }
}
