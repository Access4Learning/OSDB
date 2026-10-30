package org.sifunity.broker.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.DefaultStackSynthesizer;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Tags;

/**
 * CDK application entry point for the OSDB infrastructure.
 *
 * <p>Deploy with: {@code cdk deploy --all}
 *
 * <p>Configuration is driven by CDK context values. Override defaults with:
 * {@code cdk deploy --all -c environment=PROD -c vpcCidr=10.99.0.0/16 -c tagPrefix=OSDB-PROD}
 */
public class InfraApp {

    public static void main(String[] args) {
        App app = new App();

        // Read configuration from CDK context (overridable via -c flag)
        String envName = (String) app.getNode().tryGetContext("environment");
        if (envName == null) envName = "PROD";

        String account = (String) app.getNode().tryGetContext("account");
        String region = (String) app.getNode().tryGetContext("region");

        String tagPrefix = (String) app.getNode().tryGetContext("tagPrefix");
        if (tagPrefix == null) tagPrefix = "OSDB-" + envName;

        String vpcCidr = (String) app.getNode().tryGetContext("vpcCidr");
        if (vpcCidr == null) vpcCidr = "10.99.0.0/16";

        Environment awsEnv = makeEnv(account, region);

        String prefix = "osdb-" + envName.toLowerCase();

        // Use a synthesizer that does NOT require cdk bootstrap
        DefaultStackSynthesizer synthesizer = DefaultStackSynthesizer.Builder.create()
                .generateBootstrapVersionRule(false)
                .build();

        // Network stack: VPC, subnets, security groups
        NetworkStack network = new NetworkStack(app, prefix + "-network",
                StackProps.builder().env(awsEnv).synthesizer(synthesizer).build(), vpcCidr);

        // Database stack: RDS PostgreSQL + ElastiCache Redis
        DatabaseStack database = new DatabaseStack(app, prefix + "-database",
                StackProps.builder().env(awsEnv).synthesizer(synthesizer).build(),
                network);

        // Storage stack: S3 bucket for message payloads
        StorageStack storage = new StorageStack(app, prefix + "-storage",
                StackProps.builder().env(awsEnv).synthesizer(synthesizer).build(), envName);

        // Services stack: ECS Fargate cluster + all microservices
        ServicesStack services = new ServicesStack(app, prefix + "-services",
                StackProps.builder().env(awsEnv).synthesizer(synthesizer).build(),
                network, database, storage, envName);

        // Apply tags to ALL resources across all stacks
        Tags.of(app).add("Project", tagPrefix);
        Tags.of(app).add("Environment", envName);
        Tags.of(app).add("ManagedBy", "CloudFormation");

        app.synth();
    }

    /**
     * Build an Environment. If account/region are null, CDK uses the CLI's
     * default credentials and region (from ~/.aws/config or env vars).
     */
    private static Environment makeEnv(String account, String region) {
        return Environment.builder()
                .account(account)
                .region(region)
                .build();
    }
}
