# Services Connector

The Services Connector is a connector service within the SIF Unity Broker system responsible for managing Functional Services (Jobs) routing between consumers and providers. It handles job creation, state updates, phase management, and job queries by coordinating between consumers, providers, and the Job Service.

## Overview

As a Connector Service, the Services Connector has NO direct database access. It calls the Job Service via REST APIs to perform all job state management operations. The service is stateless to enable horizontal scaling and focuses purely on routing logic and request validation.

### Key Features

- **Job Creation Routing**: Routes job creation requests from consumers to Job Service and notifies providers
- **Job State Management**: Routes state updates from providers to Job Service
- **Phase Management**: Routes phase creation and updates to Job Service
- **Job Retrieval**: Queries Job Service for job details and lists
- **Provider Discovery**: Queries provider registry to determine routing destinations
- **Request Validation**: Validates incoming requests before routing
- **Caching**: Caches provider registry lookups for performance
- **Error Handling**: Handles Job Service, Environment Service, and provider failures gracefully

## Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Language**: Java 17 (Amazon Corretto)
- **HTTP Client**: Spring WebClient (reactive, non-blocking)
- **Cache**: Redis (distributed caching for provider registry)
- **Validation**: Bean Validation (javax.validation)
- **Shared Library**: sif-broker-common (DTOs, exceptions, utilities)
- **Testing**: JUnit 5, jqwik (property-based testing), WireMock (HTTP mocking)

## Architecture

The Services Connector follows a layered architecture:

```
services-connector/
├── controller/          # REST API endpoints
├── service/             # Business logic (routing)
├── client/              # HTTP clients for downstream services
├── cache/               # Caching logic
├── dto/                 # Request/Response DTOs
├── config/              # Configuration
├── exception/           # Custom exceptions
└── util/                # Utilities
```

## API Endpoints

All endpoints require the following headers:
- `X-Environment-Id`: UUID of the environment
- `X-Environment-Name`: Name of the environment

### Job Management

#### Create Job
```http
POST /api/v1/jobs
Content-Type: application/json
X-Environment-Id: {uuid}
X-Environment-Name: {name}

{
  "serviceName": "DataExport",
  "zoneId": "default",
  "contextId": "production",
  "metadata": "{\"key\":\"value\"}"
}
```

**Response**: `201 Created`
```json
{
  "id": "uuid",
  "environmentId": "uuid",
  "serviceName": "DataExport",
  "zoneId": "default",
  "contextId": "production",
  "state": "NOTSTARTED",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z",
  "currentPhase": null,
  "metadata": "{\"key\":\"value\"}"
}
```

#### Get Job by ID
```http
GET /api/v1/jobs/{jobId}
X-Environment-Id: {uuid}
X-Environment-Name: {name}
```

**Response**: `200 OK` (same structure as create response)

#### List Jobs
```http
GET /api/v1/jobs?state=INPROGRESS&serviceName=DataExport
X-Environment-Id: {uuid}
X-Environment-Name: {name}
```

**Query Parameters**:
- `state` (optional): Filter by job state (NOTSTARTED, INPROGRESS, COMPLETED, FAILED)
- `serviceName` (optional): Filter by service name

**Response**: `200 OK` (array of jobs)

#### Update Job State
```http
PUT /api/v1/jobs/{jobId}/state
Content-Type: application/json
X-Environment-Id: {uuid}
X-Environment-Name: {name}

{
  "state": "INPROGRESS",
  "metadata": "{\"progress\":50}"
}
```

**Response**: `200 OK` (updated job)

#### Delete Job
```http
DELETE /api/v1/jobs/{jobId}
X-Environment-Id: {uuid}
X-Environment-Name: {name}
```

**Response**: `204 No Content`

### Job Phase Management

#### Create Phase
```http
POST /api/v1/jobs/{jobId}/phases
Content-Type: application/json
X-Environment-Id: {uuid}
X-Environment-Name: {name}

{
  "phaseName": "processing",
  "state": "NOTSTARTED",
  "metadata": "{\"step\":1}"
}
```

**Response**: `201 Created`
```json
{
  "id": "uuid",
  "jobId": "uuid",
  "phaseName": "processing",
  "state": "NOTSTARTED",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z",
  "metadata": "{\"step\":1}"
}
```

#### Get Phases for Job
```http
GET /api/v1/jobs/{jobId}/phases
X-Environment-Id: {uuid}
X-Environment-Name: {name}
```

**Response**: `200 OK` (array of phases)

#### Update Phase State
```http
PUT /api/v1/jobs/{jobId}/phases/{phaseId}/state
Content-Type: application/json
X-Environment-Id: {uuid}
X-Environment-Name: {name}

{
  "state": "INPROGRESS",
  "metadata": "{\"progress\":75}"
}
```

