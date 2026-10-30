package org.sifunity.broker.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.LifecycleRule;
import software.constructs.Construct;

import java.util.List;

/**
 * Storage infrastructure: S3 bucket for message payloads.
 *
 * <p>Bucket naming: {@code osdb-messages-<environment>-<account-suffix>}
 * <p>Lifecycle: Messages older than 30 days are automatically deleted.
 */
public class StorageStack extends Stack {

    private final Bucket messageBucket;

    public StorageStack(final Construct scope, final String id, final StackProps props,
                        final String environment) {
        super(scope, id, props);

        this.messageBucket = Bucket.Builder.create(this, "MessagePayloadBucket")
                .bucketName("osdb-messages-" + environment.toLowerCase() + "-" + generateSuffix())
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .encryption(BucketEncryption.S3_MANAGED)
                .versioned(false)
                .removalPolicy(RemovalPolicy.DESTROY)       // Allow teardown to delete bucket
                .lifecycleRules(List.of(
                        LifecycleRule.builder()
                                .id("auto-delete-after-30-days")
                                .expiration(Duration.days(30))
                                .enabled(true)
                                .build()
                ))
                .build();
    }

    public Bucket getMessageBucket() {
        return messageBucket;
    }

    /**
     * Generates a short suffix from the stack's account/region to ensure bucket name uniqueness.
     * S3 bucket names are globally unique, so we append context to avoid collisions.
     */
    private String generateSuffix() {
        // CDK will replace this with the actual account hash at synth time
        return software.amazon.awscdk.Fn.select(4,
                software.amazon.awscdk.Fn.split("-",
                        software.amazon.awscdk.Fn.select(2,
                                software.amazon.awscdk.Fn.split("/", getStackId()))));
    }
}
