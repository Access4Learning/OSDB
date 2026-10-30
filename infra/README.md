# OSDB Infrastructure (AWS CDK)

Infrastructure-as-code for deploying the SIF Unity Broker on AWS using the AWS CDK (Java).

## What Gets Deployed

| Stack | Resources |
|-------|-----------|
| **Network** | VPC, 2 AZs, public + private subnets, NAT gateway |
| **Database** | RDS PostgreSQL 14, ElastiCache Redis |
| **Storage** | S3 bucket for message payloads |
| **Services** | ECS Fargate cluster, ALB, all 9 microservices |

## Prerequisites

1. **AWS CLI** configured with credentials (`aws configure`)
2. **Node.js 18+** (CDK CLI is a Node tool)
3. **AWS CDK CLI**: `npm install -g aws-cdk`
4. **Java 17** and **Maven 3.9+**
5. **Docker images** pushed to a registry (ECR or Docker Hub)

## Quick Start

```bash
# One-time: bootstrap CDK in your AWS account/region
cdk bootstrap aws://ACCOUNT_ID/REGION

# Deploy everything
cd infra
cdk deploy --all

# Deploy with overrides
cdk deploy --all -c environment=production -c region=us-west-2
```

## Configuration

Override defaults via CDK context (`-c` flag):

| Context Key | Default | Description |
|-------------|---------|-------------|
| `environment` | `dev` | Environment name (used in stack naming) |
| `account` | CLI default | AWS account ID |
| `region` | CLI default | AWS region |

## Stack Architecture

```
Internet
    │
    ▼
┌─────────────────────────────────────────────┐
│  ALB (public subnet)                        │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│  ECS Fargate (private subnet)               │
│  ┌──────────┐ ┌────────────────────┐        │
│  │ Gateway  │ │ Internal Services  │        │
│  │ (public) │ │ (private ALBs)     │        │
│  └──────────┘ └────────────────────┘        │
│                                             │
│  ┌──────────┐ ┌──────────┐ ┌─────┐         │
│  │ RDS PG   │ │ Redis    │ │ S3  │         │
│  │ (private)│ │ (private)│ │     │         │
│  └──────────┘ └──────────┘ └─────┘         │
└─────────────────────────────────────────────┘
```

## Useful Commands

| Command | Description |
|---------|-------------|
| `cdk synth` | Generate CloudFormation templates (no deploy) |
| `cdk diff` | Show pending changes vs deployed |
| `cdk deploy --all` | Deploy all stacks |
| `cdk destroy --all` | Tear down all stacks |
| `mvn compile` | Compile the CDK app |

## Moving to Multi-Cloud

This CDK project targets AWS only. When you need Azure/GCP:

- **Option B (CDKTF)**: Same Java constructs, generates Terraform instead of CloudFormation
- **Option C (K8s)**: Use the existing `k8s/` manifests in each service, deploy infra-only via CDK/CDKTF

The mental model and structure transfer directly — expect days, not weeks.

## Production Checklist

- [ ] Set `multiAz(true)` on RDS
- [ ] Use Redis replication group instead of single node
- [ ] Configure ACM certificate on ALB for HTTPS
- [ ] Set up Route 53 DNS
- [ ] Push Docker images to ECR (replace `osdb/*:latest` references)
- [ ] Add WAF rules on ALB
- [ ] Enable RDS Performance Insights
- [ ] Configure auto-scaling policies on ECS services
