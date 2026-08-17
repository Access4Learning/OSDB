# Requests Connector API Documentation

## Overview

The Requests Connector provides REST APIs for routing consumer requests to providers using IMMEDIATE (synchronous) and DELAYED (asynchronous) message exchange patterns.

## Base URL

```
http://localhost:8080/api/v1/requests
```

## Authentication

All requests must be authenticated by the API Gateway before reaching the Requests Connector. The Gateway injects environment context headers that the connector uses for routing.

## Common Headers

All endpoints require the following headers:

| Header | Type | Required | Description |
|--------|------|----------|-------------|
| `X-Environment-Id` | UUID | Yes | Environment identifier (injected by Gateway) |
| `X-Environment-Name` | String | Yes | Environment name (injected by Gateway) |
| `zoneId` | String | Yes | Zone identifier for routing |
| `contextId` | String | No | Context identifier for routing (optional) |

## Endpoints

### 1. IMMEDIATE Request

Send a synchronous request to a provider and receive an immediate response.

**Endpoint**: `POST /immediate`

**Headers**:
- `X-Environment-Id`: UUID (required)
- `X-Environment-Name`: String (required)
- `zoneId`: String (required)
- `contextId`: String (optional)
- `Content-Type`: application/json

**Request Body**:
```json
{
  "serviceName": "StudentPersonal",
  "serviceType": "OBJECT",
  "messageType": "REQUEST",
  "payload": {
    "refId": "12345",
    "action": "query"
  }
}
```

**Request Body Schema**:
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `serviceName` | String | Yes | Name of the service (must match pattern: ^[a-zA-Z][a-zA-Z0-9_-]*$) |
| `serviceType` | String | Yes | Type of service (e.g., OBJECT, FUNCTION) |
| `messageType` | String | Yes | Type of message (e.g., REQUEST, RESPONSE) |
| `payload` | Object | No | Request payload (service-specific) |

**Success Response** (200 OK):
```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2024-01-15T10:30:00Z",
  "payload": {
    "refId": "12345",
    "data": {
      "studentId": "S001",
      "name": "John Doe"
    }
  }
}
```

**Response Schema**:
| Field | Type | Description |
|-------|------|-------------|
| `messageId` | UUID | Unique message identifier |
| `timestamp` | ISO 8601 DateTime | Response timestamp |
| `payload` | Object | Response payload from provider |

**Error Responses**:

- **400 Bad Request** - Invalid request parameters
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed: serviceName: must not be blank",
  "status": 400,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

- **404 Not Found** - No provider registered
```json
{
  "code": "NO_PROVIDER",
  "message": "No provider registered for StudentPersonal in zone DEFAULT context null",
  "status": 404,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

- **503 Service Unavailable** - Provider or core service unavailable
```json
{
  "code": "SERVICE_UNAVAILABLE",
  "message": "A required service is currently unavailable",
  "status": 503,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

- **504 Gateway Timeout** - Provider timeout
```json
{
  "code": "PROVIDER_TIMEOUT",
  "message": "Provider call timed out after 30000ms",
  "status": 504,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**Example cURL**:
```bash
curl -X POST http://localhost:8080/api/v1/requests/immediate \
  -H "Content-Type: application/json" \
  -H "X-Environment-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -H "X-Environment-Name: Production" \
  -H "zoneId: DEFAULT" \
  -H "contextId: SCHOOL_DISTRICT_A" \
  -d '{
    "serviceName": "StudentPersonal",
    "serviceType": "OBJECT",
    "messageType": "REQUEST",
    "payload": {
      "refId": "12345",
      "action": "query"
    }
  }'
```

---

### 2. DELAYED Request

Send an asynchronous request to a provider. The response will be delivered to the consumer's queue.

**Endpoint**: `POST /delayed`

**Headers**:
- `X-Environment-Id`: UUID (required)
- `X-Environment-Name`: String (required)
- `zoneId`: String (required)
- `contextId`: String (optional)
- `queueId`: UUID (required) - Consumer's queue for receiving responses
- `Content-Type`: application/json

**Request Body**:
```json
{
  "serviceName": "StudentPersonal",
  "serviceType": "OBJECT",
  "messageType": "REQUEST",
  "payload": {
    "refId": "12345",
    "action": "query"
  }
}
```

**Request Body Schema**: Same as IMMEDIATE request

**Success Response** (202 Accepted):
```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "queueId": "660e8400-e29b-41d4-a716-446655440001",
  "status": "ACCEPTED",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**Response Schema**:
