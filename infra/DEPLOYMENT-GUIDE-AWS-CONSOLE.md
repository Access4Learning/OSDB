# OSDB Deployment Guide — AWS Console (UI Only)

This guide walks you through deploying the SIF Unity Broker (OSDB) entirely from the AWS Console. No command-line deployment required. Every step uses the AWS web UI.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Generate the CloudFormation Template](#2-generate-the-cloudformation-template)
3. [Create ECR Repositories and Push Docker Images](#3-create-ecr-repositories-and-push-docker-images)
4. [Create Secrets in Secrets Manager](#4-create-secrets-in-secrets-manager)
5. [Deploy the Network Stack](#5-deploy-the-network-stack)
6. [Deploy the Database Stack](#6-deploy-the-database-stack)
7. [Deploy the Storage Stack](#7-deploy-the-storage-stack)
8. [Deploy the Services Stack](#8-deploy-the-services-stack)
9. [Post-Deployment Configuration (IAM, S3, Logs — Auto-Created)](#9-post-deployment-configuration)
10. [Verify the Deployment](#10-verify-the-deployment)
11. [Secrets Architecture (AES-256-GCM Encrypted)](#11-secrets-architecture-aes-256-gcm-encrypted)
12. [Configure Route 53 (Custom Domain + HTTPS)](#12-configure-route-53-custom-domain)
13. [Teardown](#13-teardown)

---

## 1. Prerequisites

Before you begin, ensure you have:

- An **AWS Account** with admin access (or at minimum: CloudFormation, ECS, RDS, ElastiCache, S3, ECR, Secrets Manager, VPC, IAM permissions)
- **Docker Desktop** installed locally (to build images)
- **Java 17** and **Maven 3.9+** installed (to build the project and synthesize templates)
- The OSDB source code checked out locally

---

## 2. Generate the CloudFormation Template

The CDK project generates CloudFormation YAML that you upload to the console.

On your local machine, run this ONE TIME to produce the templates:

```bash
cd infra
mvn compile exec:java
```

This creates a `cdk.out/` directory containing CloudFormation JSON files — one per stack:

- `osdb-dev-network.template.json`
- `osdb-dev-database.template.json`
- `osdb-dev-storage.template.json`
- `osdb-dev-services.template.json`

> **Note:** You need the CDK CLI installed locally just for synthesis: `npm install -g aws-cdk`, then run `cdk synth --all` instead of the mvn command above. This produces the same templates in `cdk.out/`.

Keep these files — you'll upload them to the CloudFormation console in the steps below.

---

## 3. Create ECR Repositories and Push Docker Images

Each microservice needs its Docker image stored in AWS ECR.

### 3a. Create Repositories (Console)

1. Open the **ECR Console**: https://console.aws.amazon.com/ecr/
2. Click **Create repository**
3. Set visibility to **Private**
4. For **Repository name**, enter: `osdb/gateway`
5. Leave defaults, click **Create repository**
6. Repeat for each service:
   - `osdb/environment-service`
   - `osdb/queue-service`
   - `osdb/subscription-service`
   - `osdb/job-service`
   - `osdb/events-connector`
   - `osdb/requests-connector`
   - `osdb/responses-connector`
   - `osdb/services-connector`

### 3b. Push Images (Local Terminal)

For each service, you need to build and push. ECR gives you the exact commands — click any repository in the console and click **"View push commands"** for copy-paste instructions.

The general pattern (replace `ACCOUNT_ID` and `REGION`):

```bash
# Authenticate Docker to ECR (one time)
aws ecr get-login-password --region REGION | docker login --username AWS --password-stdin ACCOUNT_ID.dkr.ecr.REGION.amazonaws.com

# Build the service (from project root)
cd gateway
mvn clean package -DskipTests
docker build -t osdb/gateway .

# Tag and push
docker tag osdb/gateway:latest ACCOUNT_ID.dkr.ecr.REGION.amazonaws.com/osdb/gateway:latest
docker push ACCOUNT_ID.dkr.ecr.REGION.amazonaws.com/osdb/gateway:latest
```

Repeat for all 9 services.

> **Tip:** The `View push commands` button in the ECR console gives you these exact commands pre-filled with your account ID and region.

---

## 4. Create Secrets in Secrets Manager

The OSDB uses a **database-encrypted secrets** model (see [Section 11](#11-secrets-architecture-database-encrypted) for the full architecture). However, you still need a few bootstrap secrets in AWS for the infrastructure itself.

### 4a. Database Master Password

The CDK template **auto-generates** the RDS master password and stores it in Secrets Manager automatically when the database stack deploys. You do NOT need to create this manually.

After the database stack deploys (step 6), you'll find it in Secrets Manager with a name like `osdb-<instance>-database/OsdbPostgres/Secret-XXXXXX`. The ECS tasks reference it automatically.

> **Skip this step** — it's handled by CloudFormation.

### 4b. OSDB Admin Bootstrap Secret

This is the initial shared secret used to create the first admin environment in OSDB.

1. Click **Store a new secret**
2. Secret type: **Other type of secret**
3. Key/value pairs:
   - Key: `adminSharedSecret` → Value: (generate a 64-character random string)
   - Key: `adminSessionToken` → Value: (generate a 64-character random string)
4. Click **Next**
5. Secret name: `osdb/admin/bootstrap-credentials`
6. Description: `Bootstrap credentials for initial OSDB admin environment`
7. Click **Next** → **Next** → **Store**

### 4c. Redis Auth Token (Optional but Recommended)

1. Click **Store a new secret**
2. Secret type: **Other type of secret**
3. Key/value pairs:
   - Key: `authToken` → Value: (generate a 32+ character alphanumeric string)
4. Secret name: `osdb/redis/auth-token`
5. Click **Next** → **Next** → **Store**

### 4d. Encryption Key for Shared Secrets

This key is used by the environment-service to encrypt/decrypt per-environment shared secrets in the database.

1. Generate a 32-byte random key locally:
   ```powershell
   # PowerShell
   [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])
   ```
2. Click **Store a new secret**
3. Secret type: **Other type of secret**
4. Key/value pairs:
   - Key: `ENCRYPTION_KEY_20250508` → Value: (paste the base64 string)
5. Secret name: `osdb/encryption-keys`
6. Description: `AES-256-GCM encryption key for OSDB environment shared secrets`
7. Click **Next** → **Next** → **Store**

> **Important:** Save this key version name (`20250508`) — you'll configure it in the ECS task environment as `SECRETS_BACKEND_MULTI_KEY_CURRENT_VERSION=20250508`.

---

## 5. Deploy the Network Stack

1. Open **CloudFormation Console**: https://console.aws.amazon.com/cloudformation/
2. Click **Create stack** → **With new resources (standard)**
3. Choose **Upload a template file**
4. Click **Choose file** → select `cdk.out/osdb-demo-network.template.json`
5. Click **Next**
6. Stack name: `osdb-demo-network`
7. No parameters needed
8. Click **Next** → **Next**
9. Review, then click **Submit**
10. Wait for status: **CREATE_COMPLETE** (usually 2-3 minutes)

> **What this creates:** VPC (10.99.0.0/16), 2 public subnets, 2 private subnets, NAT gateway, internet gateway, route tables.

---

## 6. Deploy the Database Stack

1. In CloudFormation Console, click **Create stack** → **With new resources (standard)**
2. Upload `cdk.out/osdb-demo-database.template.json`
3. Click **Next**
4. Stack name: `osdb-demo-database`
5. No parameters needed — VPC and subnet references are automatically imported from the network stack
6. Click **Next** → **Next** → **Submit**
7. Wait for status: **CREATE_COMPLETE** (usually 8-12 minutes for RDS)

> **What this creates:** RDS PostgreSQL 14 instance (db.t3.medium), ElastiCache Redis node (cache.t3.micro), security groups for each. The RDS master password is auto-generated and stored in Secrets Manager.

---

## 7. Deploy the Storage Stack

1. Click **Create stack** → **With new resources (standard)**
2. Upload `cdk.out/osdb-demo-storage.template.json`
3. Click **Next**
4. Stack name: `osdb-demo-storage`
5. No parameters needed
6. Click **Next** → **Next** → **Submit**
7. Wait for status: **CREATE_COMPLETE** (usually 30 seconds)

> **What this creates:** S3 bucket for message payloads (named `osdb-messages-demo-<suffix>`), encryption enabled, public access blocked, 30-day lifecycle rule.

---

## 8. Deploy the Services Stack

1. Click **Create stack** → **With new resources (standard)**
2. Upload `cdk.out/osdb-demo-services.template.json`
3. Click **Next**
4. Stack name: `osdb-demo-services`
5. No parameters needed — VPC, database endpoints, S3 bucket, and Redis endpoint are all automatically imported from the other stacks
6. Click **Next**
7. On the options page, check **I acknowledge that AWS CloudFormation might create IAM resources** (the ECS tasks need IAM roles)
8. Click **Next** → **Submit**
9. Wait for status: **CREATE_COMPLETE** (usually 5-8 minutes)

> **What this creates:** ECS Fargate cluster, Application Load Balancer, 9 Fargate services, task definitions, IAM task roles with S3 access, CloudWatch log groups.

---

## 9. Post-Deployment Configuration

### What CloudFormation Created Automatically (IAM & S3)

The CDK-generated templates automatically provision the following — you don't need to create these manually:

**IAM Roles (auto-created per service):**

| Role | Purpose | Permissions |
|------|---------|-------------|
| Task Execution Role | ECS agent uses this to pull images and write logs | `ecr:GetAuthorizationToken`, `ecr:BatchGetImage`, `logs:CreateLogStream`, `logs:PutLogEvents` |
| Task Role (gateway) | The running gateway container's identity | S3 read/write to message bucket, Secrets Manager read for `osdb/*` secrets |
| Task Role (services) | Each internal service's container identity | S3 read/write to message bucket, Secrets Manager read for `osdb/*` secrets |

**S3 Bucket (auto-created by Storage stack):**

| What | Details |
|------|---------|
| Bucket name | Auto-generated (e.g., `osdb-dev-storage-messagepayloadbucket-XXXXX`) |
| Passed to services as | Environment variable `S3_BUCKET_NAME` on every ECS task |
| Access granted via | IAM policy on each task role (auto-attached by CDK) |
| Encryption | S3-managed (AES-256) |
| Public access | Blocked entirely |

You do NOT need to manually create the S3 bucket, configure bucket policies, or attach IAM policies. The CloudFormation template handles all of this. The services receive the bucket name as an environment variable and have IAM permissions to read/write objects in it.

**CloudWatch Log Groups (auto-created):**

Each service gets a log group at `/ecs/osdb/<service-name>`. Logs are retained for 30 days by default.

---

### 9a. Database Migrations (Automatic)

Flyway migrations run **automatically** when the environment-service starts for the first time. No manual action needed.

When the ECS task boots, Spring Boot detects an empty database and Flyway applies all migrations from `db/migration/` (creating tables, indexes, constraints, etc.).

You can verify migrations ran by checking the environment-service logs in CloudWatch:
1. Open **CloudWatch Console** → **Log groups**
2. Find `/ecs/osdb/<instance>/environment-service`
3. Look for lines like: `Successfully applied X migrations`

### 9b. Find Your ALB URL

1. Open **EC2 Console** → **Load Balancers**
2. Find the ALB created by the services stack (named something like `osdb-dev-services-Gatew...`)
3. Copy the **DNS name** (e.g., `osdb-dev-services-Gatew-XXXXX.us-east-1.elb.amazonaws.com`)
4. This is your OSDB endpoint

### 9c. Create the Initial Admin Environment

Using the ALB URL and the bootstrap credentials from step 4b:

```
POST http://<ALB-DNS>/api/environments
```

With HMAC authentication using the `adminSessionToken` and `adminSharedSecret` you stored in Secrets Manager.

See `API-AUTHENTICATION-GUIDE.md` in the project root for the full HMAC signing process.

---

## 10. Verify the Deployment

### Health Checks

Hit the gateway health endpoint:

```
GET http://<ALB-DNS>/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

### Check ECS Service Status

1. Open **ECS Console** → your cluster
2. All 9 services should show **Running tasks: 1/1** (or 2/2 for gateway)
3. If any show 0 running tasks, click the service → **Events** tab to see why

### Check RDS Connectivity

1. Open **RDS Console** → your instance
2. Status should be **Available**
3. Check **Monitoring** tab for connection count (should be > 0 after services start)

---

## 11. Secrets Architecture (AES-256-GCM Encrypted)

The OSDB uses **AES-256-GCM encryption** (reversible, two-way) for environment shared secrets. This is NOT hashing — the secrets must be decryptable because HMAC verification requires the original plaintext.

### How It Works

```
┌─────────────────────────────────────────────────────────┐
│  AWS Secrets Manager (infrastructure secrets only)      │
│  ┌─────────────────────────────────────────────┐        │
│  │ osdb/database/master-credentials            │        │
│  │ (RDS login — used by Spring Boot datasource)│        │
│  └─────────────────────────────────────────────┘        │
│  ┌─────────────────────────────────────────────┐        │
│  │ osdb/admin/bootstrap-credentials            │        │
│  │ (one-time admin setup only)                 │        │
│  └─────────────────────────────────────────────┘        │
│  ┌─────────────────────────────────────────────┐        │
│  │ osdb/encryption-keys                        │        │
│  │ ENCRYPTION_KEY_20240115=<base64 32-byte key>│        │
│  │ (injected as env var into ECS tasks)        │        │
│  └─────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│  OSDB Application (environment-service)                 │
│                                                         │
│  Configuration (application-prod.yml):                  │
│  secrets:                                               │
│    backend:                                             │
│      type: multi-key                                    │
│      multi-key:                                         │
│        current-version: "20240115"                      │
│        keys:                                            │
│          "20240115": "${ENCRYPTION_KEY_20240115}"        │
│                                                         │
│  When a new environment registers:                      │
│  1. Generate shared_secret (random 64 chars)            │
│  2. Encrypt with AES-256-GCM using current key          │
│  3. Store as "20240115:<base64_encrypted>" in DB        │
│  4. Return plaintext shared_secret to caller ONCE       │
│                                                         │
│  On every API request (HMAC verification):              │
│  1. Look up encrypted secret from DB                    │
│  2. Parse version prefix ("20240115:")                  │
│  3. Decrypt using the matching key version              │
│  4. Use plaintext to verify HMAC-SHA256 signature       │
│     (which includes the request timestamp)              │
└─────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│  PostgreSQL (RDS)                                       │
│  ┌─────────────────────────────────────────────┐        │
│  │ environments table                          │        │
│  │ ├── id (UUID)                               │        │
│  │ ├── application_name                        │        │
│  │ ├── session_token (plaintext, rotatable)    │        │
│  │ ├── shared_secret = "20240115:base64data"   │        │
│  │ │   └── version prefix tells which key      │        │
│  │ │       was used to encrypt this value      │        │
│  │ ├── ...                                     │        │
│  └─────────────────────────────────────────────┘        │
│                                                         │
│  Encryption at rest: RDS storage encryption (AES-256)   │
│  Encryption in transit: TLS required                    │
└─────────────────────────────────────────────────────────┘
```

### Key Rotation

The multi-key backend supports seamless key rotation:

1. Generate a new 32-byte random key, base64-encode it
2. Add it to Secrets Manager under a new version name (e.g., `ENCRYPTION_KEY_20250601`)
3. Update `current-version` in the application config to `"20250601"`
4. Keep the old key configured — existing secrets still decrypt with it
5. (Optional) Run a migration job to re-encrypt all secrets with the new key
6. Remove old key only after all secrets are migrated

### What Gets Stored Where

| Secret | Where | Format |
|--------|-------|--------|
| RDS master password | AWS Secrets Manager | Plaintext (managed by AWS) |
| Admin bootstrap secret | AWS Secrets Manager | Plaintext (one-time use) |
| Redis auth token | AWS Secrets Manager | Plaintext (infra-level) |
| AES encryption key(s) | AWS Secrets Manager → ECS env var | Base64-encoded 32 bytes |
| Per-environment shared secrets | PostgreSQL `environments` table | `YYYYMMDD:<base64_AES-GCM_encrypted>` |
| Per-environment session tokens | PostgreSQL `environments` table | Plaintext (rotatable) |

### Creating the Encryption Key (Step 4d — Add to Secrets Manager)

1. Generate a 32-byte random key (on your local machine):
   ```bash
   # Linux/Mac
   openssl rand -base64 32
   
   # PowerShell
   [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])
   ```
2. Open **Secrets Manager Console**
3. Click **Store a new secret** → **Other type of secret**
4. Key/value:
   - Key: `ENCRYPTION_KEY_20250508` → Value: (paste the base64 string from step 1)
5. Secret name: `osdb/encryption-keys`
6. Click **Next** → **Next** → **Store**

This secret's value is injected into ECS tasks as an environment variable. The application reads it via `${ENCRYPTION_KEY_20250508}` in its Spring config.

### Why This Model (Not Hashing)

- **HMAC verification needs the plaintext** — the caller signs requests with `HMAC-SHA256(shared_secret, timestamp + method + path + body)`. The server must reconstruct the same HMAC, which requires the original secret.
- **Encryption is reversible** — AES-256-GCM lets us decrypt when needed for verification.
- **Version prefix enables rotation** — old secrets encrypted with old keys still work.
- **Portable** — the encryption is application-level (Java code), not tied to any cloud service. Moving to Azure/GCP only requires changing where the encryption key env var comes from.

### Security Layers

1. **Network isolation** — RDS and Redis in private subnets, no public access
2. **Encryption at rest** — RDS storage encryption (AES-256), S3 server-side encryption
3. **Encryption in transit** — TLS enforced on all connections
4. **Application-level encryption** — shared secrets encrypted with AES-256-GCM before DB storage
5. **IAM roles** — ECS tasks use IAM roles (no access keys in containers)
6. **Security groups** — only VPC-internal traffic reaches DB and Redis
7. **Key versioning** — supports rotation without downtime

---

## 12. Configure Route 53 (Custom Domain)

If you want a friendly URL like `osdb.yourdomain.com` instead of the raw ALB DNS name:

### 12a. Prerequisites

- A domain registered in Route 53 (or a hosted zone for a domain you own elsewhere)
- An ACM (AWS Certificate Manager) certificate for your domain (for HTTPS)

### 12b. Create an ACM Certificate

1. Open **ACM Console**: https://console.aws.amazon.com/acm/
2. Click **Request a certificate** → **Request a public certificate**
3. Domain name: `osdb.yourdomain.com` (or `*.yourdomain.com` for wildcard)
4. Validation method: **DNS validation** (recommended)
5. Click **Request**
6. On the certificate details page, click **Create records in Route 53** (if your domain is in Route 53 — this auto-validates)
7. Wait for status: **Issued** (usually 5-15 minutes)

### 12c. Add HTTPS Listener to ALB

1. Open **EC2 Console** → **Load Balancers**
2. Select your OSDB ALB
3. Click **Listeners** tab → **Add listener**
4. Protocol: **HTTPS**, Port: **443**
5. Default action: **Forward to** → select the gateway target group
6. Security policy: `ELBSecurityPolicy-TLS13-1-2-2021-06` (or latest)
7. Default SSL certificate: select the ACM certificate from step 12b
8. Click **Add**
9. (Optional) Edit the HTTP:80 listener to redirect to HTTPS:443

### 12d. Create Route 53 DNS Record

1. Open **Route 53 Console**: https://console.aws.amazon.com/route53/
2. Click **Hosted zones** → select your domain
3. Click **Create record**
4. Record name: `osdb` (creates `osdb.yourdomain.com`)
5. Record type: **A**
6. Toggle **Alias** to ON
7. Route traffic to: **Alias to Application and Classic Load Balancer**
8. Region: your deployment region
9. Select your OSDB ALB from the dropdown
10. Click **Create records**

Your OSDB is now reachable at `https://osdb.yourdomain.com`.

---

## 13. Teardown

Designed for repeated build/teardown cycles. Stacks delete cleanly in reverse order.

To remove everything (in reverse order):

1. CloudFormation Console → select `osdb-<instance>-services` → **Delete**
2. Wait for DELETE_COMPLETE
3. Delete `osdb-<instance>-storage` (bucket is auto-emptied and deleted — `DESTROY` policy)
4. Delete `osdb-<instance>-database` (creates a final snapshot by default)
5. Delete `osdb-<instance>-network`
6. (Optional) Delete ECR repositories if no longer needed
7. (Optional) Delete secrets from Secrets Manager if no longer needed

> **Secrets survive teardown.** The secrets you created in step 4 are NOT part of the CloudFormation stacks — they persist between deploy/teardown cycles. This means you can tear down and redeploy without recreating secrets each time.

> **RDS snapshots.** Deleting the database stack creates a final RDS snapshot. You'll be charged for snapshot storage until you delete it manually. For frequent teardown/rebuild cycles, you can skip the snapshot by modifying the template's `DeletionPolicy` to `Delete` (already set for non-prod in the CDK code).

> **S3 bucket.** The bucket uses `DESTROY` removal policy with `autoDeleteObjects: true`, so it empties and deletes itself on stack deletion. No manual cleanup needed.

### Rebuild After Teardown

To stand it back up again:
1. Rebuild Docker images if code changed (step 3b)
2. Re-deploy stacks in order (steps 5 → 6 → 7 → 8)
3. Secrets are already in place — no need to recreate them
4. Flyway migrations run automatically on first boot

---

## Quick Reference

| Resource | Console URL |
|----------|-------------|
| CloudFormation | https://console.aws.amazon.com/cloudformation/ |
| ECR | https://console.aws.amazon.com/ecr/ |
| ECS | https://console.aws.amazon.com/ecs/ |
| RDS | https://console.aws.amazon.com/rds/ |
| ElastiCache | https://console.aws.amazon.com/elasticache/ |
| S3 | https://console.aws.amazon.com/s3/ |
| Secrets Manager | https://console.aws.amazon.com/secretsmanager/ |
| EC2 (Load Balancers) | https://console.aws.amazon.com/ec2/#LoadBalancers |
| VPC | https://console.aws.amazon.com/vpc/ |
