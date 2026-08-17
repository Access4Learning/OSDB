// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;
import java.time.Duration;

/**
 * Storage configuration for message payload storage.
 * 
 * <p>Configures S3Client bean when S3 storage is enabled.
 * The storage provider (S3 or filesystem) is selected based on
 * the message.storage.type property.</p>
 * 
 * <p>Supports endpoint override for LocalStack and other S3-compatible services.</p>
 * 
 * <p>Requirements: 8.4, 8.5</p>
 */
@Configuration
public class StorageConfig {
    
    /**
     * Configure S3Client for S3 storage provider.
     * 
     * <p>This bean is only created when message.storage.type=s3.
     * Supports endpoint override for LocalStack via sif.broker.storage.s3.endpoint property.
     * 
     * <p>Uses AWS default credentials provider which checks:
     * <ul>
     *   <li>Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)</li>
     *   <li>System properties</li>
     *   <li>AWS credentials file (~/.aws/credentials)</li>
     *   <li>IAM role for EC2/ECS/Lambda</li>
     * </ul>
     * 
     * <p>For LocalStack, you can provide test credentials via:
     * <ul>
     *   <li>sif.broker.storage.s3.access-key (defaults to "test")</li>
     *   <li>sif.broker.storage.s3.secret-key (defaults to "test")</li>
     * </ul>
     * 
     * @param region the AWS region for S3 operations
     * @param endpoint optional endpoint override for LocalStack/S3-compatible services
     * @param accessKey optional access key for LocalStack
     * @param secretKey optional secret key for LocalStack
     * @return configured S3Client
     * 
     * <p>Requirements: 8.4, 8.5</p>
     */
    @Bean
    @ConditionalOnProperty(name = "message.storage.type", havingValue = "s3")
    public S3Client s3Client(
            @Value("${message.storage.s3.region:us-east-1}") String region,
            @Value("${sif.broker.storage.s3.endpoint:#{null}}") String endpoint,
            @Value("${sif.broker.storage.s3.access-key:#{null}}") String accessKey,
            @Value("${sif.broker.storage.s3.secret-key:#{null}}") String secretKey) {
        
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(100)
                        .connectionTimeout(Duration.ofSeconds(5)));
        
        // Configure endpoint override for LocalStack
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
            
            // Use path-style access for LocalStack
            builder.forcePathStyle(true);
            
            // Use static credentials for LocalStack if provided
            if (accessKey != null && secretKey != null) {
                builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                    )
                );
            }
        } else {
            // Use default credentials provider for real AWS
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        
        return builder.build();
    }
}
