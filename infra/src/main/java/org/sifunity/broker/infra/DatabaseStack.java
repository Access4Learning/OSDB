package org.sifunity.broker.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.elasticache.CfnCacheCluster;
import software.amazon.awscdk.services.elasticache.CfnSubnetGroup;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.rds.DatabaseInstanceEngine;
import software.amazon.awscdk.services.rds.PostgresEngineVersion;
import software.amazon.awscdk.services.rds.PostgresInstanceEngineProps;
import software.constructs.Construct;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Database infrastructure: RDS PostgreSQL and ElastiCache Redis.
 */
public class DatabaseStack extends Stack {

    private final DatabaseInstance postgres;
    private final CfnCacheCluster redis;
    private final SecurityGroup dbSecurityGroup;
    private final SecurityGroup redisSecurityGroup;

    public DatabaseStack(final Construct scope, final String id, final StackProps props,
                         final NetworkStack network) {
        super(scope, id, props);

        // Security group for PostgreSQL
        this.dbSecurityGroup = SecurityGroup.Builder.create(this, "DbSecurityGroup")
                .vpc(network.getVpc())
                .description("Allow PostgreSQL access from private subnets")
                .allowAllOutbound(false)
                .build();
        dbSecurityGroup.addIngressRule(
                Peer.ipv4(network.getVpc().getVpcCidrBlock()),
                Port.tcp(5432),
                "PostgreSQL from VPC");

        // RDS PostgreSQL 14
        this.postgres = DatabaseInstance.Builder.create(this, "OsdbPostgres")
                .engine(DatabaseInstanceEngine.postgres(PostgresInstanceEngineProps.builder()
                        .version(PostgresEngineVersion.VER_14)
                        .build()))
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.MEDIUM))
                .vpc(network.getVpc())
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PRIVATE_WITH_EGRESS).build())
                .securityGroups(List.of(dbSecurityGroup))
                .credentials(Credentials.fromGeneratedSecret("osdb_admin"))
                .databaseName("osdb")
                .multiAz(false) // set true for production
                .allocatedStorage(20)
                .maxAllocatedStorage(100)
                .backupRetention(Duration.days(7))
                .removalPolicy(RemovalPolicy.SNAPSHOT)
                .build();

        // Security group for Redis
        this.redisSecurityGroup = SecurityGroup.Builder.create(this, "RedisSecurityGroup")
                .vpc(network.getVpc())
                .description("Allow Redis access from private subnets")
                .allowAllOutbound(false)
                .build();
        redisSecurityGroup.addIngressRule(
                Peer.ipv4(network.getVpc().getVpcCidrBlock()),
                Port.tcp(6379),
                "Redis from VPC");

        // ElastiCache Redis subnet group
        List<String> privateSubnetIds = network.getVpc()
                .getPrivateSubnets().stream()
                .map(s -> s.getSubnetId())
                .collect(Collectors.toList());

        CfnSubnetGroup redisSubnetGroup = CfnSubnetGroup.Builder.create(this, "RedisSubnetGroup")
                .description("Redis subnet group for OSDB")
                .subnetIds(privateSubnetIds)
                .build();

        // ElastiCache Redis (single node for dev; use replication group for prod)
        this.redis = CfnCacheCluster.Builder.create(this, "OsdbRedis")
                .engine("redis")
                .cacheNodeType("cache.t3.micro")
                .numCacheNodes(1)
                .cacheSubnetGroupName(redisSubnetGroup.getRef())
                .vpcSecurityGroupIds(List.of(redisSecurityGroup.getSecurityGroupId()))
                .build();
    }

    public DatabaseInstance getPostgres() {
        return postgres;
    }

    public CfnCacheCluster getRedis() {
        return redis;
    }

    public SecurityGroup getDbSecurityGroup() {
        return dbSecurityGroup;
    }

    public SecurityGroup getRedisSecurityGroup() {
        return redisSecurityGroup;
    }
}