**Response**: `200 OK` (updated phase)

## Configuration

### Application Properties

Key configuration properties in `application.yml`:

```yaml
server:
  port: 8085

spring:
  application:
    name: services-connector
  redis:
    host: localhost
    port: 6379
    timeout: 2000ms

# Downstream Service URLs
job:
  service:
    url: http://localhost:8083
    timeout:
      connection: 2000  # milliseconds
      read: 10000       # milliseconds

environment:
  service:
    url: http://localhost:8081
    timeout:
      connection: 2000  # milliseconds
      read: 10000       # milliseconds

# Provider Communication
provider:
  timeout:
    connection: 5000  # milliseconds
    read: 30000       # milliseconds
  cache:
    ttl: 300          # seconds (5 minutes)

# Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

### Environment Variables

Configuration can be overridden using environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | 8085 | Application port |
| `JOB_SERVICE_URL` | http://localhost:8083 | Job Service URL |
| `ENVIRONMENT_SERVICE_URL` | http://localhost:8081 | Environment Service URL |
| `SPRING_REDIS_HOST` | localhost | Redis host |
| `SPRING_REDIS_PORT` | 6379 | Redis port |
| `PROVIDER_TIMEOUT_CONNECTION` | 5000 | Provider connection timeout (ms) |
| `PROVIDER_TIMEOUT_READ` | 30000 | Provider read timeout (ms) |
| `PROVIDER_CACHE_TTL` | 300 | Provider cache TTL (seconds) |
| `JOB_SERVICE_TIMEOUT_CONNECTION` | 2000 | Job Service connection timeout (ms) |
| `JOB_SERVICE_TIMEOUT_READ` | 10000 | Job Service read timeout (ms) |

### Timeout Configuration

The service uses different timeouts for different types of calls:

- **Job Service**: Shorter timeouts (2s connection, 10s read) for fast internal service calls
- **Environment Service**: Shorter timeouts (2s connection, 10s read) for fast internal service calls
- **Provider Endpoints**: Longer timeouts (5s connection, 30s read) for external provider calls

### Caching Strategy

Provider registry lookups are cached in Redis with:
- Default TTL: 5 minutes (300 seconds)
- Cache key format: `provider:{serviceName}:{zoneId}:{contextId}`
- Distributed cache for horizontal scaling
- Automatic cache expiration

## Building and Running

### Prerequisites

- Java 17 or higher
- Maven 3.8+
- Redis server running on localhost:6379
- Job Service running on localhost:8083
- Environment Service running on localhost:8081

### Build

```bash
mvn clean package
```

### Run

```bash
mvn spring-boot:run
```

Or run the JAR:

```bash
java -jar target/services-connector-1.0.0-SNAPSHOT.jar
```

### Run Tests

```bash
# Run all tests
mvn test

# Run only unit tests
mvn test -Dtest=*Test

# Run only property-based tests
mvn test -Dtest=*PropertiesTest
```

## Testing

The service uses a comprehensive dual testing approach with three types of tests:

### Unit Tests

Test specific examples and edge cases:
- Controller tests with mocked services
- Service tests with mocked clients
- Client tests with WireMock
- Cache tests with mocked Redis
- DTO validation tests

**Run unit tests:**
```bash
mvn test -Dtest=*Test
```

### Property-Based Tests

Test universal properties across all inputs:
- Uses jqwik library for property-based testing
- Minimum 100 iterations per property test
- Tests correctness properties from design document
- Comprehensive input coverage through randomization

**Property examples:**
- Job creation queries provider registry
- Job Service operations are forwarded
- Provider unavailability does not fail job creation
- Cache hit avoids Environment Service call
- Error responses conform to SIF format

**Run property tests:**
```bash
mvn test -Dtest=*PropertiesTest
```

### Integration Tests

Test end-to-end flows with real dependencies:
- **WireMock Integration**: Mock Job Service, Environment Service, Provider endpoints
- **Redis Integration**: Test caching with embedded Redis
- **Complete Flow Testing**: Test full request/response cycles

**Integration test coverage:**
- Job creation flow (with provider notification)
- Job state update flow
- Phase management flow
- Redis caching behavior
- Error scenarios (404, 503, timeouts)

**Run integration tests:**
```bash
mvn test -Dtest=*IntegrationTest
```

### Run All Tests

```bash
# Run all tests
mvn test

# Run with coverage report
mvn test jacoco:report

