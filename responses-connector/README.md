# Responses Connector

## Overview

The Responses Connector is a connector service within the SIF Unity Broker system responsible for receiving Provider responses for DELAYED requests and routing them to Consumer queues. It handles response correlation with original requests, manages response timeouts and errors, and ensures reliable delivery of asynchronous responses.

## Service Characteristics

- **Service Type**: Connector Service
- **Port**: 8084
- **Database Access**: None (calls Queue Service via REST APIs)
- **Stateless**: Yes (enables horizontal scaling)
- **Dependencies**: Queue Service

## Key Responsibilities

1. **Response Reception**: Accept responses from Providers and Requests Connector
2. **Response Routing**: Enqueue responses to consumer queues via Queue Service
3. **Response Correlation**: Maintain requestId correlation for matching responses to requests
4. **Error Response Handling**: Route error responses to consumer queues
5. **Response Validation**: Validate incoming responses before routing
6. **Resilience**: Handle Queue Service failures with retries and circuit breakers

## Architecture

### Integration Points

**Upstream Dependencies** (services that call Responses Connector):
- **API Gateway**: Routes provider responses to Responses Connector
- **Requests Connector**: Routes responses from DELAYED_to_IMMEDIATE pattern

**Downstream Dependencies** (services Responses Connector calls):
- **Queue Service**: Message enqueuing for consumer queues

### Response Flow Patterns

**Provider Response Flow**:
1. Provider → Gateway (authenticated response with queueId and requestId headers)
2. Gateway → Responses Connector (with environment context headers)
3. Responses Connector validates response (queueId, requestId, payload)
4. Responses Connector constructs SIF message envelope
5. Responses Connector enqueues response to consumer's queue via Queue Service
6. Responses Connector returns HTTP 200 OK to Provider

**Requests Connector Response Flow** (DELAYED_to_IMMEDIATE):
1. Requests Connector makes synchronous call to IMMEDIATE-only Provider
2. Provider returns response to Requests Connector
3. Requests Connector → Responses Connector (internal API with queueId and requestId)
4. Responses Connector validates response
5. Responses Connector constructs SIF message envelope
6. Responses Connector enqueues response to consumer's queue via Queue Service
7. Responses Connector returns success to Requests Connector

## API Endpoints

### Provider Response Endpoint

```
POST /api/v1/responses
Content-Type: application/json
X-Environment-Id: {uuid}
X-Environment-Name: {name}
queueId: {consumer-queue-uuid}
requestId: {original-request-uuid}

Request Body:
{
  "serviceName": "StudentPersonal",
  "serviceType": "OBJECT",
  "messageType": "RESPONSE",
  "payload": {
    // SIF response payload
  }
}

Response: 200 OK
{
  "messageId": "uuid",
  "queueId": "uuid",
  "status": "DELIVERED",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Error Response Endpoint — ❌ REMOVED

> **This endpoint no longer exists.** It corresponded to no SIF concept: the specification conveys
> errors as an **Error Object carried in the response payload**, with the code in the HTTP status
> field, and defines no separate error endpoint. It also had no callers anywhere in the repository.
>
> **Instead:** POST to `/{domainId}/{solutionId}/responses` with a SIF Error Object as the payload.
>
> The *internal* equivalent (`POST /internal/responses/error`) is retained — requests-connector uses
> it to report proxy and paging failures to the consumer — but it is not publicly reachable.
>
> The block below is kept only as a record of the removed shape. Do not build against it.

```
POST /api/v1/responses/error   (REMOVED)
Content-Type: application/json
X-Environment-Id: {uuid}
X-Environment-Name: {name}
queueId: {consumer-queue-uuid}
requestId: {original-request-uuid}

Request Body:
{
  "error": {
    "code": "PROVIDER_ERROR",
    "message": "Provider failed to process request",
    "description": "Detailed error description",
    "scope": "PROVIDER"
  }
}

