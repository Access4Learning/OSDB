// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.queueservice.storage.S3StorageProvider;
import org.sifunity.broker.queueservice.storage.StorageException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for S3StorageProvider using mocked S3Client.
 */
@ExtendWith(MockitoExtension.class)
class S3StorageProviderTest {
    
    @Mock
    private S3Client s3Client;
    
    private ObjectMapper objectMapper;
    private S3StorageProvider storageProvider;
    
    private static final String BUCKET_NAME = "test-bucket";
    private static final String STORAGE_KEY = "env/zone/context/service/message-id.json";
    private static final String PAYLOAD_TTL = "7d";
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        storageProvider = new S3StorageProvider(s3Client, BUCKET_NAME, PAYLOAD_TTL, objectMapper);
    }
    
    @Test
    void testStore_ShouldStorePayloadInS3WithExpiresHeader() throws Exception {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");
        payload.put("number", 42);
        
        Instant beforeStore = Instant.now();
        
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        
        // When
        String result = storageProvider.store(STORAGE_KEY, payload);
        
        // Then
        assertThat(result).isEqualTo(STORAGE_KEY);
        
        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());
        
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo(BUCKET_NAME);
        assertThat(request.key()).isEqualTo(STORAGE_KEY);
        assertThat(request.contentType()).isEqualTo("application/json");
        // Verify Expires header is set to approximately now + 7 days
        assertThat(request.expires()).isNotNull();
        Instant expectedExpires = beforeStore.plus(Duration.ofDays(7));
        assertThat(request.expires()).isAfterOrEqualTo(expectedExpires.minusSeconds(5));
        assertThat(request.expires()).isBeforeOrEqualTo(expectedExpires.plusSeconds(5));
    }
    
    @Test
    void testStore_ShouldThrowStorageExceptionOnFailure() {
        // Given
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");
        
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("S3 error").build());
        
        // When/Then
        assertThatThrownBy(() -> storageProvider.store(STORAGE_KEY, payload))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to store message in S3");
    }
    
    @Test
    void testRetrieve_ShouldReturnPayloadWhenExists() throws Exception {
        // Given
        Map<String, Object> expectedPayload = new HashMap<>();
        expectedPayload.put("key", "value");
        expectedPayload.put("number", 42);
        
        String json = objectMapper.writeValueAsString(expectedPayload);
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                json.getBytes()
        );
        
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(responseBytes);
        
        // When
        Optional<Object> result = storageProvider.retrieve(STORAGE_KEY);
        
        // Then
        assertThat(result).isPresent();
        @SuppressWarnings("unchecked")
        Map<String, Object> actualPayload = (Map<String, Object>) result.get();
        assertThat(actualPayload).containsEntry("key", "value");
        assertThat(actualPayload).containsEntry("number", 42);
        
        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(requestCaptor.capture());
        
        GetObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo(BUCKET_NAME);
        assertThat(request.key()).isEqualTo(STORAGE_KEY);
    }
    
    @Test
    void testRetrieve_ShouldReturnEmptyWhenNotFound() {
        // Given
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Key not found").build());
        
        // When
        Optional<Object> result = storageProvider.retrieve(STORAGE_KEY);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    void testRetrieve_ShouldThrowStorageExceptionOnOtherFailures() {
        // Given
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("S3 error").build());
        
        // When/Then
        assertThatThrownBy(() -> storageProvider.retrieve(STORAGE_KEY))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to retrieve message from S3");
    }
    
    @Test
    void testDelete_ShouldDeletePayloadFromS3Asynchronously() throws Exception {
        // Given
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());
        
        // When
        storageProvider.delete(STORAGE_KEY);
        
        // Allow async execution to complete
        Thread.sleep(500);
        
        // Then
        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        
        DeleteObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo(BUCKET_NAME);
        assertThat(request.key()).isEqualTo(STORAGE_KEY);
    }
    
    @Test
    void testDelete_ShouldNotThrowOnFailure_BestEffort() throws Exception {
        // Given - S3 delete will fail
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("S3 error").build());
        
        // When - should not throw since delete is async and best-effort
        storageProvider.delete(STORAGE_KEY);
        
        // Allow async execution to complete
        Thread.sleep(500);
        
        // Then - verify the delete was attempted
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }
    
    @Test
    void testExists_ShouldReturnTrueWhenPayloadExists() {
        // Given
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());
        
        // When
        boolean result = storageProvider.exists(STORAGE_KEY);
        
        // Then
        assertThat(result).isTrue();
        
        ArgumentCaptor<HeadObjectRequest> requestCaptor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(requestCaptor.capture());
        
        HeadObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo(BUCKET_NAME);
        assertThat(request.key()).isEqualTo(STORAGE_KEY);
    }
    
    @Test
    void testExists_ShouldReturnFalseWhenPayloadNotFound() {
        // Given
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Key not found").build());
        
        // When
        boolean result = storageProvider.exists(STORAGE_KEY);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    void testExists_ShouldThrowStorageExceptionOnOtherFailures() {
        // Given
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("S3 error").build());
        
        // When/Then
        assertThatThrownBy(() -> storageProvider.exists(STORAGE_KEY))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Failed to check message existence in S3");
    }
    
    @Test
    void testParseDuration_Days() {
        assertThat(S3StorageProvider.parseDuration("7d")).isEqualTo(Duration.ofDays(7));
        assertThat(S3StorageProvider.parseDuration("1d")).isEqualTo(Duration.ofDays(1));
        assertThat(S3StorageProvider.parseDuration("30d")).isEqualTo(Duration.ofDays(30));
    }
    
    @Test
    void testParseDuration_Hours() {
        assertThat(S3StorageProvider.parseDuration("24h")).isEqualTo(Duration.ofHours(24));
        assertThat(S3StorageProvider.parseDuration("1h")).isEqualTo(Duration.ofHours(1));
    }
    
    @Test
    void testParseDuration_Minutes() {
        assertThat(S3StorageProvider.parseDuration("30m")).isEqualTo(Duration.ofMinutes(30));
    }
    
    @Test
    void testParseDuration_Seconds() {
        assertThat(S3StorageProvider.parseDuration("60s")).isEqualTo(Duration.ofSeconds(60));
    }
    
    @Test
    void testParseDuration_NullOrBlank_DefaultsTo7Days() {
        assertThat(S3StorageProvider.parseDuration(null)).isEqualTo(Duration.ofDays(7));
        assertThat(S3StorageProvider.parseDuration("")).isEqualTo(Duration.ofDays(7));
        assertThat(S3StorageProvider.parseDuration("  ")).isEqualTo(Duration.ofDays(7));
    }
    
    @Test
    void testParseDuration_Iso8601() {
        assertThat(S3StorageProvider.parseDuration("PT168H")).isEqualTo(Duration.ofHours(168));
    }

    @Test
    void testDeleteByPrefix_ShouldDeleteAllObjectsWithPrefix() {
        // Given - single page of results
        S3Object obj1 = S3Object.builder().key("domain1/sol1/msg1.json").build();
        S3Object obj2 = S3Object.builder().key("domain1/sol1/msg2.json").build();
        S3Object obj3 = S3Object.builder().key("domain1/sol1/sub/msg3.json").build();

        ListObjectsV2Response listResponse = ListObjectsV2Response.builder()
                .contents(obj1, obj2, obj3)
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(listResponse);
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(DeleteObjectsResponse.builder().build());

        // When
        long deleted = storageProvider.deleteByPrefix("domain1/sol1/");

        // Then
        assertThat(deleted).isEqualTo(3);

        ArgumentCaptor<ListObjectsV2Request> listCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(listCaptor.capture());
        assertThat(listCaptor.getValue().bucket()).isEqualTo(BUCKET_NAME);
        assertThat(listCaptor.getValue().prefix()).isEqualTo("domain1/sol1/");
        assertThat(listCaptor.getValue().maxKeys()).isEqualTo(1000);

        ArgumentCaptor<DeleteObjectsRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(deleteCaptor.capture());
        assertThat(deleteCaptor.getValue().bucket()).isEqualTo(BUCKET_NAME);
        assertThat(deleteCaptor.getValue().delete().objects()).hasSize(3);
    }

    @Test
    void testDeleteByPrefix_ShouldPaginateWithContinuationToken() {
        // Given - two pages of results
        S3Object obj1 = S3Object.builder().key("domain1/msg1.json").build();
        S3Object obj2 = S3Object.builder().key("domain1/msg2.json").build();

        ListObjectsV2Response firstPage = ListObjectsV2Response.builder()
                .contents(obj1)
                .isTruncated(true)
                .nextContinuationToken("token-abc")
                .build();

        ListObjectsV2Response secondPage = ListObjectsV2Response.builder()
                .contents(obj2)
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(firstPage)
                .thenReturn(secondPage);
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(DeleteObjectsResponse.builder().build());

        // When
        long deleted = storageProvider.deleteByPrefix("domain1/");

        // Then
        assertThat(deleted).isEqualTo(2);
        verify(s3Client, times(2)).listObjectsV2(any(ListObjectsV2Request.class));
        verify(s3Client, times(2)).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void testDeleteByPrefix_ShouldReturnZeroWhenNoObjectsFound() {
        // Given - empty results
        ListObjectsV2Response emptyResponse = ListObjectsV2Response.builder()
                .contents(java.util.Collections.emptyList())
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(emptyResponse);

        // When
        long deleted = storageProvider.deleteByPrefix("nonexistent/");

        // Then
        assertThat(deleted).isEqualTo(0);
        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }
}