# Skip tests during build
mvn package -DskipTests
```

### Test Coverage

The test suite provides comprehensive coverage:
- **Unit Tests**: ~60 tests covering all components
- **Property Tests**: ~20 properties with 100+ iterations each
- **Integration Tests**: ~15 end-to-end flow tests
- **Total**: 2000+ test executions per build

## Deployment

### Local Development

#### Prerequisites
- Java 17 or higher
- Maven 3.8+
- Redis server running on localhost:6379
- Job Service running on localhost:8083
- Environment Service running on localhost:8081

#### Run Locally
```bash
# Start Redis (if not running)
docker run -d -p 6379:6379 redis:7-alpine

# Build and run
mvn clean package
mvn spring-boot:run
```

### Docker

#### Build Docker Image
```bash
# Build the application
mvn clean package -DskipTests

# Build Docker image
docker build -t sif-unity-broker/services-connector:latest .
```

#### Run with Docker
```bash
docker run -p 8085:8085 \
  -e JOB_SERVICE_URL=http://job-service:8083 \
  -e ENVIRONMENT_SERVICE_URL=http://environment-service:8081 \
  -e SPRING_REDIS_HOST=redis \
  -e SPRING_REDIS_PORT=6379 \
  sif-unity-broker/services-connector:latest
```

#### Docker Compose
```yaml
version: '3.8'
services:
  services-connector:
    image: sif-unity-broker/services-connector:latest
    ports:
      - "8085:8085"
    environment:
      - JOB_SERVICE_URL=http://job-service:8083
      - ENVIRONMENT_SERVICE_URL=http://environment-service:8081
      - SPRING_REDIS_HOST=redis
      - SPRING_REDIS_PORT=6379
    depends_on:
      - redis
      - job-service
      - environment-service
  
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

### Kubernetes

#### Deploy to Kubernetes
```bash
# Apply all manifests
kubectl apply -f k8s/deployment.yaml

# Verify deployment
kubectl get deployments services-connector
kubectl get pods -l app=services-connector
kubectl get services services-connector

# Check logs
kubectl logs -l app=services-connector --tail=100

# Port forward for testing
kubectl port-forward svc/services-connector 8085:8085
```

#### Kubernetes Resources

The deployment includes:
- **Deployment**: 3 replicas with rolling update strategy
- **Service**: ClusterIP on port 8085
- **ConfigMap**: Application configuration
- **HorizontalPodAutoscaler**: Auto-scaling (3-10 replicas)
- **PodDisruptionBudget**: Minimum 2 pods available

See [k8s/README.md](k8s/README.md) for detailed Kubernetes deployment guide.

### Production Deployment Checklist

- [ ] Build and test Docker image
- [ ] Configure production URLs for Job Service and Environment Service
- [ ] Configure Redis connection (production instance)
- [ ] Set appropriate resource limits (memory, CPU)
- [ ] Configure monitoring and alerting
- [ ] Set up log aggregation
- [ ] Configure network policies
- [ ] Enable TLS/SSL for external communication
- [ ] Set up backup and disaster recovery
- [ ] Perform load testing
- [ ] Security scan completed
- [ ] Documentation updated

## Monitoring

### Health Check

```bash
# Basic health check
curl http://localhost:8085/actuator/health

# Detailed health check (requires authorization)
curl http://localhost:8085/actuator/health?showDetails=true
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "redis": {
      "status": "UP"
    },
    "diskSpace": {
      "status": "UP"
    }
  }
}
```

### Metrics

Prometheus metrics available at:

```bash
curl http://localhost:8085/actuator/prometheus
```

**Key metrics:**
- `http_server_requests_seconds` - HTTP request latency per endpoint
- `http_server_requests_total` - Total HTTP requests
- `jvm_memory_used_bytes` - JVM memory usage
- `jvm_gc_pause_seconds` - GC pause time
- `cache_gets_total` - Cache operations (hits/misses)
- Custom application metrics for Job Service, Environment Service, and Provider calls

### Actuator Endpoints

Available endpoints:
- `/actuator/health` - Health status
- `/actuator/health/liveness` - Liveness probe
- `/actuator/health/readiness` - Readiness probe
- `/actuator/metrics` - Available metrics
- `/actuator/metrics/{metric}` - Specific metric details
- `/actuator/info` - Application information
- `/actuator/prometheus` - Prometheus metrics export

### Monitoring Best Practices

1. **Set up alerts** for:
   - High error rates (>5%)
   - High latency (>1s p99)
   - Low cache hit rate (<80%)
   - Service unavailability

2. **Monitor key metrics**:
   - Request throughput and latency
   - Job Service call success rate
   - Provider notification success rate
   - Cache hit rate
   - Memory and CPU usage

3. **Log aggregation**:
   - Collect logs from all pods
   - Set up log-based alerts
   - Track correlation IDs for request tracing

## Troubleshooting

### Common Issues

#### Service Won't Start

**Symptom**: Application fails to start

