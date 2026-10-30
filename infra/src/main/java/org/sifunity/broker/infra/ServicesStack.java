package org.sifunity.broker.infra;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticache.CfnCacheCluster;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.services.servicediscovery.NamespaceType;
import software.constructs.Construct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ECS Fargate services stack using Service Connect for inter-service communication.
 *
 * <p>Architecture:
 * <ul>
 *   <li>Gateway: public ALB → Fargate task (only public-facing service)</li>
 *   <li>Internal services: Fargate tasks with Service Connect (no ALBs)</li>
 *   <li>Service discovery: Cloud Map namespace ({@code osdb.local})</li>
 * </ul>
 *
 * <p>The gateway routes to internal services via their Cloud Map names:
 * {@code http://environment-service:8081}, {@code http://queue-service:8082}, etc.
 *
 * <p>This eliminates the need for internal ALBs and hardcoded URLs.
 */
public class ServicesStack extends Stack {

    private final Cluster cluster;
    private final SecurityGroup serviceSecurityGroup;

    public ServicesStack(final Construct scope, final String id, final StackProps props,
                         final NetworkStack network,
                         final DatabaseStack database,
                         final StorageStack storage,
                         final String environment) {
        super(scope, id, props);

        // ECS Cluster with Cloud Map namespace for Service Connect
        this.cluster = Cluster.Builder.create(this, "OsdbCluster")
                .clusterName("osdb-" + environment.toLowerCase())
                .vpc(network.getVpc())
                .containerInsightsV2(ContainerInsights.ENHANCED)
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                        .name("osdb.local")
                        .type(software.amazon.awscdk.services.servicediscovery.NamespaceType.HTTP)
                        .build())
                .build();

        // Set ServiceConnectDefaults so ECS knows to use this namespace for Service Connect
        CfnCluster cfnCluster = (CfnCluster) cluster.getNode().getDefaultChild();
        cfnCluster.setServiceConnectDefaults(
                CfnCluster.ServiceConnectDefaultsProperty.builder()
                        .namespace("osdb.local")
                        .build());

        // Shared security group for all services (allows inter-service traffic)
        this.serviceSecurityGroup = SecurityGroup.Builder.create(this, "ServicesSg")
                .vpc(network.getVpc())
                .description("Shared SG for all OSDB ECS services")
                .allowAllOutbound(true)
                .build();
        // Allow all traffic within the security group (service-to-service)
        serviceSecurityGroup.addIngressRule(serviceSecurityGroup, Port.allTcp(),
                "Allow inter-service communication");

        // Shared environment variables
        String dbEndpoint = database.getPostgres().getDbInstanceEndpointAddress();
        String dbPort = database.getPostgres().getDbInstanceEndpointPort();
        String redisEndpoint = database.getRedis().getAttrRedisEndpointAddress();
        String bucketName = storage.getMessageBucket().getBucketName();

        // RDS credentials from Secrets Manager
        ISecret dbSecret = database.getPostgres().getSecret();

        // Encryption keys from Secrets Manager
        ISecret encryptionKeysSecret = software.amazon.awscdk.services.secretsmanager.Secret.fromSecretNameV2(
                this, "EncryptionKeysSecret", "osdb/encryption-keys");

        // Bootstrap admin credentials from Secrets Manager
        ISecret bootstrapSecret = software.amazon.awscdk.services.secretsmanager.Secret.fromSecretNameV2(
                this, "BootstrapCredentialsSecret", "osdb/admin/bootstrap-credentials");

        // ECS secrets (injected from Secrets Manager at container start)
        Map<String, Secret> sharedSecrets = Map.of(
                "SPRING_DATASOURCE_PASSWORD", Secret.fromSecretsManager(dbSecret, "password"),
                "ENCRYPTION_KEY_20250508", Secret.fromSecretsManager(encryptionKeysSecret, "ENCRYPTION_KEY_20250508"),
                "ADMIN_SESSION_TOKEN", Secret.fromSecretsManager(bootstrapSecret, "adminSessionToken"),
                "ADMIN_SHARED_SECRET", Secret.fromSecretsManager(bootstrapSecret, "adminSharedSecret")
        );

        // Base environment variables shared by all services
        Map<String, String> baseEnv = new HashMap<>(Map.of(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://" + dbEndpoint + ":" + dbPort + "/osdb",
                "SPRING_DATASOURCE_USERNAME", "osdb_admin",
                "REDIS_HOST", redisEndpoint,
                "REDIS_PORT", "6379",
                "STORAGE_TYPE", "s3",
                "S3_BUCKET_NAME", bucketName,
                "S3_BUCKET", bucketName,
                "SPRING_PROFILES_ACTIVE", "prod",
                "OSDB_ENVIRONMENT", environment
        ));
        baseEnv.put("SECRETS_BACKEND_MULTI_KEY_CURRENT_VERSION", "20250508");
        baseEnv.put("MESSAGE_STORAGE_TYPE", "s3");
        baseEnv.put("MESSAGE_STORAGE_S3_BUCKET", bucketName);

        // Inter-service URLs via Service Connect (used by connectors to reach other services)
        baseEnv.put("SUBSCRIPTION_SERVICE_URL", "http://subscription-service:8083");
        baseEnv.put("ENVIRONMENT_SERVICE_URL", "http://environment-service:8081");
        baseEnv.put("QUEUE_SERVICE_URL", "http://queue-service:8082");
        baseEnv.put("RESPONSES_CONNECTOR_URL", "http://responses-connector:8088");

        // OpenTelemetry: point OTLP exporter to the CloudWatch agent sidecar
        baseEnv.put("OBSERVABILITY_TRACING_OTLP_ENDPOINT", "http://localhost:4316/v1/traces");
        baseEnv.put("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "http://localhost:4316/v1/traces");
        baseEnv.put("OTEL_TRACES_SAMPLER", "xray");
        baseEnv.put("OTEL_PROPAGATORS", "tracecontext,baggage,b3,xray");
        baseEnv.put("OTEL_RESOURCE_ATTRIBUTES", "service.name=${SPRING_APPLICATION_NAME:-osdb}");

        // Gateway gets additional env vars for routing to internal services via Service Connect
        Map<String, String> gatewayEnv = new HashMap<>(baseEnv);
        gatewayEnv.put("ENVIRONMENT_SERVICE_URL", "http://environment-service:8081");
        gatewayEnv.put("SIF_BROKER_SERVICES_QUEUE_SERVICE_URL", "http://queue-service:8082");
        gatewayEnv.put("SIF_BROKER_SERVICES_SUBSCRIPTION_SERVICE_URL", "http://subscription-service:8083");
        gatewayEnv.put("SIF_BROKER_SERVICES_JOB_SERVICE_URL", "http://job-service:8084");
        gatewayEnv.put("SIF_BROKER_SERVICES_EVENTS_CONNECTOR_URL", "http://events-connector:8084");
        gatewayEnv.put("SIF_BROKER_SERVICES_REQUESTS_CONNECTOR_URL", "http://requests-connector:8085");
        gatewayEnv.put("SIF_BROKER_SERVICES_RESPONSES_CONNECTOR_URL", "http://responses-connector:8088");
        gatewayEnv.put("SIF_BROKER_SERVICES_SERVICES_CONNECTOR_URL", "http://services-connector:8087");

        // --- Gateway (public-facing, behind ALB) ---
        ApplicationLoadBalancedFargateService gateway = createGateway(
                gatewayEnv, sharedSecrets, network, environment);
        storage.getMessageBucket().grantReadWrite(gateway.getTaskDefinition().getTaskRole());

        // --- Internal services (Service Connect, no ALBs) ---
        List<ServiceConfig> internalServices = List.of(
                new ServiceConfig("environment-service", 8081, 512, 1024),
                new ServiceConfig("queue-service", 8082, 1024, 2048),
                new ServiceConfig("subscription-service", 8083, 256, 512),
                new ServiceConfig("job-service", 8084, 256, 512),
                new ServiceConfig("events-connector", 8084, 512, 1024),
                new ServiceConfig("requests-connector", 8085, 256, 512),
                new ServiceConfig("responses-connector", 8088, 256, 512),
                new ServiceConfig("services-connector", 8087, 256, 512)
        );

        List<String> allServiceNames = new ArrayList<>();
        allServiceNames.add(gateway.getService().getServiceName());
        for (ServiceConfig svc : internalServices) {
            FargateService service = createInternalService(
                    svc.name(), svc.port(), svc.cpu(), svc.memoryMiB(),
                    baseEnv, sharedSecrets, network, environment);
            // Grant S3 access
            storage.getMessageBucket().grantReadWrite(service.getTaskDefinition().getTaskRole());
            allServiceNames.add(service.getServiceName());
        }

        new DashboardConstruct(this, "MonitoringDashboard",
                environment,
                cluster.getClusterName(),
                allServiceNames,
                database.getPostgres(),
                database.getRedis(),
                (software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer) gateway.getLoadBalancer());
    }

    /**
     * Creates the public-facing gateway behind an ALB with Service Connect enabled.
     */
    private ApplicationLoadBalancedFargateService createGateway(
            Map<String, String> env, Map<String, Secret> secrets,
            NetworkStack network, String environment) {

        LogGroup logGroup = LogGroup.Builder.create(this, "GatewayLogs")
                .logGroupName("/ecs/osdb/" + environment.toLowerCase() + "/gateway")
                .retention(RetentionDays.ONE_MONTH)
                .build();

        ApplicationLoadBalancedFargateService gateway = ApplicationLoadBalancedFargateService.Builder
                .create(this, "GatewayService")
                .cluster(cluster)
                .cpu(512)
                .memoryLimitMiB(2048)
                .desiredCount(2)
                .healthCheckGracePeriod(software.amazon.awscdk.Duration.seconds(300))
                .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                        .image(ecrImage("gateway"))
                        .containerPort(8080)
                        .environment(env)
                        .secrets(secrets)
                        .containerName("gateway")
                        .build())
                .taskSubnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                        .build())
                .securityGroups(List.of(serviceSecurityGroup))
                .publicLoadBalancer(true)
                .build();

        // Use liveness endpoint for ALB health check (doesn't depend on downstream services)
        gateway.getTargetGroup().configureHealthCheck(
                software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck.builder()
                        .path("/actuator/health/liveness")
                        .healthyHttpCodes("200")
                        .interval(software.amazon.awscdk.Duration.seconds(30))
                        .timeout(software.amazon.awscdk.Duration.seconds(10))
                        .healthyThresholdCount(2)
                        .unhealthyThresholdCount(5)
                        .build());

        // Add CloudWatch Agent policy to gateway task role
        gateway.getTaskDefinition().getTaskRole().addManagedPolicy(
                software.amazon.awscdk.services.iam.ManagedPolicy.fromAwsManagedPolicyName("CloudWatchAgentServerPolicy"));
        gateway.getTaskDefinition().getTaskRole().addManagedPolicy(
                software.amazon.awscdk.services.iam.ManagedPolicy.fromAwsManagedPolicyName("AWSXRayDaemonWriteAccess"));

        // CloudWatch Agent sidecar on gateway
        gateway.getTaskDefinition().addContainer("ecs-cwagent", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("public.ecr.aws/cloudwatch-agent/cloudwatch-agent:latest"))
                .containerName("ecs-cwagent")
                .essential(false)
                .secrets(Map.of(
                        "CW_CONFIG_CONTENT", Secret.fromSsmParameter(
                                software.amazon.awscdk.services.ssm.StringParameter.fromStringParameterName(
                                        this, "GatewayCwParam", "ecs-cwagent"))
                ))
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(LogGroup.Builder.create(this, "GatewayCwAgentLogs")
                                .logGroupName("/ecs/osdb/" + environment.toLowerCase() + "/cwagent-gateway")
                                .retention(RetentionDays.ONE_MONTH)
                                .build())
                        .streamPrefix("cwagent")
                        .build()))
                .build());

        // Enable Service Connect as a CLIENT so the gateway can resolve
        // other services by DNS name (http://environment-service:8081, etc.)
        gateway.getService().enableServiceConnect(ServiceConnectProps.builder()
                .namespace("osdb.local")
                .build());

        return gateway;
    }

    /**
     * Creates an internal service with Service Connect (no ALB).
     * Registers the service in Cloud Map so other services can reach it by name.
     */
    private FargateService createInternalService(
            String name, int port, int cpu, int memoryMiB,
            Map<String, String> env, Map<String, Secret> secrets,
            NetworkStack network, String environment) {

        LogGroup logGroup = LogGroup.Builder.create(this, capitalize(name) + "Logs")
                .logGroupName("/ecs/osdb/" + environment.toLowerCase() + "/" + name)
                .retention(RetentionDays.ONE_MONTH)
                .build();

        // Task definition with per-service CPU/memory sizing
        FargateTaskDefinition taskDef = FargateTaskDefinition.Builder.create(this, capitalize(name) + "TaskDef")
                .cpu(cpu)
                .memoryLimitMiB(memoryMiB)
                .build();

        // Add CloudWatch Agent policy to task role for X-Ray and Application Signals
        taskDef.getTaskRole().addManagedPolicy(
                software.amazon.awscdk.services.iam.ManagedPolicy.fromAwsManagedPolicyName("CloudWatchAgentServerPolicy"));
        taskDef.getTaskRole().addManagedPolicy(
                software.amazon.awscdk.services.iam.ManagedPolicy.fromAwsManagedPolicyName("AWSXRayDaemonWriteAccess"));

        // Application container
        ContainerDefinition container = taskDef.addContainer(name, ContainerDefinitionOptions.builder()
                .image(ecrImage(name))
                .containerName(name)
                .environment(env)
                .secrets(secrets)
                .portMappings(List.of(PortMapping.builder()
                        .containerPort(port)
                        .name(name) // Port mapping name used by Service Connect
                        .protocol(Protocol.TCP)
                        .build()))
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(logGroup)
                        .streamPrefix(name)
                        .build()))
                .build());

        // CloudWatch Agent sidecar (receives OTLP traces, exports to X-Ray + Application Signals)
        taskDef.addContainer("ecs-cwagent", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("public.ecr.aws/cloudwatch-agent/cloudwatch-agent:latest"))
                .containerName("ecs-cwagent")
                .essential(false)
                .secrets(Map.of(
                        "CW_CONFIG_CONTENT", Secret.fromSsmParameter(
                                software.amazon.awscdk.services.ssm.StringParameter.fromStringParameterName(
                                        this, capitalize(name) + "CwParam", "ecs-cwagent"))
                ))
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(logGroup)
                        .streamPrefix("cwagent")
                        .build()))
                .build());

        // Fargate service with Service Connect
        FargateService service = FargateService.Builder.create(this, capitalize(name) + "Service")
                .cluster(cluster)
                .taskDefinition(taskDef)
                .desiredCount(1)
                .securityGroups(List.of(serviceSecurityGroup))
                .vpcSubnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                        .build())
                .serviceConnectConfiguration(ServiceConnectProps.builder()
                        .services(List.of(ServiceConnectService.builder()
                                .portMappingName(name)
                                .dnsName(name)  // This is how other services reach it: http://<name>:<port>
                                .port(port)
                                .build()))
                        .build())
                .build();

        return service;
    }

    /**
     * Looks up an existing ECR repository by name.
     */
    private ContainerImage ecrImage(String serviceName) {
        IRepository repo = Repository.fromRepositoryName(this,
                capitalize(serviceName) + "Repo", "osdb/" + serviceName);
        return ContainerImage.fromEcrRepository(repo, "latest");
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        for (String part : s.split("-")) {
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1));
        }
        return sb.toString();
    }

    private record ServiceConfig(String name, int port, int cpu, int memoryMiB) {}
}