Response: 200 OK
{
  "messageId": "uuid",
  "queueId": "uuid",
  "status": "ERROR_DELIVERED",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Internal Response Endpoint (for Requests Connector)

```
POST /internal/responses
Content-Type: application/json
queueId: {consumer-queue-uuid}
requestId: {original-request-uuid}

Request Body:
{
  "serviceName": "StudentPersonal",
  "serviceType": "OBJECT",
  "messageType": "RESPONSE",
  "payload": {
    // SIF response payload
  }
}

Response: 200 OK
{
  "messageId": "uuid",
  "status": "DELIVERED"
}
```

## Configuration

### Application Properties

```yaml
spring:
  application:
    name: responses-connector

server:
  port: 8084

queue:
  service:
    url: http://queue-service:8082
    connection-timeout: 5000
    read-timeout: 10000
    max-retries: 3
    retry-delay: 100
```

### Environment Variables

- `QUEUE_SERVICE_URL`: Queue Service base URL (default: http://localhost:8082)
- `QUEUE_SERVICE_CONNECTION_TIMEOUT`: Connection timeout in milliseconds (default: 5000)
- `QUEUE_SERVICE_READ_TIMEOUT`: Read timeout in milliseconds (default: 10000)
- `QUEUE_SERVICE_MAX_RETRIES`: Maximum retry attempts (default: 3)
- `QUEUE_SERVICE_RETRY_DELAY`: Initial retry delay in milliseconds (default: 100)

## Building and Running

### Prerequisites

- Java 17 (Amazon Corretto recommended)
- Maven 3.8+
- Queue Service running on port 8082

### Build

```bash
mvn clean install
```

### Run

```bash
mvn spring-boot:run
```

Or run the JAR:

```bash
java -jar target/responses-connector-1.0.0-SNAPSHOT.jar
```

### Run Tests

```bash
# Run all tests
mvn test

# Run unit tests only
mvn test -Dtest="*Test"

# Run property-based tests only
mvn test -Dtest="*PropertiesTest"
```

## Testing

The service includes comprehensive testing:

- **Unit Tests**: Test individual components in isolation
- **Property-Based Tests**: Verify universal properties across randomized inputs using jqwik
- **Integration Tests**: Test end-to-end response routing flow with WireMock

### Test Coverage

Target: 80%+ code coverage

## Monitoring

### Health Check

```
GET /actuator/health
```

### Metrics

```
GET /actuator/metrics
```

Key metrics:
- `responses.received.total`: Total number of responses received
- `responses.routed.total`: Total number of responses successfully routed
- `responses.failed.total`: Total number of failed routing attempts
- `queue.service.calls.total`: Total Queue Service API calls
- `queue.service.errors.total`: Total Queue Service errors

## Error Handling

All error responses conform to SIF 3.7 error format:

```json
{
  "error": {
    "code": "QUEUE_NOT_FOUND",
    "message": "Queue not found: {queueId}",
    "description": "The specified queue does not exist",
    "scope": "BROKER",
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

### Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| VALIDATION_ERROR | 400 | Request validation failed |
| MISSING_HEADER | 400 | Required header missing |
| QUEUE_NOT_FOUND | 404 | Queue does not exist |
| SERVICE_UNAVAILABLE | 503 | Queue Service unavailable |
| GATEWAY_TIMEOUT | 504 | Queue Service timeout |
| INTERNAL_ERROR | 500 | Unexpected error |

## Deployment

### Docker

```bash
docker build -t responses-connector:latest .
docker run -p 8084:8084 responses-connector:latest
```

### Kubernetes

```bash
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

## Development

### Project Structure

```
responses-connector/
├── src/main/java/org/sifunity/responsesconnector/
│   ├── controller/          # REST API endpoints
│   ├── service/             # Business logic
│   ├── client/              # HTTP clients for other services
│   ├── dto/                 # Request/Response DTOs
│   ├── config/              # Configuration
│   ├── exception/           # Custom exceptions
│   └── util/                # Utilities
└── src/test/java/org/sifunity/responsesconnector/
    ├── property/            # Property-based tests
    ├── unit/                # Unit tests
    └── integration/         # Integration tests
```

## License

This project is licensed under the MIT License. See [LICENSE](../LICENSE) for details.
