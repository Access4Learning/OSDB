// SPDX-License-Identifier: MIT
package org.sifunity.integration.container;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.net.URI;
import java.time.Duration;

/**
 * LocalStack test container configuration for integration tests.
 * 
 * <p>This container provides a LocalStack instance for S3 simulation during integration testing.
 * LocalStack allows testing AWS services locally without requiring real AWS infrastructure.
 * The container is configured with:
 * <ul>
 *   <li>Image: localstack/localstack:latest</li>
 *   <li>Services: S3 only (minimal configuration)</li>
 *   <li>Exposed port: 4566</li>
 *   <li>S3 bucket: sif-broker-test-messages (created on startup)</li>
 *   <li>Health check: Wait for S3 service to be ready</li>
 * </ul>
 * 
 * <p>The container is started once before all tests and stopped after all tests complete.
 * The S3 bucket is cleared between tests to ensure test isolation.
 * 
 * @since 1.0.0
 */
@Slf4j
public class LocalStackTestContainer extends LocalStackContainer {
    
    private static final String IMAGE_VERSION = "localstack/localstack:3.4";
    private static final String BUCKET_NAME = "sif-broker-test-messages";
    private static final Region REGION = Region.US_EAST_1;
    
    private static LocalStackTestContainer container;
    private S3Client s3Client;
    
    /**
     * Creates a new LocalStack test container with default configuration.
     */
    private LocalStackTestContainer() {
        super(DockerImageName.parse(IMAGE_VERSION));
        
        // Configure LocalStack to only run S3 service
        withServices(Service.S3);
        
        // Configure health check - wait for S3 service to be ready
        waitingFor(Wait.forLogMessage(".*Ready\\.*\\n", 1)
            .withStartupTimeout(Duration.ofSeconds(120)));
        
        // Enable reuse for faster test execution (optional)
        withReuse(false); // Set to true for development to reuse containers
        
        log.info("LocalStack test container configured for S3 service");
    }
    
    /**
     * Gets the singleton instance of the LocalStack test container.
     * 
     * @return the LocalStack test container instance
     */
    public static LocalStackTestContainer getInstance() {
        if (container == null) {
            container = new LocalStackTestContainer();
        }
        return container;
    }
    
    /**
     * Starts the LocalStack container and creates the S3 bucket.
     */
    @Override
    public void start() {
        super.start();
        log.info("LocalStack test container started: endpoint={}", getEndpoint());
        
        // Create S3 client
        createS3Client();
        
        // Create S3 bucket
        createS3Bucket();
    }
    
    /**
     * Stops the LocalStack container and closes the S3 client.
     */
    @Override
    public void stop() {
        log.info("Stopping LocalStack test container");
        
        // Close S3 client
        if (s3Client != null) {
            s3Client.close();
        }
        
        super.stop();
    }
    
    /**
     * Creates an S3 client configured to connect to LocalStack.
     */
    private void createS3Client() {
        s3Client = S3Client.builder()
            .endpointOverride(getEndpoint())
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(getAccessKey(), getSecretKey())
                )
            )
            .region(REGION)
            .build();
        
        log.info("S3 client created for LocalStack");
    }
    
    /**
     * Creates the S3 bucket for test messages.
     */
    private void createS3Bucket() {
        try {
            // Check if bucket already exists
            s3Client.headBucket(HeadBucketRequest.builder()
                .bucket(BUCKET_NAME)
                .build());
            
            log.info("S3 bucket already exists: {}", BUCKET_NAME);
        } catch (Exception e) {
            // Bucket doesn't exist, create it
            try {
                s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(BUCKET_NAME)
                    .build());
                
                log.info("S3 bucket created: {}", BUCKET_NAME);
            } catch (Exception createException) {
                log.error("Failed to create S3 bucket: {}", BUCKET_NAME, createException);
                throw new RuntimeException("Failed to create S3 bucket", createException);
            }
        }
    }
    
    /**
     * Gets the LocalStack endpoint URI for S3.
     * 
     * @return the endpoint URI
     */
    public URI getEndpoint() {
        return getEndpointOverride(Service.S3);
    }
    
    /**
     * Gets the LocalStack endpoint URL as a string.
     * 
     * @return the endpoint URL
     */
    public String getEndpointUrl() {
        return getEndpoint().toString();
    }
    
    /**
     * Gets the S3 bucket name.
     * 
     * @return the bucket name
     */
    public String getBucketName() {
        return BUCKET_NAME;
    }
    
    /**
     * Gets the AWS region as a Region object.
     * 
     * @return the region
     */
    public Region getAwsRegion() {
        return REGION;
    }
    
    /**
     * Gets the S3 client for interacting with LocalStack.
     * 
     * @return the S3 client
     */
    public S3Client getS3Client() {
        return s3Client;
    }
    
    /**
     * Clears all objects from the S3 bucket.
     * This should be called between tests to ensure test isolation.
     */
    public void clearBucket() {
        try {
            s3Client.listObjectsV2(builder -> builder.bucket(BUCKET_NAME))
                .contents()
                .forEach(object -> {
                    s3Client.deleteObject(builder -> builder
                        .bucket(BUCKET_NAME)
                        .key(object.key()));
                    log.debug("Deleted S3 object: {}", object.key());
                });
            
            log.info("S3 bucket cleared: {}", BUCKET_NAME);
        } catch (Exception e) {
            log.error("Failed to clear S3 bucket: {}", BUCKET_NAME, e);
        }
    }
}
