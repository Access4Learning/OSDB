// SPDX-License-Identifier: MIT
package org.sifunity.broker.subscriptionservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.exception.ServiceUnavailableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for QueueServiceClient.
 *
 * <p>Tests verify queue existence checks via the shared database.</p>
 */
@ExtendWith(MockitoExtension.class)
class QueueServiceClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private QueueServiceClient queueServiceClient;

    @BeforeEach
    void setUp() {
        queueServiceClient = new QueueServiceClient(restTemplate, jdbcTemplate, "http://queue-service:8082");
    }

    @Test
    void queueExists_returnsTrue_whenQueueFound() {
        UUID queueId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM queues WHERE id = ?"), eq(Integer.class), any(UUID.class)))
                .thenReturn(1);

        assertThat(queueServiceClient.queueExists(queueId)).isTrue();
    }

    @Test
    void queueExists_returnsFalse_whenQueueNotFound() {
        UUID queueId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM queues WHERE id = ?"), eq(Integer.class), any(UUID.class)))
                .thenReturn(0);

        assertThat(queueServiceClient.queueExists(queueId)).isFalse();
    }

    @Test
    void queueExists_throwsServiceUnavailable_onDatabaseError() {
        UUID queueId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM queues WHERE id = ?"), eq(Integer.class), any(UUID.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> queueServiceClient.queueExists(queueId))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Queue Service");
    }
}
