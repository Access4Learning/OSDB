# Events Connector API Documentation

## Overview

The Events Connector provides a REST API for providers to publish events to the SIF Unity Broker. Events are distributed to all subscriber queues that have matching subscriptions based on service name, zone, and context.

## Base URL

```
http://events-connector:8084
```

## Authentication

All requests must be authenticated by the API Gateway before reaching the Events Connector. The Gateway injects environment context headers that identify the authenticated provider's environment.

**Required Gateway Headers:**
- `X-Environment-Id`: UUID of the authenticated environment
- `X-Environment-Name`: Name of the authenticated environment

The Events Connector does NOT perform authentication - it assumes all requests have been authenticated by the Gateway.

---

## Endpoints

### POST /events

Publish an event for distribution to all matching subscriber queues.

#### Request

**Headers:**

| Header | Type | Required | Description | Example |
|--------|------|----------|-------------|---------|
| `X-Environment-Id` | UUID | Yes | Environment ID (injected by Gateway) | `550e8400-e29b-41d4-a716-446655440000` |
| `X-Environment-Name` | String | Yes | Environment name (injected by Gateway) | `Production` |
| `serviceName` | String | Yes | Service/object type name | `StudentPersonal` |
| `zoneId` | String | Yes | Zone identifier | `zone-1` |
| `contextId` | String | No | Context identifier (defaults to `DEFAULT`) | `DEFAULT` |
| `messageId` | UUID | No | Unique message identifier (auto-generated if not provided) | `123e4567-e89b-12d3-a456-426614174000` |
| `timestamp` | ISO-8601 | No | Event timestamp (set to current time if not provided) | `2024-01-15T10:30:00Z` |
| `Content-Type` | String | Yes | Must be `application/json` | `application/json` |

**Body:**

Event payload as JSON. The structure depends on the event type and service.

```json
{
  "eventAction": "CREATE",
  "objectType": "StudentPersonal",
  "object": {
    "refId": "12345678-1234-1234-1234-123456789012",
    "localId": "S12345",
    "name": {
      "familyName": "Smith",
      "givenName": "John"
    },
    "demographics": {
      "birthDate": "2010-05-15"
    }
  }
}
```

#### Response

**Success (200 OK)** - All queues succeeded:

```json
{
  "messageId": "87654321-4321-4321-4321-210987654321",
  "timestamp": "2024-01-15T10:30:00Z",
  "subscriberCount": 3,
  "deliveryStatus": "SUCCESS",
  "deliveryResults": [
    {
      "queueId": "queue-1",
      "status": "SUCCESS",
      "error": null
    },
    {
      "queueId": "queue-2",
      "status": "SUCCESS",
      "error": null
    },
    {
      "queueId": "queue-3",
      "status": "SUCCESS",
      "error": null
    }
  ]
}
```

**Success (200 OK)** - No subscribers:

```json
{
  "messageId": "87654321-4321-4321-4321-210987654321",
  "timestamp": "2024-01-15T10:30:00Z",
  "subscriberCount": 0,
  "deliveryStatus": "NO_SUBSCRIBERS",
  "deliveryResults": []
}
```

**Partial Success (207 Multi-Status)** - Some queues succeeded, some failed:

```json
{
  "messageId": "87654321-4321-4321-4321-210987654321",
  "timestamp": "2024-01-15T10:30:00Z",
  "subscriberCount": 3,
  "deliveryStatus": "PARTIAL",
  "deliveryResults": [
    {
      "queueId": "queue-1",
      "status": "SUCCESS",
      "error": null
    },
    {
      "queueId": "queue-2",
      "status": "SUCCESS",
      "error": null
    },
    {
      "queueId": "queue-3",
      "status": "FAILED",
      "error": "Queue Service unavailable: Connection timeout"
    }
  ]
}
```

**Validation Error (400 Bad Request)** - Missing required header:

