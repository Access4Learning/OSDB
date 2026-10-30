# Setup Guide

Detailed instructions for setting up and configuring the Bruno API Test Suite.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Environment-Specific Setup](#environment-specific-setup)
- [Credential Management](#credential-management)
- [Verification](#verification)

## Prerequisites

### Required Software

1. **Bruno** (v1.0.0 or later)
   - Download from [usebruno.com](https://www.usebruno.com/)
   - Available for Windows, macOS, and Linux
   - GUI application (recommended for this test suite)

2. **SIF Unity Broker** (deployed and running)
   - Version 1.0.0 or later
   - All services must be accessible
   - Database and Redis must be configured

### Optional Software

- **Node.js** (v18+ for CLI usage)
- **Git** (for version control)
- **Docker** (if using Docker Compose deployment)
- **kubectl** (if using Kubernetes deployment)

## Installation

### Option 1: From Repository

```bash
# Clone the repository
git clone https://github.com/your-org/sif-unity-broker.git
cd sif-unity-broker/bruno-tests
```

### Option 2: Standalone Package

```bash
# Download and extract
unzip bruno-tests.zip
cd bruno-tests
```

### Option 3: Copy from Project

```bash
# If you have the full SIF Unity Broker project
cp -r /path/to/sif-unity-broker/bruno-tests ./
cd bruno-tests
```

## Configuration

### Step 1: Create Environment File

Copy the example environment file:

```bash
cp .env.example .env
```

### Step 2: Configure Credentials

Edit `.env` and set your credentials:

```env
# Environment Selection
SELECTED_ENVIRONMENT=local

# Local Development Credentials
LOCAL_SESSION_TOKEN=your-session-token-here
LOCAL_SHARED_SECRET=your-shared-secret-here
```

**Important Security Notes**:
- ⚠️ Never commit `.env` to version control
- ⚠️ Keep credentials secure
- ⚠️ Use different credentials for each environment
- ⚠️ Rotate credentials regularly

### Step 3: Open Collection in Bruno

1. Launch Bruno application
2. Click "Open Collection" (or File → Open Collection)
3. Navigate to the `bruno-tests` folder
4. Click "Select Folder"

Bruno will load the collection and display all test folders.

### Step 4: Select Environment

In Bruno:
1. Look for the environment dropdown (top-right corner)
2. Click the dropdown
3. Select your environment: `local`, `docker-compose`, or `kubernetes`

The selected environment determines which service URLs are used.

## Environment-Specific Setup

### Local Development

For local development with services running on localhost:

**1. Configure .env:**

```env
SELECTED_ENVIRONMENT=local
LOCAL_SESSION_TOKEN=admin-bootstrap-token
LOCAL_SHARED_SECRET=admin-bootstrap-secret
```

**2. Verify Services:**

```bash
# Check all services are running
curl http://localhost:8080/actuator/health  # Gateway
curl http://localhost:8081/actuator/health  # Environment Service
curl http://localhost:8082/actuator/health  # Queue Service
# ... etc
```

**3. Select Environment in Bruno:**
- Select `local` from environment dropdown

**4. Run Health Checks:**
- Navigate to `01-health-checks`
- Click "Run Collection"
- Verify all tests pass

### Docker Compose

For Docker Compose deployments:

**1. Start Broker:**

```bash
cd /path/to/sif-unity-broker
docker-compose up -d
```

**2. Wait for Services:**

```bash
# Wait for all services to be healthy
docker-compose ps
```

**3. Configure .env:**

```env
SELECTED_ENVIRONMENT=docker
DOCKER_SESSION_TOKEN=admin-bootstrap-token
DOCKER_SHARED_SECRET=admin-bootstrap-secret
```

**4. Select Environment in Bruno:**
- Select `docker-compose` from environment dropdown

**5. Run Health Checks:**
- Navigate to `01-health-checks`
- Click "Run Collection"
- Verify all tests pass

### Kubernetes

For Kubernetes deployments:

**1. Deploy Broker:**

```bash
kubectl apply -f k8s/
```

**2. Wait for Pods:**

```bash
kubectl get pods -n sif-unity
kubectl wait --for=condition=ready pod -l app=gateway -n sif-unity
```

**3. Port Forward (if needed):**

```bash
# Forward Gateway port
kubectl port-forward -n sif-unity svc/gateway 8080:80

# Or use Ingress/LoadBalancer URLs
```

**4. Configure .env:**

```env
SELECTED_ENVIRONMENT=kubernetes
K8S_SESSION_TOKEN=admin-bootstrap-token
K8S_SHARED_SECRET=admin-bootstrap-secret
```

**5. Update Environment URLs (if needed):**

Edit `environments/kubernetes.bru` if using Ingress or LoadBalancer:

```
vars {
  gatewayUrl: https://sif-broker.example.com
  # ... update other URLs
}
```

**6. Select Environment in Bruno:**
- Select `kubernetes` from environment dropdown

**7. Run Health Checks:**
- Navigate to `01-health-checks`
- Click "Run Collection"
- Verify all tests pass

## Credential Management

### Bootstrap ADMIN Credentials

The SIF Unity Broker typically includes a bootstrap ADMIN environment with pre-configured credentials:

- **Session Token**: `admin-bootstrap-token`
- **Shared Secret**: `admin-bootstrap-secret`

These are configured in the broker's database initialization scripts.

### Creating Test Credentials

If you need to create a new test environment:

**1. Create Environment (No Auth Required):**

```bash
curl -X POST http://localhost:8080/environments \
  -H "Content-Type: application/json" \
  -d '{
    "applicationName": "bruno-test-env",
    "type": "CONSUMER",
    "sessionToken": "test-token-123",
    "sharedSecret": "test-secret-456"
  }'
```

**2. Update .env:**

```env
LOCAL_SESSION_TOKEN=test-token-123
LOCAL_SHARED_SECRET=test-secret-456
```

**3. Create ACL Entries:**

The test environment needs ACL entries to access services. Use the infrastructure tests to create ACLs.

### Multiple Credential Sets

You can configure different credentials for each environment:

```env
# Local Development
LOCAL_SESSION_TOKEN=local-token
LOCAL_SHARED_SECRET=local-secret

# Docker Compose
DOCKER_SESSION_TOKEN=docker-token
DOCKER_SHARED_SECRET=docker-secret

# Kubernetes
K8S_SESSION_TOKEN=k8s-token
K8S_SHARED_SECRET=k8s-secret
```

Switch between them by changing `SELECTED_ENVIRONMENT`.

### Credential Rotation

To rotate credentials:

1. Create a new environment with new credentials
2. Update `.env` with new credentials
3. Create ACL entries for the new environment
4. Test with new credentials
5. Delete old environment

## Verification

### Step 1: Verify Bruno Installation

```bash
# Check Bruno is installed
bruno --version  # If using CLI

# Or open Bruno GUI and check Help → About
```

### Step 2: Verify Collection Loaded

In Bruno:
- You should see folders: `01-health-checks`, `02-infrastructure`, etc.
- Click on any `.bru` file to view the request

### Step 3: Verify Environment Selected

In Bruno:
- Check environment dropdown shows your selected environment
- Verify environment variables are loaded (click environment name to view)

### Step 4: Verify Credentials

Run a simple authenticated request:

1. Navigate to `02-infrastructure/environments`
2. Open `GET List Environments - Success.bru`
3. Click "Send"
4. Verify response is 200 OK (not 401)

### Step 5: Run Health Checks

1. Navigate to `01-health-checks`
2. Click "Run Collection"
3. Verify all 9 services return 200 OK

If all health checks pass, your setup is complete!

## Troubleshooting Setup

### Bruno Won't Open Collection

**Problem**: Bruno shows error when opening collection

**Solutions**:
- Verify `bruno.json` exists in the folder
- Check folder permissions
- Try opening a parent folder and navigating to `bruno-tests`

### Environment Variables Not Loading

**Problem**: Variables show as `{{variableName}}` in requests

**Solutions**:
- Verify environment is selected in dropdown
- Check `.env` file exists and has correct format
- Restart Bruno after editing `.env`
- Verify `SELECTED_ENVIRONMENT` matches environment name

### Authentication Fails

**Problem**: All requests return 401 Unauthorized

**Solutions**:
- Verify credentials in `.env` are correct
- Check session token exists in broker database
- Verify `SELECTED_ENVIRONMENT` is set correctly
- Check timestamp tolerance (must be within 5 minutes)

### Services Not Accessible

**Problem**: Health checks fail with connection errors

**Solutions**:
- Verify broker is running: `docker ps` or `kubectl get pods`
- Check service URLs in environment configuration
- Verify network connectivity
- Check firewall rules
- Try accessing services directly: `curl http://localhost:8080/actuator/health`

### Port Conflicts

**Problem**: Services running on different ports

**Solutions**:
- Update environment configuration files (`environments/*.bru`)
- Change service URLs to match your deployment
- Verify port mappings in Docker Compose or Kubernetes

## Next Steps

After successful setup:

1. ✅ Run health checks (`01-health-checks`)
2. ✅ Run infrastructure tests (`02-infrastructure`)
3. ✅ Run authentication tests (`03-authentication`)
4. ✅ Run authorization tests (`04-authorization`)
5. ✅ Run message pattern tests (`05-message-patterns`)
6. ✅ Run error handling tests (`06-error-handling`)
7. ✅ Run cleanup (`99-cleanup`)

See [README.md](README.md) for detailed usage instructions.

## Additional Resources

- [Bruno Documentation](https://docs.usebruno.com/)
- [SIF Specification](https://specification.sifassociation.org/)
- [Troubleshooting Guide](TROUBLESHOOTING.md)
- [Contributing Guide](CONTRIBUTING.md)
