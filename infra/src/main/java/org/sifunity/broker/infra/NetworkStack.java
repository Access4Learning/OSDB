package org.sifunity.broker.infra;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.IpAddresses;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.constructs.Construct;

import java.util.List;

/**
 * Network infrastructure: VPC with public and private subnets.
 *
 * <p>Layout:
 * <ul>
 *   <li>Public subnets: ALB only</li>
 *   <li>Private subnets (with NAT): ECS tasks, RDS, Redis</li>
 * </ul>
 *
 * <p>The CIDR block is configurable to avoid conflicts with existing VPCs.
 * Default: 10.99.0.0/16 (chosen to avoid common 10.0.0.0/16 conflicts).
 */
public class NetworkStack extends Stack {

    private final Vpc vpc;

    public NetworkStack(final Construct scope, final String id, final StackProps props,
                        final String vpcCidr) {
        super(scope, id, props);

        this.vpc = Vpc.Builder.create(this, "OsdbVpc")
                .ipAddresses(IpAddresses.cidr(vpcCidr))
                .maxAzs(2)
                .subnetConfiguration(List.of(
                        SubnetConfiguration.builder()
                                .name("public")
                                .subnetType(SubnetType.PUBLIC)
                                .cidrMask(24)
                                .build(),
                        SubnetConfiguration.builder()
                                .name("private")
                                .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                                .cidrMask(24)
                                .build()
                ))
                .build();
    }

    public Vpc getVpc() {
        return vpc;
    }
}