```json
{
  "error": {
    "code": "VALIDATION_001",
    "message": "Missing required header: serviceName is required",
    "description": "Invalid event data provided",
    "scope": "EVENTS_CONNECTOR",
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

**Validation Error (400 Bad Request)** - Missing environment context:

```json
{
  "error": {
    "code": "VALIDATION_001",
    "message": "Missing environment context: X-Environment-Id header is required",
    "description": "Invalid event data provided",
    "scope": "EVENTS_CONNECTOR",
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

**Complete Failure (500 Internal Server Error)** - All queues failed:

```json
{
  "messageId": "87654321-4321-4321-4321-210987654321",
  "timestamp": "2024-01-15T10:30:00Z",
  "subscriberCount": 3,
  "deliveryStatus": "FAILED",
  "deliveryResults": [
    {
      "queueId": "queue-1",
      "status": "FAILED",
      "error": "Queue Service unavailable"
    },
    {
      "queueId": "queue-2",
      "status": "FAILED",
      "error": "Queue Service unavailable"
    },
    {
      "queueId": "queue-3",
      "status": "FAILED",
      "error": "Queue Service unavailable"
    }
  ]
}
```

**Service Unavailable (503 Service Unavailable)** - Subscription Service unavailable:

```json
{
  "error": {
    "code": "SUBSCRIPTION_SERVICE_001",
    "message": "Failed to query subscriptions",
    "description": "Unable to query subscriptions for event distribution",
    "scope": "EVENTS_CONNECTOR",
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

#### Status Codes

| Status Code | Description |
|-------------|-------------|
| `200 OK` | Event distributed successfully to all subscribers OR no subscribers found |
| `207 Multi-Status` | Event distributed to some subscribers (partial success) |
| `400 Bad Request` | Validation error (missing required headers or invalid data) |
| `500 Internal Server Error` | All queue deliveries failed |
| `503 Service Unavailable` | Subscription Service or Queue Service unavailable |

---

## Response Schema

### EventDistributionResponse

| Field | Type | Description |
|-------|------|-------------|
| `messageId` | UUID | Unique message identifier |
| `timestamp` | ISO-8601 | Event timestamp |
| `subscriberCount` | Integer | Number of subscribers found |
| `deliveryStatus` | Enum | Overall delivery status: `SUCCESS`, `PARTIAL`, `FAILED`, `NO_SUBSCRIBERS` |
| `deliveryResults` | Array | List of delivery results for each queue |

### QueueDeliveryResult

| Field | Type | Description |
|-------|------|-------------|
| `queueId` | String | Queue identifier |
| `status` | Enum | Delivery status: `SUCCESS`, `FAILED` |
| `error` | String | Error message (null if successful) |

### SifErrorResponse

| Field | Type | Description |
|-------|------|-------------|
| `error.code` | String | Error code (e.g., `VALIDATION_001`) |
| `error.message` | String | Error message |
| `error.description` | String | Detailed error description |
| `error.scope` | String | Service scope (always `EVENTS_CONNECTOR`) |
| `error.timestamp` | ISO-8601 | Error timestamp |

---

## Examples

### Example 1: Successful Event Distribution

**Request:**

```bash
curl -X POST http://events-connector:8084/events \
  -H "Content-Type: application/json" \
  -H "X-Environment-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -H "X-Environment-Name: Production" \
  -H "serviceName: StudentPersonal" \
  -H "zoneId: zone-1" \
  -H "contextId: DEFAULT" \
  -d '{
    "eventAction": "CREATE",
    "objectType": "StudentPersonal",
    "object": {
      "refId": "12345678-1234-1234-1234-123456789012",
      "localId": "S12345",
      "name": {
        "familyName": "Smith",
        "givenName": "John"
      }
    }
  }'
```

**Response (200 OK):**

```json
{
  "messageId": "87654321-4321-4321-4321-210987654321",
  "timestamp": "2024-01-15T10:30:00Z",
  "subscriberCount": 2,
  "deliveryStatus": "SUCCESS",
  "deliveryResults": [
    {
      "queueId": "queue-1",
      "status": "SUCCESS",
      "error": null
    },
    {
      "queueId": "queue-2",
      "status": "SUCCESS",
      "error": null
    }
  ]
}
```

### Example 2: Event with No Subscribers

**Request:**

```bash
curl -X POST http://events-connector:8084/events \
  -H "Content-Type: application/json" \
  -H "X-Environment-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -H "X-Environment-Name: Production" \
  -H "serviceName: SchoolInfo" \
  -H "zoneId: zone-2" \
  -d '{
    "eventAction": "UPDATE",
    "objectType": "SchoolInfo",
    "object": {
      "refId": "98765432-9876-9876-9876-987654321098",
      "schoolName": "Test School"
    }
  }'
