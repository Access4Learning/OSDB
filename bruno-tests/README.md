# SIF Unity Broker - Bruno API Test Suite

Comprehensive API test suite for the SIF Unity Broker using [Bruno](https://www.usebruno.com/), an open-source API testing tool.

## Overview

This standalone test suite provides complete coverage of all externally-facing APIs in the SIF Unity Broker. It's designed to be distributed to the open source community for testing deployed broker instances.

### What's Included

- ✅ Health checks for all 9 services
- ✅ Infrastructure API tests (environments, ACLs, queues, subscriptions, etc.)
- ✅ Authentication and authorization tests
- ✅ Message pattern tests (IMMEDIATE, DELAYED, EVENT)
- ✅ Error handling tests
- ✅ Cleanup utilities

### Key Features

- **Standalone**: No dependencies on other project modules
- **Secure**: Credentials stored in `.env` file (excluded from Git)
- **Well-Documented**: Comprehensive documentation at folder and request levels
- **Best Practices**: Follows Bruno and SIF 3.7 best practices
- **Multiple Environments**: Support for local, Docker Compose, and Kubernetes deployments

## Prerequisites

### Required

1. **Bruno** - Download from [usebruno.com](https://www.usebruno.com/)
   - GUI application (recommended)
   - CLI available but optional

2. **Running SIF Unity Broker** - The test suite assumes the broker is already deployed
   - Local development setup
   - Docker Compose deployment
   - Kubernetes deployment

3. **Valid Credentials** - Session token and shared secret for authentication
   - Bootstrap ADMIN credentials (typically pre-configured)
   - Or create a test environment first

### Optional

- Node.js (for CLI usage)
- Git (for version control)

## Quick Start

### 1. Clone or Download

```bash
# If part of the SIF Unity Broker repository
cd bruno-tests

# Or download as standalone package
unzip bruno-tests.zip
cd bruno-tests
```

### 2. Configure Credentials

Copy the example environment file and add your credentials:

```bash
cp .env.example .env
```

Edit `.env` and set your credentials:

```env
# Select which environment to use
SELECTED_ENVIRONMENT=local

# Local development credentials
LOCAL_SESSION_TOKEN=your-session-token-here
LOCAL_SHARED_SECRET=your-shared-secret-here
```

**Important**: Never commit `.env` to version control!

### 3. Open in Bruno

1. Launch Bruno
2. Click "Open Collection"
3. Navigate to the `bruno-tests` folder
4. Select the folder

### 4. Select Environment

In Bruno:
1. Click the environment dropdown (top right)
2. Select your environment: `local`, `docker-compose`, or `kubernetes`

### 5. Run Tests

Start with health checks:
1. Navigate to `01-health-checks`
2. Click "Run Collection" or run individual tests
3. Verify all services are healthy

Then proceed with other test collections in order.

## Project Structure

```
bruno-tests/
├── .env.example              # Example credentials file
├── .gitignore               # Excludes .env and sensitive files
├── bruno.json               # Bruno collection configuration
├── VERSION                  # Compatible broker version
├── README.md                # This file
├── SETUP.md                 # Detailed setup instructions
├── TROUBLESHOOTING.md       # Common issues and solutions
├── CONTRIBUTING.md          # Guidelines for adding tests
├── CHANGELOG.md             # Version history
│
├── environments/            # Environment configurations
│   ├── local.bru           # Local development
│   ├── docker-compose.bru  # Docker Compose
│   └── kubernetes.bru      # Kubernetes
│
├── common/                  # Shared scripts and utilities
│   ├── auth.bru            # HMAC authentication
│   ├── helpers.bru         # Utility functions
│   └── cleanup.bru         # Cleanup utilities
│
├── 01-health-checks/        # Service health checks
│   ├── folder.bru          # Folder documentation
│   ├── gateway-health.bru
│   ├── environment-service-health.bru
│   └── ... (9 services total)
│
├── 02-infrastructure/       # Infrastructure API tests
│   ├── folder.bru          # Folder documentation
│   ├── environments/       # Environment management
│   ├── acls/              # ACL management
│   ├── provider-registry/ # Provider registration
│   ├── zones-contexts/    # Zone and context management
│   ├── queues/            # Queue operations
│   ├── subscriptions/     # Subscription management
│   └── jobs/              # Job state management
│
├── 03-inplace-solution/     # Standalone solution tests
│   ├── folder.bru          # Folder documentation
│   ├── 00-initialize/     # Initialize environments
│   ├── consumer/          # Consumer tests
│   └── provider/          # Provider tests
│
├── 04-authentication/       # Authentication tests
│   ├── folder.bru          # Folder documentation
│   └── ... (auth test cases)
│
├── 05-message-patterns/     # Message pattern tests
│   ├── folder.bru          # Folder documentation
│   ├── immediate/         # IMMEDIATE pattern
│   ├── delayed/           # DELAYED pattern
│   └── event/             # EVENT pattern
│
├── 06-error-handling/       # Error scenario tests
│   ├── folder.bru          # Folder documentation
│   └── ... (error test cases)
│
└── 99-cleanup/              # Cleanup utilities
    ├── folder.bru          # Folder documentation
    └── ... (cleanup scripts)
```

## Test Collections

### 01-health-checks

Verifies all services are running and healthy. Run these first!

- No authentication required
- Tests all 9 services
- Should complete in < 10 seconds

### 02-infrastructure

Tests all infrastructure APIs:
- Environments (create, list, get, update, delete)
- ACLs (access control management)
- Provider Registry (provider registration and discovery)
- Zones and Contexts (multi-tenancy)
- Queues (message queue operations)
- Subscriptions (event subscriptions)
- Jobs (job state management)

### 03-authentication

Tests authentication scenarios:
- Valid authentication
- Invalid session tokens
- Expired timestamps
- Invalid HMAC signatures
- Malformed headers

### 04-authorization

Tests authorization scenarios:
- APPROVED ACL entries
- Missing ACL entries (403)
- DENIED ACL entries
- Zone/context access control

### 05-message-patterns

Tests all three SIF message exchange patterns:
- **IMMEDIATE**: Synchronous request/response
- **DELAYED**: Asynchronous via queues
- **EVENT**: Publish/subscribe

### 06-error-handling

Tests error scenarios:
- 400 Bad Request (validation errors)
- 404 Not Found
- 409 Conflict
- 503 Service Unavailable

### 99-cleanup

Utilities to clean up test data:
- Delete test environments
- Delete test queues
- Delete test subscriptions
- Delete test ACLs

## Running Tests

### Via Bruno GUI (Recommended)

1. **Run Individual Test**:
   - Click on a test request
   - Click "Send" button
   - Review response and assertions

2. **Run Collection**:
   - Right-click on a folder
   - Select "Run Collection"
   - All tests in folder execute sequentially

3. **Run All Tests**:
   - Right-click on root folder
   - Select "Run Collection"
   - All tests execute in order

### Via Bruno CLI (Optional)

```bash
# Install Bruno CLI
npm install -g @usebruno/cli

# Run all tests
bru run --env local

# Run specific collection
bru run 01-health-checks --env local

# Run with output
bru run --env local --output results.json
```

## Authentication

All authenticated requests use SIF HMAC-SHA256 authentication:

1. **Session Token**: Identifies the environment
2. **Shared Secret**: Used to compute HMAC signature
3. **Timestamp**: ISO 8601 timestamp (prevents replay attacks)
4. **HMAC Signature**: Computed from SESSION_TOKEN + TIMESTAMP

The `common/auth.bru` script handles this automatically for all authenticated requests.

### Signature Format

```
Authorization: SIF_HMACSHA256 {sessionToken}:{signature}
timestamp: 2024-01-15T10:30:00.000Z
```

### Signature Message

```
METHOD
PATH
TIMESTAMP
BODY (if present)
```

## Environment Configuration

### Switching Environments

Update `.env`:

```env
SELECTED_ENVIRONMENT=docker  # or local, kubernetes
```

Then select the corresponding environment in Bruno.

### Environment Variables

Each environment file (`environments/*.bru`) defines:
- Service URLs (gateway, environment-service, etc.)
- Default zone and context IDs
- Test data prefix
- Timeout settings

### Multiple Credential Sets

Store different credentials for each environment:

```env
# Local
LOCAL_SESSION_TOKEN=token1
LOCAL_SHARED_SECRET=secret1

# Docker
DOCKER_SESSION_TOKEN=token2
DOCKER_SHARED_SECRET=secret2

# Kubernetes
K8S_SESSION_TOKEN=token3
K8S_SHARED_SECRET=secret3
```

## Test Data Management

### Test Data Prefix

All test resources are created with the `bruno-test` prefix:
- `bruno-test-consumer-abc123`
- `bruno-test-queue-def456`

This makes it easy to identify and clean up test data.

### Cleanup

Run the `99-cleanup` collection to remove all test resources:
1. Navigate to `99-cleanup`
2. Run collection
3. All test resources are deleted

### Isolation

Each test:
- Creates its own test data
- Uses unique names (with UUIDs)
- Cleans up after itself (where possible)

## Troubleshooting

### Connection Refused

**Problem**: Cannot connect to broker services

**Solutions**:
1. Verify broker is running: `docker ps` or `kubectl get pods`
2. Check service URLs in environment configuration
3. Verify network connectivity
4. Check firewall rules

### Authentication Failures (401)

**Problem**: All requests return 401 Unauthorized

**Solutions**:
1. Verify credentials in `.env` are correct
2. Check `SELECTED_ENVIRONMENT` matches your setup
3. Verify session token exists in broker database
4. Check timestamp is within tolerance (5 minutes)

### Authorization Failures (403)

**Problem**: Authenticated requests return 403 Forbidden

**Solutions**:
1. Verify ACL entries exist for your environment
2. Check ACL entries have `APPROVED` status
3. Verify zone and context match ACL entries
4. Check access type (PROVIDE, CONSUME, SUBSCRIBE)

### Test Failures

**Problem**: Tests fail with unexpected responses

**Solutions**:
1. Run health checks first
2. Check broker logs for errors
3. Verify test prerequisites are met
4. Review test documentation
5. Check for stale test data

See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for more details.

## Best Practices

### Before Running Tests

1. ✅ Run health checks first
2. ✅ Verify credentials are configured
3. ✅ Check broker is fully deployed
4. ✅ Review test documentation

### During Testing

1. ✅ Run tests in order (01, 02, 03, etc.)
2. ✅ Review test results and assertions
3. ✅ Check response times
4. ✅ Monitor broker logs

### After Testing

1. ✅ Run cleanup collection
2. ✅ Review any failures
3. ✅ Document issues found
4. ✅ Update test data if needed

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on:
- Adding new tests
- Test naming conventions
- Documentation requirements
- Code style

## Version Compatibility

- **Test Suite Version**: 1.0.0
- **Compatible Broker Version**: 1.0.0+
- **SIF Specification**: 3.7

See [VERSION](VERSION) file for details.

## Support

### Documentation

- [SETUP.md](SETUP.md) - Detailed setup instructions
- [TROUBLESHOOTING.md](TROUBLESHOOTING.md) - Common issues
- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution guidelines
- [CHANGELOG.md](CHANGELOG.md) - Version history

### Resources

- [Bruno Documentation](https://docs.usebruno.com/)
- [SIF Specification](https://specification.sifassociation.org/)
- [SIF Unity Broker Repository](https://github.com/your-org/sif-unity-broker)

### Getting Help

1. Check documentation in this folder
2. Review test-level documentation (docs in each .bru file)
3. Check broker logs for errors
4. Open an issue in the repository

## License

This project is licensed under the MIT License. See [LICENSE](../LICENSE) for details.

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history and changes.
