// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.property;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jqwik.api.*;
import org.sifunity.broker.common.constant.SubscriptionType;
import org.sifunity.broker.common.dto.SubscriptionDTO;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class MinimalSerializationTest {
    
    @Property(tries = 10)
    void testSerialization() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        SubscriptionDTO dto = new SubscriptionDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "testService",
                "test-zone",
                "test-ctx",
                SubscriptionType.EVENT,
                Instant.now()
        );
        
        String json = objectMapper.writeValueAsString(dto);
        SubscriptionDTO deserialized = objectMapper.readValue(json, SubscriptionDTO.class);
        
        assertThat(deserialized.queueId()).isEqualTo(dto.queueId());
        assertThat(json).contains("\"messageType\"");
    }
}
