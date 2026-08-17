// SPDX-License-Identifier: MIT
package org.sifunity.broker.common;

import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.assertion.SifAssertions;
import org.sifunity.broker.common.builder.TestBuilders;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;
import org.sifunity.broker.common.dto.EnvironmentDTO;
import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.common.generator.TestDataGenerators;
import org.sifunity.broker.common.model.SifErrorResponse;
import org.sifunity.broker.common.model.SifMessageEnvelope;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for test utilities to verify they work correctly together.
 * This test validates that TestDataGenerators, TestBuilders, and SifAssertions
 * all function properly and can be used together.
 */
class TestUtilitiesIntegrationTest {

    @Test
    void testBuilders_shouldCreateValidDTOs() {
        // Test EnvironmentDTO builder
        EnvironmentDTO env = TestBuilders.environment()
            .applicationName("TestApp")
            .type(EnvironmentType.BROKERED)
            .build();
        
        assertThat(env).isNotNull();
        assertThat(env.applicationName()).isEqualTo("TestApp");
        assertThat(env.type()).isEqualTo(EnvironmentType.BROKERED);
        assertThat(env.id()).isNotNull();
        assertThat(env.sessionToken()).isNotNull();
        assertThat(env.createdAt()).isNotNull();
        
        // Test QueueDTO builder
        QueueDTO queue = TestBuilders.queue()
            .name("test-queue")
            .maxRetries(5)
            .build();
        
        assertThat(queue).isNotNull();
        assertThat(queue.name()).isEqualTo("test-queue");
        assertThat(queue.maxRetries()).isEqualTo(5);
        assertThat(queue.id()).isNotNull();
        assertThat(queue.ownerId()).isNotNull();
    }

    @Test
    void testDataGenerators_shouldGenerateValidData() {
        // Test that generators can be instantiated and used
        assertThat(TestDataGenerators.uuids()).isNotNull();
        assertThat(TestDataGenerators.instants()).isNotNull();
        assertThat(TestDataGenerators.nonBlankStrings()).isNotNull();
        assertThat(TestDataGenerators.messageExchangePatterns()).isNotNull();
        assertThat(TestDataGenerators.environmentTypes()).isNotNull();
        assertThat(TestDataGenerators.accessTypes()).isNotNull();
        assertThat(TestDataGenerators.accessLevels()).isNotNull();
        assertThat(TestDataGenerators.serviceNames()).isNotNull();
        assertThat(TestDataGenerators.zoneIds()).isNotNull();
        assertThat(TestDataGenerators.environments()).isNotNull();
        assertThat(TestDataGenerators.queues()).isNotNull();
        assertThat(TestDataGenerators.aclEntries()).isNotNull();
    }

    @Test
    void sifAssertions_shouldValidateEnvelopes() {
        // Create a valid envelope using builder
        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageType(MessageType.REQUEST)
            .serviceName("StudentPersonal")
            .serviceType(ServiceType.OBJECT)
            .payload("test payload")
            .build();
        
        // This should not throw any exception
        SifAssertions.assertValidSifEnvelope(envelope);
    }

    @Test
    void sifAssertions_shouldValidateErrorResponses() {
        // Create a valid error response
        SifErrorResponse error = new SifErrorResponse(
            "uuid-test-1", 401, "AUTH_001",
            "Invalid token",
            null,
            "BROKER",
            Instant.now(),
            null,
            null
        );
        
        // This should not throw any exception
        SifAssertions.assertValidSifError(error);
    }

    @Test
    void sifAssertions_shouldValidateStorageKeys() {
        // Test valid storage key format
        String validKey = "env1/zone1/context1/StudentPersonal/550e8400-e29b-41d4-a716-446655440000.json";
        
        // This should not throw any exception
        SifAssertions.assertStorageKeyFormat(validKey);
    }

    @Test
    void testUtilities_shouldWorkTogether() {
        // Use builders to create DTOs
        EnvironmentDTO env = TestBuilders.environment()
            .applicationName("IntegrationTest")
            .type(EnvironmentType.BROKERED)
            .build();
        
        QueueDTO queue = TestBuilders.queue()
            .name("integration-queue")
            .ownerId(env.id())
            .build();
        
        // Create envelope
        SifMessageEnvelope<String> envelope = SifMessageEnvelope.<String>builder()
            .messageType(MessageType.EVENT)
            .serviceName("TestService")
            .serviceType(ServiceType.UTILITY)
            .payload("Integration test payload")
            .build();
        
        // Validate using assertions
        SifAssertions.assertValidSifEnvelope(envelope);
        
        // Verify the integration worked
        assertThat(env.applicationName()).isEqualTo("IntegrationTest");
        assertThat(queue.ownerId()).isEqualTo(env.id());
        assertThat(envelope.getServiceName()).isEqualTo("TestService");
    }
}