```

**Response (200 OK):**

```json
{
  "messageId": "11111111-1111-1111-1111-111111111111",
  "timestamp": "2024-01-15T10:35:00Z",
  "subscriberCount": 0,
  "deliveryStatus": "NO_SUBSCRIBERS",
  "deliveryResults": []
}
```

### Example 3: Partial Delivery Failure

**Request:**

```bash
curl -X POST http://events-connector:8084/events \
  -H "Content-Type: application/json" \
  -H "X-Environment-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -H "X-Environment-Name: Production" \
  -H "serviceName: StudentPersonal" \
  -H "zoneId: zone-1" \
  -d '{
    "eventAction": "DELETE",
    "objectType": "StudentPersonal",
    "object": {
      "refId": "12345678-1234-1234-1234-123456789012"
    }
  }'
```

**Response (207 Multi-Status):**

```json
{
  "messageId": "22222222-2222-2222-2222-222222222222",
  "timestamp": "2024-01-15T10:40:00Z",
  "subscriberCount": 3,
  "deliveryStatus": "PARTIAL",
  "deliveryResults": [
    {
      "queueId": "queue-1",
      "status": "SUCCESS",
      "error": null
    },
    {
      "queueId": "queue-2",
      "status": "FAILED",
      "error": "Queue Service unavailable: Connection timeout"
    },
    {
      "queueId": "queue-3",
      "status": "SUCCESS",
      "error": null
    }
  ]
}
```

### Example 4: Missing Required Header

**Request:**

```bash
curl -X POST http://events-connector:8084/events \
  -H "Content-Type: application/json" \
  -H "X-Environment-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -H "X-Environment-Name: Production" \
  -H "zoneId: zone-1" \
  -d '{
    "eventAction": "CREATE",
    "objectType": "StudentPersonal",
    "object": {}
  }'
```

**Response (400 Bad Request):**

```json
{
  "error": {
    "code": "VALIDATION_001",
    "message": "Missing required header: serviceName is required",
    "description": "Invalid event data provided",
    "scope": "EVENTS_CONNECTOR",
    "timestamp": "2024-01-15T10:45:00Z"
  }
}
```

### Example 5: Missing Environment Context

**Request:**

```bash
curl -X POST http://events-connector:8084/events \
  -H "Content-Type: application/json" \
  -H "serviceName: StudentPersonal" \
  -H "zoneId: zone-1" \
  -d '{
    "eventAction": "CREATE",
    "objectType": "StudentPersonal",
    "object": {}
  }'