| Field | Type | Description |
|-------|------|-------------|
| `messageId` | UUID | Unique message identifier |
| `queueId` | UUID | Consumer's queue identifier |
| `status` | String | Request status (always "ACCEPTED") |
| `timestamp` | ISO 8601 DateTime | Acceptance timestamp |

**Error Responses**:

- **400 Bad Request** - Invalid request parameters or missing queueId
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed: serviceName: must not be blank",
  "status": 400,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

- **404 Not Found** - No provider registered or no provider subscription
```json
{
  "code": "NO_SUBSCRIPTION",
  "message": "Provider has no request subscription",
  "status": 404,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

- **503 Service Unavailable** - Core service unavailable
```json
{
  "code": "SERVICE_UNAVAILABLE",
  "message": "A required service is currently unavailable",
  "status": 503,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**Example cURL**:
```bash
curl -X POST http://localhost:8080/api/v1/requests/delayed \
  -H "Content-Type: application/json" \
  -H "X-Environment-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -H "X-Environment-Name: Production" \
  -H "zoneId: DEFAULT" \
  -H "contextId: SCHOOL_DISTRICT_A" \
  -H "queueId: 660e8400-e29b-41d4-a716-446655440001" \
  -d '{
    "serviceName": "StudentPersonal",
    "serviceType": "OBJECT",
    "messageType": "REQUEST",
    "payload": {
      "refId": "12345",
      "action": "query"
    }
  }'
```

---

## Request Flow Patterns

### IMMEDIATE Pattern Flow

1. Consumer → Gateway (authenticated request)
2. Gateway → Requests Connector (with environment context headers)
3. Requests Connector queries provider registry (cached)
4. Requests Connector forwards request to Provider endpoint
5. Provider processes and returns response synchronously
6. Requests Connector returns response to Consumer via Gateway

**Timing**: Response returned in same HTTP connection (typically < 1 second)

### DELAYED to DELAYED Pattern Flow

1. Consumer → Gateway (authenticated request with queueId)
2. Gateway → Requests Connector (with environment context headers)
3. Requests Connector queries provider registry (cached)
4. Requests Connector detects provider supports DELAYED
5. Requests Connector queries Subscription Service for provider's request subscription
6. Requests Connector enqueues request to provider's queue via Queue Service
7. Requests Connector returns HTTP 202 Accepted to Consumer
8. Provider polls their queue and processes request asynchronously
9. Provider sends response to Responses Connector
10. Responses Connector enqueues response to Consumer's queue

**Timing**: HTTP 202 returned immediately (< 200ms), actual response delivered asynchronously

### DELAYED to IMMEDIATE Pattern Flow

1. Consumer → Gateway (authenticated request with queueId)
2. Gateway → Requests Connector (with environment context headers)
3. Requests Connector queries provider registry (cached)
4. Requests Connector detects provider supports only IMMEDIATE
5. Requests Connector stores request metadata in Redis work queue
6. Requests Connector returns HTTP 202 Accepted to Consumer
7. Background worker polls Redis work queue
8. Worker makes synchronous HTTP call to Provider
9. Provider processes and returns response synchronously
10. Worker routes response to Responses Connector with Consumer's queueId
11. Responses Connector enqueues response to Consumer's queue

**Timing**: HTTP 202 returned immediately (< 200ms), actual response delivered asynchronously (typically < 5 seconds)

---

## Error Codes Reference

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `NO_PROVIDER` | 404 | No provider registered for the requested service/zone/context |
| `NO_SUBSCRIPTION` | 404 | Provider has no request subscription (DELAYED pattern) |
| `MISSING_HEADER` | 400 | Required header missing (X-Environment-Id, X-Environment-Name, queueId) |
| `UNSUPPORTED_PATTERN` | 400 | Provider doesn't support the required message exchange pattern |
| `VALIDATION_ERROR` | 400 | Request validation failed (invalid serviceName, zoneId, etc.) |
| `SERVICE_UNAVAILABLE` | 503 | Core service (Environment, Queue, Subscription) or provider unavailable |
| `PROVIDER_TIMEOUT` | 504 | Provider call timed out |
| `PROVIDER_ERROR` | 4xx | Provider returned an error response (forwarded to consumer) |
| `INTERNAL_ERROR` | 500 | Unexpected internal error |

---

## Rate Limiting

Rate limiting is handled by the API Gateway. The Requests Connector does not implement rate limiting.

---

## Versioning

The API uses URL versioning: `/api/v1/requests`

Future versions will be available at `/api/v2/requests`, etc.

---

## Support

For issues or questions:
- Check the [README](README.md) for configuration and troubleshooting
- Review the [Runbook](RUNBOOK.md) for operational procedures
- Contact the SIF Unity Broker team
