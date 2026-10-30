# SIF Unity Broker - API Authentication Guide

## Overview

The SIF Unity Broker uses HMAC-SHA256 authentication for secure API access. Each request must include:
1. **Authorization header**: `SIF_HMACSHA256 Base64(sessionToken:hash)`
2. **timestamp header**: ISO 8601 timestamp
3. **HMAC signature**: Calculated as HMAC-SHA256(sessionToken:timestamp, sharedSecret)

## Bootstrap ADMIN Credentials

From your PostgreSQL database:
- **Session Token**: `admin-bootstrap-token`
- **Shared Secret**: `admin-bootstrap-secret`

## Authentication Format

### Authorization Header Format
```
Authorization: SIF_HMACSHA256 Base64(sessionToken:hash)
timestamp: 2026-01-21T06:30:00.000Z
```

Where:
- `sessionToken` is your environment's session token
- `hash` is the Base64-encoded HMAC-SHA256 signature
- The hash is computed as: `HMAC-SHA256(sessionToken:timestamp, sharedSecret)`

### HMAC Signature Calculation

The signature is calculated as:
```
HMAC-SHA256(sharedSecret, message)
```

Where `message` is:
```
METHOD\n
PATH\n
TIMESTAMP\n
BODY (if present)
```

**Example message for GET request:**
```
GET
/environments
2026-01-21T06:30:00.000Z
```

**Example message for POST request:**
```
POST
/environments
2026-01-21T06:30:00.000Z
{"applicationName":"TestApp","type":"DIRECT"}
```

## Simple curl Examples (Without HMAC - for testing)

### 1. List All Environments
```bash
# Direct to Environment Service
curl -X GET "http://localhost:8081/environments" \
  -H "Authorization: SIF_HMACSHA256 admin-bootstrap-token:dummy-signature"

# Via Gateway
curl -X GET "http://localhost:8080/environments" \
  -H "Authorization: SIF_HMACSHA256 admin-bootstrap-token:dummy-signature"
```

### 2. Filter ADMIN Environments
```bash
curl -X GET "http://localhost:8081/environments?type=ADMIN" \
  -H "Authorization: SIF_HMACSHA256 admin-bootstrap-token:dummy-signature"
```

### 3. Get Specific Environment by ID
```bash
# First get the ID from list command, then:
curl -X GET "http://localhost:8081/environments/{environment-id}" \
  -H "Authorization: SIF_HMACSHA256 admin-bootstrap-token:dummy-signature"
```

### 4. Create New Environment (No Auth Required)
```bash
# POST /environments does not require authentication (per AuthorizationFilter)
curl -X POST "http://localhost:8081/environments" \
  -H "Content-Type: application/json" \
  -d '{
    "applicationName": "MyTestApp",
    "type": "DIRECT",
    "sessionToken": "my-session-token",
    "sharedSecret": "my-shared-secret",
    "defaultZoneId": "default",
    "defaultContextId": "default"
  }'
```

## PowerShell Script with Proper HMAC

For proper HMAC authentication, use the provided PowerShell script:

```powershell
.\test-api-authentication.ps1
```

This script:
- ✅ Calculates proper HMAC-SHA256 signatures
- ✅ Includes correct timestamp headers
- ✅ Tests multiple endpoints
- ✅ Shows request/response details
- ✅ Demonstrates creating new environments

## Python Example with Proper HMAC

```python
import hmac
import hashlib
import base64
import requests
from datetime import datetime

def make_authenticated_request(method, url, session_token, shared_secret, body=None):
    # Create timestamp
    timestamp = datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%S.%f')[:-3] + 'Z'
    
    # Create message to sign (session token + timestamp)
    message = f"{session_token}:{timestamp}"
    
    # Calculate HMAC signature
    signature = base64.b64encode(
        hmac.new(
            shared_secret.encode('utf-8'),
            message.encode('utf-8'),
            hashlib.sha256
        ).digest()
    ).decode('utf-8')
    
    # Create headers
    headers = {
        'Authorization': f'SIF_HMACSHA256 {session_token}:{signature}',
        'timestamp': timestamp,
        'Content-Type': 'application/json'
    }
    
    # Make request
    if body:
        response = requests.request(method, url, headers=headers, data=body)
    else:
        response = requests.request(method, url, headers=headers)
    
    return response

# Example usage
session_token = "admin-bootstrap-token"
shared_secret = "admin-bootstrap-secret"

# List environments
response = make_authenticated_request(
    'GET',
    'http://localhost:8081/environments',
    session_token,
    shared_secret
)

print(response.json())
```

## Testing Without HMAC Validation

**Note**: Based on the `AuthorizationFilter` code, it appears the filter extracts the session token but may not be validating the HMAC signature. This means you might be able to make requests with just:

```bash
curl -X GET "http://localhost:8081/environments" \
  -H "Authorization: SIF_HMACSHA256 admin-bootstrap-token:any-signature-here"
```

Try this first! If it works, the HMAC validation might not be fully implemented yet.

## Common Endpoints

### Environment Service (Port 8081)

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/environments` | List all environments | Yes |
| GET | `/environments/{id}` | Get specific environment | Yes |
| POST | `/environments` | Create new environment | **No** |
| PUT | `/environments/{id}` | Update environment | Yes |
| DELETE | `/environments/{id}` | Delete environment | Yes |
| GET | `/environments?type=ADMIN` | Filter by type | Yes |

### Via Gateway (Port 8080)

All environment service endpoints are accessible via the gateway at the same paths:
- `http://localhost:8080/environments`
- `http://localhost:8080/environments/{id}`
- etc.

## Health Checks (No Auth Required)

```bash
# Check all services
curl http://localhost:8080/actuator/health  # Gateway
curl http://localhost:8081/actuator/health  # Environment Service
curl http://localhost:8082/actuator/health  # Queue Service
curl http://localhost:8083/actuator/health  # Subscription Service
curl http://localhost:8084/actuator/health  # Job Service
curl http://localhost:8085/actuator/health  # Requests Connector
curl http://localhost:8086/actuator/health  # Responses Connector
curl http://localhost:8087/actuator/health  # Events Connector
curl http://localhost:8088/actuator/health  # Services Connector
```

## Quick Start

1. **Test basic connectivity:**
   ```bash
   curl http://localhost:8081/actuator/health
   ```

2. **List environments (try without proper HMAC first):**
   ```bash
   curl -X GET "http://localhost:8081/environments" \
     -H "Authorization: SIF_HMACSHA256 admin-bootstrap-token:test"
   ```

3. **If that doesn't work, use the PowerShell script:**
   ```powershell
   .\test-api-authentication.ps1
   ```

4. **Create a test environment (no auth needed):**
   ```bash
   curl -X POST "http://localhost:8081/environments" \
     -H "Content-Type: application/json" \
     -d '{
       "applicationName": "TestApp",
       "type": "DIRECT",
       "sessionToken": "test-token-123",
       "sharedSecret": "test-secret-456"
     }'
   ```

## Troubleshooting

### 401 Unauthorized
- Check that session token is correct: `admin-bootstrap-token`
- Verify the Authorization header format
- Check timestamp is recent (within tolerance window)

### 403 Forbidden
- The session token is valid but lacks permissions
- ADMIN environments should have full permissions
- Check the operation is allowed for your environment type

### 404 Not Found
- Verify the service is running on the correct port
- Check the endpoint path is correct
- Ensure the resource ID exists

## Next Steps

After successfully authenticating:
1. Create additional environments for testing
2. Register providers
3. Create subscriptions
4. Test request/response flows
5. Monitor via actuator endpoints
