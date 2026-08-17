// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.sifunity.broker.queueservice.storage.S3StorageProvider;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property 20: S3 expiration metadata — S3 PutObject includes Expires header.
 *
 * <p>Validates: Requirements 14.2</p>
 */
class S3ExpirationPropertyTest {

    @Provide
    Arbitrary<String> ttlValues() {
        return Arbitraries.of("1d", "7d", "30d", "24h");
    }

    /**
     * **Validates: Requirements 14.2**
     */
    @Property(tries = 100)
    @Label("Feature: redis-streams-queue-service, Property 20: S3 expiration metadata")
    void s3PutObjectIncludesExpiresHeader(
            @ForAll("ttlValues") String ttlValue,
            @ForAll @IntRange(min = 1, max = 100) int payloadSize) {

        S3Client s3Client = mock(S3Client.class);
        ObjectMapper objectMapper = new ObjectMapper();

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        S3StorageProvider provider = new S3StorageProvider(s3Client, "test-bucket", ttlValue, objectMapper);

        String storageKey = "domain/solution/env/" + UUID.randomUUID() + ".json";
        String payload = "{\"data\":\"" + "x".repeat(payloadSize) + "\"}";

        Instant beforeStore = Instant.now();
        provider.store(storageKey, payload);

        // Capture the PutObjectRequest
        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest request = requestCaptor.getValue();

        // Verify Expires header is set
        assertThat(request.expires()).isNotNull();

        // Expires should be approximately now + TTL
        Duration expectedTtl = S3StorageProvider.parseDuration(ttlValue);
        Instant expectedExpires = beforeStore.plus(expectedTtl);

        // Allow 5 seconds tolerance for test execution time
        assertThat(request.expires()).isAfterOrEqualTo(expectedExpires.minusSeconds(5));
        assertThat(request.expires()).isBeforeOrEqualTo(expectedExpires.plusSeconds(5));

        // Verify bucket and key
        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.key()).isEqualTo(storageKey);
        assertThat(request.contentType()).isEqualTo("application/json");
    }
}