**Possible causes:**
1. Redis not accessible
2. Port 8085 already in use
3. Invalid configuration

**Solutions:**
```bash
# Check Redis connection
redis-cli -h localhost -p 6379 ping

# Check port availability
netstat -an | grep 8085

# Check logs
tail -f logs/services-connector.log

# Verify configuration
cat src/main/resources/application.yml
```

#### High Latency

**Symptom**: Slow response times

**Possible causes:**
1. Job Service slow or unavailable
2. Environment Service slow or unavailable
3. Redis connection issues
4. High load

**Solutions:**
```bash
# Check downstream service health
curl http://localhost:8083/actuator/health  # Job Service
curl http://localhost:8081/actuator/health  # Environment Service

# Check Redis latency
redis-cli --latency -h localhost -p 6379

# Monitor metrics
curl http://localhost:8085/actuator/metrics/http.server.requests

# Check resource usage
top -p $(pgrep -f services-connector)
```

#### Cache Not Working

**Symptom**: High Environment Service call rate

**Possible causes:**
1. Redis not connected
2. Cache TTL too short
3. Cache keys not matching

**Solutions:**
```bash
# Check Redis connection
curl http://localhost:8085/actuator/health

# Check cache metrics
curl http://localhost:8085/actuator/metrics/cache.gets

# Monitor Redis keys
redis-cli -h localhost -p 6379 keys "provider:*"

# Check cache TTL
redis-cli -h localhost -p 6379 ttl "provider:DataExport:default:default"
```

#### Provider Notification Failures

**Symptom**: Jobs created but providers not notified

**Possible causes:**
1. Provider endpoint unreachable
2. Provider endpoint timeout
3. Invalid provider endpoint URL

**Solutions:**
```bash
# Check logs for provider errors
grep "Failed to notify provider" logs/services-connector.log

# Test provider endpoint manually
curl -X POST http://provider-endpoint/jobs \
  -H "Content-Type: application/json" \
  -d '{"id":"test"}'

# Verify provider registration
curl http://localhost:8081/api/v1/providers/lookup?serviceName=DataExport&zoneId=default
```

#### Memory Issues

**Symptom**: OutOfMemoryError or high memory usage

**Solutions:**
```bash
# Check memory usage
jmap -heap $(pgrep -f services-connector)

# Analyze heap dump
jmap -dump:format=b,file=heap.bin $(pgrep -f services-connector)

# Adjust JVM settings in Dockerfile
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

# Monitor GC activity
jstat -gc $(pgrep -f services-connector) 1000
```

### Debug Mode

Enable debug logging:

```yaml
logging:
  level:
    org.sifunity.servicesconnector: DEBUG
    org.springframework.web: DEBUG
```

Or via environment variable:
```bash
export LOGGING_LEVEL_ORG_SIFUNITY_SERVICESCONNECTOR=DEBUG
```

### Getting Help

1. **Check logs**: Look for error messages and stack traces
2. **Check metrics**: Review Prometheus metrics for anomalies
3. **Check health**: Verify all dependencies are healthy
4. **Review configuration**: Ensure all URLs and settings are correct
5. **Contact support**: Provide logs, metrics, and configuration details

## Dependencies

### Downstream Services

- **Job Service** (port 8083): Manages job state objects
- **Environment Service** (port 8081): Provides provider registry
- **Provider Applications**: Receive job notifications

### Infrastructure

- **Redis**: Distributed cache for provider registry lookups
- **API Gateway**: Routes authenticated requests to Services Connector

## Error Handling

All error responses conform to SIF 3.7 error format:

```json
{
  "code": "ROUTE_001",
  "message": "No provider registered for DataExport in zone default context production",
  "scope": "BROKER",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

Common error codes:
- `400 Bad Request`: Invalid request parameters or validation errors
- `404 Not Found`: Job not found or no provider registered
- `409 Conflict`: Invalid state transition or duplicate phase
- `503 Service Unavailable`: Job Service or Environment Service unavailable
- `500 Internal Server Error`: Unexpected errors

## Development

### Project Structure

```
services-connector/
├── src/
│   ├── main/
│   │   ├── java/org/sifunity/servicesconnector/
│   │   │   ├── ServicesConnectorApplication.java
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── client/
│   │   │   ├── cache/
│   │   │   ├── dto/
│   │   │   ├── config/
│   │   │   ├── exception/
│   │   │   └── util/
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       ├── java/org/sifunity/servicesconnector/
│       │   ├── property/
│       │   ├── unit/
│       │   └── integration/
│       └── resources/
│           └── application-test.yml
├── pom.xml
└── README.md
```

## License

This project is licensed under the MIT License. See [LICENSE](../LICENSE) for details.
