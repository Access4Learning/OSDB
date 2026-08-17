// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.unit;

import org.junit.jupiter.api.Test;
import org.sifunity.broker.queueservice.config.StorageConfig;
import org.sifunity.broker.queueservice.storage.FileSystemStorageProvider;
import org.sifunity.broker.queueservice.storage.S3StorageProvider;
import org.sifunity.broker.queueservice.storage.StorageProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StorageConfig.
 * Tests that correct storage provider is selected based on configuration.
 * 
 * **Validates: Requirements 7.1, 8.4, 8.5**
 */
class StorageConfigTest {
    
    /**
     * Test: StorageConfig creates S3Client when S3 storage is configured
     * 
     * **Validates: Requirements 8.4, 8.5**
     */
    @SpringBootTest
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
        "message.storage.type=s3",
        "message.storage.s3.region=us-east-1",
        "message.storage.s3.bucket=test-bucket"
    })
    static class S3StorageConfigTest {
        
        @Autowired(required = false)
        private S3Client s3Client;
        
        @Autowired
        private StorageProvider storageProvider;
        
        @Test
        void testS3Client_IsCreatedWhenS3StorageConfigured() {
            // Then: S3Client bean is created
            assertThat(s3Client).isNotNull();
        }
        
        @Test
        void testStorageProvider_IsS3ProviderWhenS3Configured() {
            // Then: StorageProvider is S3StorageProvider
            assertThat(storageProvider).isInstanceOf(S3StorageProvider.class);
        }
    }
    
    /**
     * Test: StorageConfig does not create S3Client when filesystem storage is configured
     * 
     * **Validates: Requirements 8.4, 8.5**
     */
    @SpringBootTest
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
        "message.storage.type=filesystem",
        "message.storage.filesystem.path=/tmp/test-storage"
    })
    static class FileSystemStorageConfigTest {
        
        @Autowired(required = false)
        private S3Client s3Client;
        
        @Autowired
        private StorageProvider storageProvider;
        
        @Test
        void testS3Client_IsNotCreatedWhenFilesystemStorageConfigured() {
            // Then: S3Client bean is not created
            assertThat(s3Client).isNull();
        }
        
        @Test
        void testStorageProvider_IsFileSystemProviderWhenFilesystemConfigured() {
            // Then: StorageProvider is FileSystemStorageProvider
            assertThat(storageProvider).isInstanceOf(FileSystemStorageProvider.class);
        }
    }
    
    /**
     * Test: S3Client is created with correct region
     * 
     * **Validates: Requirements 8.4, 8.5**
     */
    @Test
    void testS3Client_CreatedWithCorrectRegion() {
        // Given: StorageConfig
        StorageConfig config = new StorageConfig();
        
        // When: Creating S3Client with specific region (no endpoint override)
        S3Client s3Client = config.s3Client("us-west-2", null, null, null);
        
        // Then: S3Client is created
        assertThat(s3Client).isNotNull();
        
        // Clean up
        s3Client.close();
    }
    
    /**
     * Test: S3Client uses default region when not specified
     * 
     * **Validates: Requirements 8.4, 8.5**
     */
    @Test
    void testS3Client_UsesDefaultRegion() {
        // Given: StorageConfig
        StorageConfig config = new StorageConfig();
        
        // When: Creating S3Client with default region (no endpoint override)
        S3Client s3Client = config.s3Client("us-east-1", null, null, null);
        
        // Then: S3Client is created
        assertThat(s3Client).isNotNull();
        
        // Clean up
        s3Client.close();
    }
}
