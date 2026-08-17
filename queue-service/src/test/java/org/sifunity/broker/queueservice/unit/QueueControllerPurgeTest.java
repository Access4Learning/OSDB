// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.common.dto.QueueDTO;
import org.sifunity.broker.common.exception.QueueNotFoundException;
import org.sifunity.broker.queueservice.controller.QueueController;
import org.sifunity.broker.queueservice.dto.PurgeAcceptedResponse;
import org.sifunity.broker.queueservice.service.QueueCleanupService;
import org.sifunity.broker.queueservice.service.QueueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QueueController purge endpoint.
 *
 * <p>Requirements: 8.2, 9.1, 9.2, 9.3</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QueueController Purge Endpoint Tests")
class QueueControllerPurgeTest {

    private static final String VALID_TOKEN = "test-internal-token";

    @Mock private QueueService queueService;
    @Mock private QueueCleanupService queueCleanupService;

    private QueueController controller;

    private UUID queueId;
    private UUID ownerId;
    private QueueDTO queueDTO;

    @BeforeEach
    void setUp() {
        controller = new QueueController(queueService, queueCleanupService, VALID_TOKEN);
        queueId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        queueDTO = new QueueDTO(
                queueId, "test-queue", ownerId, "IMMEDIATE",
                "domain/solution/queues/" + queueId + "/messages",
                1, Instant.now(), null, null, 3, 5000L, 0L
        );
    }

    @Nested
    @DisplayName("Purge Returns 202 Accepted")
    class PurgeReturns202Tests {

        @Test
        @DisplayName("Should return 202 with PurgeAcceptedResponse body")
        void purgeEndpointReturns202WithCorrectBody() {
            when(queueService.getQueue(queueId)).thenReturn(queueDTO);

            ResponseEntity<?> response = controller.purgeQueue(
                    "domain", "solution", queueId,
                    ownerId.toString(), null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(response.getBody()).isInstanceOf(PurgeAcceptedResponse.class);

            PurgeAcceptedResponse body = (PurgeAcceptedResponse) response.getBody();
            assertThat(body.queueId()).isEqualTo(queueId);
            assertThat(body.status()).isEqualTo("ACCEPTED");
            assertThat(body.message()).isEqualTo("Queue purge initiated");

            verify(queueCleanupService).submitPurge(eq(queueId), eq("domain"), eq("solution"), eq(ownerId));
        }
    }

    @Nested
    @DisplayName("Purge with No X-Environment-Id Header")
    class PurgeNoEnvironmentHeaderTests {

        @Test
        @DisplayName("Should return 403 when no X-Environment-Id header present")
        void purgeWithNoEnvironmentIdHeader_returns403() {
            when(queueService.getQueue(queueId)).thenReturn(queueDTO);

            ResponseEntity<?> response = controller.purgeQueue(
                    "domain", "solution", queueId,
                    null, null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verifyNoInteractions(queueCleanupService);
        }
    }

    @Nested
    @DisplayName("Purge with Non-Owner Environment")
    class PurgeNonOwnerTests {

        @Test
        @DisplayName("Should return 403 when non-owner environment attempts purge")
        void purgeWithNonOwnerEnvironmentReturns403() {
            UUID nonOwnerId = UUID.randomUUID();
            when(queueService.getQueue(queueId)).thenReturn(queueDTO);

            ResponseEntity<?> response = controller.purgeQueue(
                    "domain", "solution", queueId,
                    nonOwnerId.toString(), null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verifyNoInteractions(queueCleanupService);
        }
    }

    @Nested
    @DisplayName("Purge with Broker-Services Fingerprint")
    class PurgeBrokerServicesBypassTests {

        @Test
        @DisplayName("Should allow purge when broker-services fingerprint with valid token")
        void purgeWithBrokerServicesFingerprintAndToken_bypassesOwnership() {
            UUID nonOwnerId = UUID.randomUUID();
            when(queueService.getQueue(queueId)).thenReturn(queueDTO);

            ResponseEntity<?> response = controller.purgeQueue(
                    "domain", "solution", queueId,
                    nonOwnerId.toString(), "broker-services", VALID_TOKEN);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            verify(queueCleanupService).submitPurge(eq(queueId), eq("domain"), eq("solution"), eq(ownerId));
        }

        @Test
        @DisplayName("Should return 403 when broker-services fingerprint without valid token")
        void purgeWithBrokerServicesFingerprintWithoutToken_returns403() {
            UUID nonOwnerId = UUID.randomUUID();
            when(queueService.getQueue(queueId)).thenReturn(queueDTO);

            ResponseEntity<?> response = controller.purgeQueue(
                    "domain", "solution", queueId,
                    nonOwnerId.toString(), "broker-services", null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verifyNoInteractions(queueCleanupService);
        }
    }

    @Nested
    @DisplayName("Purge Non-Existent Queue")
    class PurgeNonExistentQueueTests {

        @Test
        @DisplayName("Should throw QueueNotFoundException when queue does not exist")
        void purgeOnNonExistentQueueThrows404() {
            UUID nonExistentId = UUID.randomUUID();
            when(queueService.getQueue(nonExistentId))
                    .thenThrow(new QueueNotFoundException("Queue not found: " + nonExistentId));

            assertThatThrownBy(() -> controller.purgeQueue(
                    "domain", "solution", nonExistentId,
                    null, null, null))
                    .isInstanceOf(QueueNotFoundException.class);

            verifyNoInteractions(queueCleanupService);
        }
    }

    @Nested
    @DisplayName("Delete Non-Existent Queue")
    class DeleteNonExistentQueueTests {

        @Test
        @DisplayName("Should throw QueueNotFoundException when deleting non-existent queue")
        void deleteOnNonExistentQueueThrows404() {
            UUID nonExistentId = UUID.randomUUID();
            when(queueService.getQueue(nonExistentId))
                    .thenThrow(new QueueNotFoundException("Queue not found: " + nonExistentId));

            assertThatThrownBy(() -> controller.deleteQueue(
                    "domain", "solution", nonExistentId,
                    null, null, null))
                    .isInstanceOf(QueueNotFoundException.class);

            verifyNoInteractions(queueCleanupService);
        }
    }
}
