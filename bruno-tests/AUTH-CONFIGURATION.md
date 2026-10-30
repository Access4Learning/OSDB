# Authentication Configuration Guide

## Overview

All Bruno tests use a dynamic credential resolution pattern based on environment prefix and role. This allows for flexible configuration across multiple environments and supports both single-credential and dual-credential (consumer/provider) scenarios.

## How It Works

### 1. Environment Configuration

Each Bruno environment file (`.bru`) defines a `currentEnv` variable that serves as the prefix for credential lookup:

```
vars {
  currentEnv: local
  gatewayUrl: http://localhost:8080
  ...
}
```

### 2. Credential Naming Pattern

All credentials in `.env` follow this pattern:

```
{prefix}-{role}SessionToken
{prefix}-{role}SharedSecret
```

Where:
- `prefix` = value of `currentEnv` from the environment file (e.g., `local`, `docker`, `kubernetes`, `standalone`)
- `role` = either `consumer` or `provider`

### 3. Dynamic Resolution

The `auth.js` script:
1. Reads `currentEnv` from the Bruno environment
2. Receives the `role` parameter (defaults to `consumer`)
3. Constructs the variable name: `{currentEnv}-{role}SessionToken`
4. Uses `bru.interpolate()` to resolve the value from `.env`

## Environment Examples

### Local Development (Single Credential)

**Environment file** (`local.bru`):
```
vars {
  currentEnv: local
  gatewayUrl: http://localhost:8080
}
```

**`.env` file**:
```
local-consumerSessionToken=your-token-here
local-consumerSharedSecret=your-secret-here
```

**Test usage**:
```javascript
const auth = require('./common/auth.js');
auth.setAuthHeaders(req, bru); // Uses 'consumer' role by default
```

### Standalone (Dual Credentials)

**Environment file** (`standalone.bru`):
```
vars {
  currentEnv: standalone
  gatewayUrl: https://my-broker.example.com/svcs/uat
  standaloneConsumerEnvironmentId: c8ee9729-b130-480b-9e7d-ab7c9ae865fe
  standaloneProviderEnvironmentId: 19ccb1b9-6bef-11f0-b74f-0294b414b631
}
```

**`.env` file**:
```
standalone-consumerSessionToken=your-consumer-session-token-here
standalone-consumerSharedSecret=your-consumer-shared-secret-here
standalone-providerSessionToken=your-provider-session-token-here
standalone-providerSharedSecret=your-provider-shared-secret-here
```

**Test usage**:
```javascript
const auth = require('./common/auth.js');

// For consumer tests
auth.setAuthHeaders(req, bru, 'consumer');

// For provider tests
auth.setAuthHeaders(req, bru, 'provider');
```

## Adding New Environments

To add a new environment (e.g., `my-prod`):

1. **Create environment file** (`bruno-tests/environments/my-prod.bru`):
```
vars {
  currentEnv: my-prod
  gatewayUrl: https://my-prod.example.com
  ...
}
```

2. **Add credentials to `.env`**:
```
my-prod-consumerSessionToken=your-token
my-prod-consumerSharedSecret=your-secret
```

3. **For dual-credential environments, add both roles**:
```
my-prod-consumerSessionToken=consumer-token
my-prod-consumerSharedSecret=consumer-secret
my-prod-providerSessionToken=provider-token
my-prod-providerSharedSecret=provider-secret
```

## Test Types

### Infrastructure Tests (02-infrastructure)

These tests create/manage broker infrastructure (environments, ACLs, queues, etc.) and use a single credential set:

```javascript
auth.setAuthHeaders(req, bru); // Defaults to 'consumer' role
```

**Required .env entries**:
- `{prefix}-consumerSessionToken`
- `{prefix}-consumerSharedSecret`

### Standalone Tests (03-inplace-solution)

These tests interact with an existing SIF solution and require separate consumer and provider credentials:

```javascript
// Consumer tests
auth.setAuthHeaders(req, bru, 'consumer');

// Provider tests
auth.setAuthHeaders(req, bru, 'provider');
```

**Required .env entries**:
- `{prefix}-consumerSessionToken`
- `{prefix}-consumerSharedSecret`
- `{prefix}-providerSessionToken`
- `{prefix}-providerSharedSecret`

## Troubleshooting

### Error: "currentEnv not set in environment configuration"

**Cause**: The Bruno environment file doesn't have a `currentEnv` variable.

**Solution**: Add `currentEnv: {your-prefix}` to the `vars` block in your environment file.

### Error: "Missing credentials for environment: X, role: Y"

**Cause**: The `.env` file doesn't have the required credential entries.

**Solution**: Add the following to your `.env` file:
```
{prefix}-{role}SessionToken=your-token
{prefix}-{role}SharedSecret=your-secret
```

### Credentials not resolving

**Cause**: Variable name mismatch between environment file and `.env`.

**Solution**: Ensure the `currentEnv` value in your environment file matches the prefix in your `.env` entries.

## Security Notes

- Never commit the `.env` file to version control
- Use `.env.example` as a template
- Rotate credentials regularly
- Use different credentials for each environment
- Keep production credentials separate from development credentials
