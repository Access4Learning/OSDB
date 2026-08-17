# Requests Connector

## Overview

The Requests Connector is a connector service within the SIF Unity Broker system responsible for routing consumer requests to providers. It implements both IMMEDIATE (synchronous) and DELAYED (asynchronous) message exchange patterns, with intelligent routing based on provider capabilities.

## Service Type

**Connector Service** - This service has:
- NO direct database access
- Calls Core Services via REST APIs (Environment Service, Queue Service, Subscription Service)
- Stateless architecture for horizontal scaling
- Focus on routing logic and message transformation

## Key Features

### Request Routing Patterns

1. **IMMEDIATE Pattern**: Synchronous request/response
   - Consumer sends request
   - Connector forwards to provider endpoint
   - Provider responds synchronously
   - Connector returns response to consumer

2. **DELAYED to DELAYED Pattern**: Asynchronous via provider queue
   - Consumer sends request with queueId
   - Connector queries provider's request subscription
   - Connector enqueues message to provider's queue
   - Returns HTTP 202 Accepted
   - Provider processes asynchronously

3. **DELAYED to IMMEDIATE Pattern**: Broker acts as proxy
   - Consumer sends request with queueId
   - Connector stores request for background processing
   - Returns HTTP 202 Accepted
   - Background worker makes synchronous call to provider
   - Worker routes response to consumer's queue via Responses Connector

### Additional Features

- **Provider Registry Caching**: Redis-based distributed cache with 5-minute TTL
- **Background Worker**: Processes DELAYED to IMMEDIATE requests with retry logic
- **Timeout Handling**: Configurable connection and read timeouts for provider calls
- **Error Handling**: Comprehensive error handling with SIF 3.7 compliant error responses
- **Observability**: Structured logging with correlation IDs and Prometheus metrics

## Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Language**: Java 17 (Amazon Corretto)
- **HTTP Client**: Spring WebClient (reactive, non-blocking)
- **Cache**: Redis (distributed caching)
- **Background Processing**: Spring @Scheduled
- **Validation**: Bean Validation (Jakarta)
- **Testing**: JUnit 5, jqwik (property-based testing), WireMock, Testcontainers

## Dependencies

### Core Services (Downstream)
- **Environment Service**: Provider registry queries
- **Queue Service**: Message enqueuing
- **Subscription Service**: Provider subscription queries
- **Responses Connector**: Response routing for DELAYED to IMMEDIATE pattern

### Shared Library
- **sif-broker-common**: Shared DTOs, exceptions, and utilities

## API Endpoints

### IMMEDIATE Request
```
POST /api/v1/requests/immediate
Headers:
  X-Environment-Id: {uuid}
  X-Environment-Name: {name}
  zoneId: {zone}
  contextId: {context}
Body: ImmediateRequestDTO
Response: 200 OK with RequestResponseDTO
```

### DELAYED Request
```
POST /api/v1/requests/delayed
Headers:
  X-Environment-Id: {uuid}
  X-Environment-Name: {name}
  zoneId: {zone}
  contextId: {context}
  queueId: {consumer-queue-uuid}
Body: DelayedRequestDTO
Response: 202 Accepted with RequestResponseDTO
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `REDIS_HOST` | Redis host | localhost |
| `REDIS_PORT` | Redis port | 6379 |
| `ENVIRONMENT_SERVICE_URL` | Environment Service URL | http://localhost:8081 |
| `QUEUE_SERVICE_URL` | Queue Service URL | http://localhost:8082 |
| `SUBSCRIPTION_SERVICE_URL` | Subscription Service URL | http://localhost:8083 |
| `RESPONSES_CONNECTOR_URL` | Responses Connector URL | http://localhost:8084 |
| `PROVIDER_CONNECTION_TIMEOUT` | Provider connection timeout (ms) | 5000 |
| `PROVIDER_READ_TIMEOUT` | Provider read timeout (ms) | 30000 |
| `PROXY_MAX_RETRIES` | Max retries for proxy requests | 3 |
| `PROXY_POLL_INTERVAL` | Worker poll interval (ms) | 1000 |

## Building and Running

### Prerequisites
- Java 17 or higher
- Maven 3.8+
- Redis 6.0+
- sif-broker-common library installed in local Maven repository

### Build
```bash
mvn clean install
```

### Run Locally
```bash
mvn spring-boot:run
```

### Run with Docker
```bash
docker build -t requests-connector:latest .
docker run -p 8080:8080 \
  -e REDIS_HOST=redis \
  -e ENVIRONMENT_SERVICE_URL=http://environment-service:8080 \
  requests-connector:latest
```

## Testing

### Run All Tests
```bash
mvn test
```

### Run Unit Tests Only
```bash
mvn test -Dtest="*Test"
```

### Run Property-Based Tests Only
```bash
mvn test -Dtest="*PropertiesTest"
```

### Run Integration Tests Only
```bash
mvn test -Dtest="*IntegrationTest"
```

## Project Structure

```
requests-connector/
├── src/main/java/org/sifunity/requestsconnector/
│   ├── controller/          # REST API endpoints
│   ├── service/             # Business logic
│   ├── client/              # HTTP clients for other services
│   ├── cache/               # Caching logic
│   ├── dto/                 # Request/Response DTOs
│   ├── config/              # Configuration classes
│   ├── exception/           # Custom exceptions
│   └── util/                # Utilities
├── src/test/java/org/sifunity/requestsconnector/
│   ├── unit/                # Unit tests
│   ├── property/            # Property-based tests
│   └── integration/         # Integration tests
└── src/main/resources/
    └── application.yml      # Application configuration
```

## Monitoring and Observability

### Health Check
```
GET /actuator/health
```

### Metrics
```
GET /actuator/metrics
GET /actuator/prometheus
```

### Key Metrics
- `requests.immediate.count`: Count of IMMEDIATE requests
- `requests.delayed.count`: Count of DELAYED requests
- `provider.call.latency`: Provider call latency histogram
- `cache.hit.rate`: Cache hit rate
- `proxy.queue.depth`: Background worker queue depth

## Error Handling

All errors conform to SIF 3.7 error format:

```json
{
  "code": "ERROR_CODE",
  "message": "Human-readable error message",
  "description": "Detailed error description",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Common Error Codes
- `NO_PROVIDER`: No provider registered (404)
- `NO_SUBSCRIPTION`: Provider has no request subscription (404)
- `VALIDATION_ERROR`: Request validation failed (400)
- `MISSING_HEADER`: Required header missing (400)
- `UNSUPPORTED_PATTERN`: Provider doesn't support pattern (400)
- `SERVICE_UNAVAILABLE`: Core service unavailable (503)
- `PROVIDER_TIMEOUT`: Provider call timed out (504)
- `INTERNAL_ERROR`: Unexpected error (500)

## Development

### Code Style
- Follow Java naming conventions
- Use Lombok for boilerplate reduction
- Write comprehensive JavaDoc for public APIs
- Maintain test coverage above 80%

### Testing Strategy
- **Unit Tests**: Test individual components in isolation
- **Property-Based Tests**: Test universal properties across all inputs (minimum 100 iterations)
- **Integration Tests**: Test end-to-end flows with mocked external services

## License

This project is licensed under the MIT License. See [LICENSE](../LICENSE) for details.