```

**Response (400 Bad Request):**

```json
{
  "error": {
    "code": "VALIDATION_001",
    "message": "Missing environment context: X-Environment-Id header is required",
    "description": "Invalid event data provided",
    "scope": "EVENTS_CONNECTOR",
    "timestamp": "2024-01-15T10:50:00Z"
  }
}
```

---

## Error Codes

| Code | Description |
|------|-------------|
| `VALIDATION_001` | Validation error (missing or invalid headers/data) |
| `SUBSCRIPTION_SERVICE_001` | Subscription Service unavailable or error |
| `QUEUE_SERVICE_001` | Queue Service unavailable or error |
| `INTERNAL_ERROR_001` | Unexpected internal server error |

---

## Rate Limiting

Rate limiting is enforced by the API Gateway. The Events Connector itself does not implement rate limiting.

---

## Best Practices

### 1. Always Provide messageId

While the `messageId` header is optional, it's recommended to provide a unique UUID for each event. This enables:
- Event deduplication
- Event tracking and debugging
- Correlation across services

```bash
-H "messageId: $(uuidgen)"
```

### 2. Use Appropriate contextId

The `contextId` header allows for additional routing dimensions. Use meaningful context identifiers:
- `DEFAULT`: Default context (used if not specified)
- `PRODUCTION`: Production data
- `TEST`: Test data
- Custom contexts as needed

### 3. Handle Partial Delivery

When receiving a `207 Multi-Status` response, inspect the `deliveryResults` array to identify which queues failed. Consider:
- Logging failed deliveries for investigation
- Implementing retry logic for failed queues
- Alerting on high failure rates

### 4. Monitor No Subscribers

Events with `NO_SUBSCRIBERS` status indicate no matching subscriptions exist. This may be expected or may indicate:
- Missing subscriptions
- Incorrect serviceName/zoneId/contextId
- Configuration issues

### 5. Implement Timeout Handling

Set appropriate timeouts for event publication requests:
- Connection timeout: 5 seconds
- Read timeout: 30 seconds (to allow for fan-out to many queues)

### 6. Use Structured Event Payloads

Follow SIF 3.7 event structure:
```json
{
  "eventAction": "CREATE|UPDATE|DELETE",
  "objectType": "ServiceName",
  "object": { /* SIF object data */ }
}
```

### 7. Log Correlation IDs

The Events Connector generates correlation IDs for request tracing. Include these in your logs for end-to-end tracing.

---

## Health Checks

### Liveness Probe

Check if the service is running:

```bash
curl http://events-connector:8084/actuator/health/liveness
```

### Readiness Probe

Check if the service is ready to accept traffic:

```bash
curl http://events-connector:8084/actuator/health/readiness
```

### Full Health Check

Get detailed health information including downstream service status:

```bash
curl http://events-connector:8084/actuator/health
```

---

## Metrics

Prometheus metrics are exposed at:

```bash
curl http://events-connector:8084/actuator/prometheus
```

Key metrics:
- `events_received_count`: Total events received (by service, zone)
- `events_distributed_count`: Total events distributed (by status)
- `subscription_query_latency`: Subscription Service query latency
- `queue_enqueue_latency`: Queue Service enqueue latency
- `fan_out_size`: Distribution of fan-out sizes (histogram)

---

## Troubleshooting

### Issue: 400 Bad Request - Missing required header

**Cause:** Required header (serviceName, zoneId, X-Environment-Id, or X-Environment-Name) is missing.

**Solution:** Ensure all required headers are included in the request.

### Issue: 503 Service Unavailable

**Cause:** Subscription Service or Queue Service is unavailable.

**Solution:** 
1. Check if Subscription Service is running and accessible
2. Check if Queue Service is running and accessible
3. Check network connectivity between services
4. Review service logs for errors

### Issue: 207 Multi-Status - Partial delivery

**Cause:** Some queues succeeded but others failed (typically due to Queue Service issues).

**Solution:**
1. Review `deliveryResults` to identify failed queues
2. Check Queue Service health and logs
3. Verify queue existence
4. Consider implementing retry logic

### Issue: 500 Internal Server Error - All queues failed

**Cause:** All queue deliveries failed (typically due to Queue Service being completely unavailable).

**Solution:**
1. Check Queue Service health and availability
2. Review Queue Service logs
3. Check network connectivity
4. Verify Queue Service configuration

---

## Support

For issues or questions:
1. Check service logs: `kubectl logs -f deployment/events-connector`
2. Check health endpoints: `/actuator/health`
3. Review metrics: `/actuator/prometheus`
4. Contact the SIF Unity Broker support team
