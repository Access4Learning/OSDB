# Responses Connector API Documentation

## Overview

The Responses Connector provides REST APIs for receiving provider responses for DELAYED requests and routing them to consumer queues. It handles both external provider responses (via Gateway) and internal responses (from Requests Connector).

**Base URL**: `http://responses-connector:8084`

**Version**: 1.0.0

---

## Authentication

### External API (Provider Responses)

All external API requests must be authenticated by the API Gateway. The Gateway injects environment context headers:

- `X-Environment-Id`: UUID of the environment
- `X-Environment-Name`: Name of the environment

The Responses Connector does NOT perform authentication - it assumes all requests have been authenticated by the Gateway.

### Internal API (Requests Connector)

Internal API endpoints are accessible without authentication within the trusted network. These endpoints should NOT be exposed externally.

---

## API Endpoints

### 1. Submit Provider Response

Route a provider response to a consumer queue.

**Endpoint**: `POST /api/v1/responses`

**Headers**:
- `Content-Type`: `application/json` (required)
- `X-Environment-Id`: UUID (required) - Injected by Gateway
- `X-Environment-Name`: String (required) - Injected by Gateway
- `queueId`: UUID (required) - Consumer's queue ID
- `requestId`: UUID (required) - Original request ID for correlation

**Request Body**:
```json
{
  "serviceName": "StudentPersonal",
  "serviceType": "OBJECT",
  "payload": {
    "refId": "12345",
    "name": {
      "givenName": "John",
      "familyName": "Doe"
    },
    "email": "john.doe@example.com"
  }
}
```

**Response**: `200 OK`
```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "queueId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "status": "DELIVERED",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**Error Responses**:

- `400 Bad Request` - Invalid request or missing headers
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "description": "serviceName: Service name is required",
  "scope": "BROKER",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

- `404 Not Found` - Queue does not exist
```json
{
  "code": "QUEUE_NOT_FOUND",
  "message": "Queue not found: 7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "scope": "BROKER",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

- `503 Service Unavailable` - Queue Service unavailable
```json
{
  "code": "SERVICE_UNAVAILABLE",
  "message": "Queue Service is currently unavailable",
  "description": "Connection refused",
  "scope": "BROKER",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**Example**:
```bash
curl -X POST http://responses-connector:8084/api/v1/responses \
  -H "Content-Type: application/json" \
  -H "X-Environment-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -H "X-Environment-Name: Production" \
  -H "queueId: 7c9e6679-7425-40de-944b-e07fc1f90ae7" \
  -H "requestId: 3fa85f64-5717-4562-b3fc-2c963f66afa6" \
  -d '{
    "serviceName": "StudentPersonal",
    "serviceType": "OBJECT",
    "payload": {
      "refId": "12345",
      "name": {
        "givenName": "John",
        "familyName": "Doe"
      }
    }
  }'
```

---

### 2. Submit Provider Error Response — ❌ REMOVED

> **This endpoint no longer exists — do not build against it.** It corresponded to no SIF concept:
> the specification conveys errors as an **Error Object carried in the response payload**, with the
> code in the HTTP status field, and defines no separate error endpoint. It also had no callers
> anywhere in the repository (no connector, extension, admin console, or Bruno step in either suite).
>
> **Instead:** submit to `POST /{domainId}/{solutionId}/responses` with a SIF Error Object as the
> payload.
>
> The *internal* equivalent (`POST /internal/responses/error`) is retained for requests-connector to
> report proxy and paging failures, but it is not publicly reachable.
>
> The section below is kept only as a record of the removed shape.

Route a provider error response to a consumer queue.

**Endpoint**: `POST /api/v1/responses/error` — **REMOVED**

**Headers**:
- `Content-Type`: `application/json` (required)
- `X-Environment-Id`: UUID (required) - Injected by Gateway
- `X-Environment-Name`: String (required) - Injected by Gateway
- `queueId`: UUID (required) - Consumer's queue ID
- `requestId`: UUID (required) - Original request ID for correlation

**Request Body**:
```json
{
  "error": {
    "code": "PROVIDER_ERROR",
    "message": "Provider failed to process request",
    "description": "Database connection timeout",
    "scope": "PROVIDER"
  }
}
```

**Response**: `200 OK`
```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "queueId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "status": "ERROR_DELIVERED",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**Error Responses**: Same as Submit Provider Response

**Example**:
```bash
curl -X POST http://responses-connector:8084/api/v1/responses/error \
  -H "Content-Type: application/json" \
  -H "X-Environment-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -H "X-Environment-Name: Production" \
  -H "queueId: 7c9e6679-7425-40de-944b-e07fc1f90ae7" \
  -H "requestId: 3fa85f64-5717-4562-b3fc-2c963f66afa6" \
  -d '{
    "error": {
      "code": "PROVIDER_ERROR",
      "message": "Provider failed to process request",
      "description": "Database connection timeout",
      "scope": "PROVIDER"
    }
  }'
```

---

### 3. Submit Internal Response (Requests Connector)

Route an internal response from Requests Connector to a consumer queue.

**Endpoint**: `POST /internal/responses`

**Headers**:
- `Content-Type`: `application/json` (required)
- `queueId`: UUID (required) - Consumer's queue ID
- `requestId`: UUID (required) - Original request ID for correlation

**Request Body**:
```json
{
  "serviceName": "StudentPersonal",
  "serviceType": "OBJECT",
  "payload": {
    "refId": "12345",
    "name": {
      "givenName": "John",
      "familyName": "Doe"
    }
  }
}
```

**Response**: `200 OK`
```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "DELIVERED"
}
```

**Error Responses**: Same as Submit Provider Response (without environment context)

**Example**:
```bash
curl -X POST http://responses-connector:8084/internal/responses \
  -H "Content-Type: application/json" \
  -H "queueId: 7c9e6679-7425-40de-944b-e07fc1f90ae7" \
  -H "requestId: 3fa85f64-5717-4562-b3fc-2c963f66afa6" \
  -d '{
    "serviceName": "StudentPersonal",
    "serviceType": "OBJECT",
    "payload": {
      "refId": "12345"
    }
  }'
```

---

### 4. Submit Internal Error Response (Requests Connector)

Route an internal error response from Requests Connector to a consumer queue.

**Endpoint**: `POST /internal/responses/error`

**Headers**:
- `Content-Type`: `text/plain` (required)
- `queueId`: UUID (required) - Consumer's queue ID
- `requestId`: UUID (required) - Original request ID for correlation

**Request Body**: Plain text error message
```
Provider timeout after 30 seconds
```

**Response**: `200 OK`
```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ERROR_DELIVERED"
}
```

**Error Responses**: Same as Submit Provider Response (without environment context)

**Example**:
```bash
curl -X POST http://responses-connector:8084/internal/responses/error \
  -H "Content-Type: text/plain" \
  -H "queueId: 7c9e6679-7425-40de-944b-e07fc1f90ae7" \
  -H "requestId: 3fa85f64-5717-4562-b3fc-2c963f66afa6" \
  -d "Provider timeout after 30 seconds"
```

---

## Health and Monitoring

### Health Check

**Endpoint**: `GET /actuator/health`

**Response**: `200 OK`
```json
{
  "status": "UP",
  "components": {
    "queueService": {
      "status": "UP",
      "details": {
        "url": "http://queue-service:8082"
      }
    },
    "diskSpace": {
      "status": "UP"
    }
  }
}
```

### Liveness Probe

**Endpoint**: `GET /actuator/health/liveness`

**Response**: `200 OK`
```json
{
  "status": "UP"
}
```

### Readiness Probe

**Endpoint**: `GET /actuator/health/readiness`

**Response**: `200 OK`
```json
{
  "status": "UP"
}
```

### Metrics

**Endpoint**: `GET /actuator/metrics`

**Response**: `200 OK`
```json
{
  "names": [
    "responses.received.total",
    "responses.routed.total",
    "responses.failed.total",
    "queue.service.calls.total",
    "queue.service.errors.total"
  ]
}
```

**Endpoint**: `GET /actuator/metrics/{metric-name}`

**Example**: `GET /actuator/metrics/responses.received.total`

**Response**: `200 OK`
```json
{
  "name": "responses.received.total",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 1234
    }
  ]
}
```

### Prometheus Metrics

**Endpoint**: `GET /actuator/prometheus`

**Response**: `200 OK` (Prometheus format)
```
# HELP responses_received_total Total number of responses received
# TYPE responses_received_total counter
responses_received_total 1234.0

# HELP responses_routed_total Total number of responses successfully routed
# TYPE responses_routed_total counter
responses_routed_total 1200.0

# HELP responses_failed_total Total number of failed routing attempts
# TYPE responses_failed_total counter
responses_failed_total 34.0
```

---

## Data Models

### ResponseDTO

```json
{
  "serviceName": "string (required)",
  "serviceType": "string (required)",
  "payload": "object (required)"
}
```

### ErrorResponseDTO

```json
{
  "error": {
    "code": "string (required)",
    "message": "string (required)",
    "description": "string (optional)",
    "scope": "string (required)"
  }
}
```

### ResponseResultDTO

```json
{
  "messageId": "uuid",
  "queueId": "uuid (optional)",
  "status": "string",
  "timestamp": "datetime (optional)"
}
```

### SifErrorResponse

```json
{
  "code": "string",
  "message": "string",
  "description": "string (optional)",
  "scope": "string",
  "timestamp": "datetime"
}
```

---

## Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| VALIDATION_ERROR | 400 | Request validation failed |
| MISSING_HEADER | 400 | Required header missing |
| QUEUE_NOT_FOUND | 404 | Queue does not exist |
| SERVICE_UNAVAILABLE | 503 | Queue Service unavailable |
| GATEWAY_TIMEOUT | 504 | Queue Service timeout |
| INTERNAL_ERROR | 500 | Unexpected error |

---

## Integration Patterns

### Provider Response Flow

1. Provider sends response to Gateway
2. Gateway authenticates and injects environment context
3. Gateway forwards to Responses Connector
4. Responses Connector validates request
5. Responses Connector constructs SIF message envelope
6. Responses Connector enqueues to consumer queue via Queue Service
7. Responses Connector returns success to Provider

### DELAYED_to_IMMEDIATE Flow

1. Requests Connector makes synchronous call to IMMEDIATE-only Provider
2. Provider returns response to Requests Connector
3. Requests Connector sends response to Responses Connector (internal API)
4. Responses Connector enqueues to consumer queue
5. Responses Connector returns success to Requests Connector

---

## Rate Limiting

The Responses Connector does not implement rate limiting. Rate limiting is handled by the API Gateway for external requests.

---

## Versioning

API version is included in the URL path: `/api/v1/responses`

Future versions will use `/api/v2/responses`, etc.

---

## Support

For issues or questions:
- Check service logs: `kubectl logs -l app=responses-connector`
- Check health endpoint: `GET /actuator/health`
- Review metrics: `GET /actuator/metrics`

---

## Changelog

### Version 1.0.0 (2024-01-15)
- Initial release
- Provider response routing
- Internal response routing (Requests Connector)
- Error response handling
- Queue Service integration with retry logic
- Health checks and metrics
