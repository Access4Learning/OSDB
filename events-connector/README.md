# Events Connector

## Overview

The Events Connector is a stateless connector service within the SIF Unity Broker system responsible for receiving event publications from Providers, querying the Subscription Service for matching subscriptions, and distributing events to all subscriber queues via the Queue Service.

### Service Purpose

The Events Connector implements the **EVENT message exchange pattern** from the SIF 3.7 specification. It acts as a fan-out service that:

1. **Receives Events**: Accepts event publications from Provider applications via REST API
2. **Queries Subscriptions**: Finds all subscribers interested in the event (matching service name, zone, and context)
3. **Distributes Events**: Delivers the event to all matching subscriber queues concurrently
4. **Handles Failures**: Manages partial delivery scenarios where some queues succeed and others fail

### Service Responsibilities

- **Event Reception**: Accept event publications from Providers via REST API
- **Subscription Querying**: Query Subscription Service for subscriptions matching event's service name, zone, and context
- **Event Distribution**: Deliver events to all matching subscriber queues via Queue Service
- **Fan-out Processing**: Efficiently distribute events to multiple subscribers concurrently using reactive programming
- **Partial Delivery Handling**: Handle scenarios where some queues succeed and others fail
- **Error Handling**: Provide clear error responses and comprehensive logging

### What This Service Does NOT Do

- **No Database Access**: The Events Connector is stateless and does not persist any data. It calls Core Services (Subscription Service and Queue Service) via REST APIs.
- **No Authentication**: The Events Connector assumes all requests have been authenticated by the API Gateway. It does not perform authentication or authorization checks.
- **No Event Storage**: Events are not stored by this service. They are immediately distributed to subscriber queues.
- **No Subscription Management**: Subscriptions are managed by the Subscription Service, not the Events Connector.
- **No Queue Management**: Queues are managed by the Queue Service, not the Events Connector.

### Architecture Position

```
Provider → Gateway → Events Connector → Subscription Service (query subscriptions)
                           ↓
                    Queue Service (enqueue to multiple queues)
                           ↓
                    Subscriber Queues
```

The Events Connector sits between the Gateway and the Core Services (Subscription Service and Queue Service), acting as an orchestrator for event distribution.

## Architecture Principles

- **Stateless**: No local state, enabling horizontal scaling
- **No Database Access**: Calls Core Services (Subscription Service, Queue Service) via REST APIs
- **No Authentication Logic**: Assumes requests are pre-authenticated by Gateway
- **Concurrent Processing**: Uses reactive programming (Project Reactor) for efficient fan-out
- **Resilient**: Implements retries, timeouts, and circuit breakers for service calls

## Technology Stack

- **Java 17**: Programming language
- **Spring Boot 3.2.0**: Application framework
- **Spring WebFlux**: Reactive HTTP client for concurrent processing
- **Spring Web**: REST API endpoints
- **Spring Boot Actuator**: Health checks and metrics
- **Resilience4j**: Circuit breaker and retry patterns
- **Lombok**: Reduce boilerplate code
- **Jackson**: JSON processing
- **Micrometer**: Metrics and observability

## Configuration

### Application Properties

Key configuration properties in `application.yml`:

```yaml
server:
  port: 8084

subscription-service:
  url: http://localhost:8082
  timeout:
    connection: 5000
    read: 10000

queue-service:
  url: http://localhost:8081
  timeout:
    connection: 5000
    read: 10000

retry:
  max-attempts: 3
  initial-backoff: 100
  max-backoff: 1000
```

### Environment Variables

The following environment variables can be used to override configuration:

- `SERVER_PORT`: Server port (default: 8084)
- `SUBSCRIPTION_SERVICE_URL`: Subscription Service URL
- `QUEUE_SERVICE_URL`: Queue Service URL
- `RETRY_MAX_ATTEMPTS`: Maximum retry attempts for service calls
- `LOGGING_LEVEL_ORG_SIFUNITY_EVENTSCONNECTOR`: Logging level

## API Endpoints

### POST /events

Publish an event to the broker for distribution to subscribers.

**Request Headers**:
- `X-Environment-Id`: Environment ID (injected by Gateway)
- `X-Environment-Name`: Environment name (injected by Gateway)
- `serviceName`: Service/object type name (e.g., StudentPersonal)
- `zoneId`: Zone identifier
- `contextId`: Context identifier (optional, defaults to DEFAULT)
- `messageId`: Unique message identifier (optional, generated if not provided)
- `timestamp`: ISO-8601 timestamp (optional, set to current time if not provided)

**Request Body**: Event payload (JSON)

**Response Codes**:
- `200 OK`: Event distributed successfully to all subscribers
- `207 Multi-Status`: Event distributed to some subscribers (partial success)
- `400 Bad Request`: Invalid event data or missing required headers
- `500 Internal Server Error`: All queue deliveries failed
- `503 Service Unavailable`: Subscription Service or Queue Service unavailable

## Building and Running

### Prerequisites

- **Java 17** or higher
- **Maven 3.8** or higher
- **Access to sif-broker-common library** (must be built and installed locally or available in Maven repository)
- **Subscription Service** running and accessible (or use mock service)
- **Queue Service** running and accessible (or use mock service)

### Build

Build the application using Maven:

```bash
mvn clean package
```

This will:
- Compile the source code
- Run all tests (unit, property-based, and integration tests)
- Create an executable JAR in `target/events-connector-1.0.0-SNAPSHOT.jar`

**Skip tests during build:**

```bash
mvn clean package -DskipTests
```

**Build without running property-based tests (faster):**

```bash
mvn clean package -Dtest="!*PropertiesTest"
```

### Run Locally

#### Option 1: Using Maven Spring Boot Plugin

```bash
mvn spring-boot:run
```

#### Option 2: Using the JAR

```bash
java -jar target/events-connector-1.0.0-SNAPSHOT.jar
```

#### Option 3: With Custom Configuration

```bash
java -jar target/events-connector-1.0.0-SNAPSHOT.jar \
  --subscription-service.url=http://localhost:8083 \
  --queue-service.url=http://localhost:8082 \
  --server.port=8084
```

#### Option 4: With Environment Variables

```bash
export SUBSCRIPTION_SERVICE_URL=http://localhost:8083
export QUEUE_SERVICE_URL=http://localhost:8082
export SERVER_PORT=8084
java -jar target/events-connector-1.0.0-SNAPSHOT.jar
```

#### Option 5: With Spring Profile

```bash
# Use application-dev.yml configuration
java -jar target/events-connector-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

### Local Development Setup

For local development, you need Subscription Service and Queue Service running. You have several options:

#### Option 1: Use Docker Compose (Recommended)

The easiest way to run locally with mock services:

```bash
docker-compose up -d
```

This starts:
- Events Connector on port 8084
- Mock Subscription Service on port 8082
- Mock Queue Service on port 8081

#### Option 2: Run Real Services Locally

1. Start Subscription Service:
   ```bash
   cd ../subscription-service
   mvn spring-boot:run
   ```

2. Start Queue Service:
   ```bash
   cd ../queue-service
   mvn spring-boot:run
   ```

3. Start Events Connector:
   ```bash
   cd ../events-connector
   mvn spring-boot:run
   ```

#### Option 3: Use WireMock for Testing

For testing without real services, use WireMock (already configured in integration tests):

```bash
# Run integration tests which use WireMock
mvn test -Dtest="*IntegrationTest"
```

### Verify Service is Running

Once started, verify the service is running:

```bash
# Check health
curl http://localhost:8084/actuator/health

# Check liveness
curl http://localhost:8084/actuator/health/liveness

# Check readiness
curl http://localhost:8084/actuator/health/readiness

# Check info
curl http://localhost:8084/actuator/info
```

### Configuration for Local Development

Create `application-dev.yml` for local development:

```yaml
spring:
  application:
    name: events-connector

server:
  port: 8084

subscription-service:
  url: http://localhost:8083
  timeout:
    connection: 5000
    read: 10000

queue-service:
  url: http://localhost:8082
  timeout:
    connection: 5000
    read: 10000

retry:
  max-attempts: 3
  initial-backoff: 100
  max-backoff: 1000

logging:
  level:
    org.sifunity.eventsconnector: DEBUG
    org.springframework.web: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

Run with dev profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Testing

The Events Connector has comprehensive test coverage including unit tests, property-based tests, and integration tests.

### Run All Tests

```bash
mvn test
```

### Run Unit Tests Only

Unit tests verify individual components in isolation with mocks:

```bash
mvn test -Dtest="*Test"
```

Unit test categories:
- **Controller Tests**: Test REST API endpoints with MockMvc
- **Service Tests**: Test business logic with mocked dependencies
- **Client Tests**: Test HTTP clients with WireMock
- **Exception Handler Tests**: Test error handling and responses

### Run Property-Based Tests Only

Property-based tests verify correctness properties across randomized inputs (100+ iterations per property):

```bash
mvn test -Dtest="*PropertiesTest"
```

Property test categories:
- **Event Distribution Properties**: Event acceptance, validation, distribution
- **Message Construction Properties**: SIF envelope construction, ID generation
- **Service Integration Properties**: Retry logic, timeout handling, error handling
- **Environment Context Properties**: Header extraction, validation
- **Observability Properties**: Logging, metrics, deduplication

### Run Integration Tests Only

Integration tests verify complete flows with full Spring context:

```bash
mvn test -Dtest="*IntegrationTest"
```

Integration test scenarios:
- Complete event distribution flow
- Multiple subscribers
- No subscribers
- Service unavailability
- Partial delivery failures

### Test Coverage

View test coverage report:

```bash
mvn clean test jacoco:report
```

Coverage report location: `target/site/jacoco/index.html`

**Coverage Goals:**
- Line Coverage: 80%+
- Branch Coverage: 75%+

### Manual Testing

#### Test Event Publication

```bash
curl -X POST http://localhost:8084/events \
  -H "Content-Type: application/json" \
  -H "X-Environment-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -H "X-Environment-Name: Test" \
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

#### Test with Missing Header

```bash
curl -X POST http://localhost:8084/events \
  -H "Content-Type: application/json" \
  -H "X-Environment-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -H "X-Environment-Name: Test" \
  -H "zoneId: zone-1" \
  -d '{
    "eventAction": "CREATE",
    "objectType": "StudentPersonal",
    "object": {}
  }'
```

Expected: 400 Bad Request with validation error

### Load Testing

Use Apache JMeter or Gatling for load testing:

**Target Performance:**
- 500 events per second throughput
- Event distribution within 200ms (excluding service latency)
- Subscription queries within 100ms
- Queue enqueue within 100ms per queue

**Example JMeter Test Plan:**
1. Thread Group: 50 threads, 10 second ramp-up
2. HTTP Request: POST /events with valid headers and payload
3. Assertions: Response code 200 or 207
4. Listeners: Aggregate Report, View Results Tree

## Health Checks

### Liveness Probe

```bash
curl http://localhost:8084/actuator/health/liveness
```

### Readiness Probe

```bash
curl http://localhost:8084/actuator/health/readiness
```

### Full Health Check

```bash
curl http://localhost:8084/actuator/health
```

## Metrics

Prometheus metrics are exposed at:

```bash
curl http://localhost:8084/actuator/prometheus
```

Key metrics:
- `events_received_count`: Total events received
- `events_distributed_count`: Total events distributed (by status)
- `subscription_query_latency`: Subscription Service query latency
- `queue_enqueue_latency`: Queue Service enqueue latency
- `fan_out_size`: Distribution of fan-out sizes

## Docker

### Build Docker Image

The Events Connector uses a multi-stage Docker build for optimal image size:

```bash
docker build -t events-connector:latest .
```

The Dockerfile:
- Uses Maven to build the application in the first stage
- Uses Amazon Corretto 17 Alpine for the runtime stage
- Runs as a non-root user for security
- Includes health check configuration
- Exposes port 8084

### Run Docker Container

Run a single container:

```bash
docker run -p 8084:8084 \
  -e SUBSCRIPTION_SERVICE_URL=http://subscription-service:8083 \
  -e QUEUE_SERVICE_URL=http://queue-service:8082 \
  events-connector:latest
```

### Docker Compose

For local development and testing, use Docker Compose to run the Events Connector with mock services:

```bash
docker-compose up -d
```

This will start:
- Events Connector service (port 8084)
- Mock Subscription Service (port 8082)
- Mock Queue Service (port 8081)

**Stop services:**

```bash
docker-compose down
```

**View logs:**

```bash
docker-compose logs -f events-connector
```

**Rebuild after code changes:**

```bash
docker-compose up -d --build
```

## Deployment

### Kubernetes

Deploy using the provided Kubernetes manifests:

```bash
kubectl apply -f k8s/
```

This will create:
- **Deployment**: 3 replicas with rolling update strategy
- **Service**: ClusterIP service on port 8084
- **ConfigMap**: Configuration for service URLs and retry settings
- **HorizontalPodAutoscaler**: Auto-scaling from 3 to 10 replicas based on CPU/memory

**Check deployment status:**

```bash
kubectl get deployment events-connector
kubectl get pods -l app=events-connector
kubectl get service events-connector
kubectl get hpa events-connector-hpa
```

**View logs:**

```bash
kubectl logs -l app=events-connector --tail=100 -f
```

**Update configuration:**

```bash
# Edit configmap
kubectl edit configmap events-connector-config

# Restart deployment to pick up changes
kubectl rollout restart deployment/events-connector
```

**Scale manually:**

```bash
kubectl scale deployment events-connector --replicas=5
```

See [k8s/README.md](k8s/README.md) for detailed Kubernetes deployment instructions.

### Production Deployment Checklist

Before deploying to production:

1. **Configuration**
   - [ ] Update service URLs in ConfigMap
   - [ ] Configure appropriate resource limits
   - [ ] Set correct replica count (minimum 3 for HA)
   - [ ] Configure HPA thresholds based on load testing

2. **Security**
   - [ ] Ensure Gateway authentication is configured
   - [ ] Verify network policies restrict access
   - [ ] Run as non-root user (already configured in Dockerfile)
   - [ ] Use secrets for sensitive configuration

3. **Monitoring**
   - [ ] Configure Prometheus scraping
   - [ ] Set up Grafana dashboards
   - [ ] Configure alerts for high error rates
   - [ ] Configure alerts for service unavailability

4. **Logging**
   - [ ] Configure centralized logging (ELK, Splunk, etc.)
   - [ ] Set appropriate log levels
   - [ ] Ensure correlation IDs are captured

5. **Testing**
   - [ ] Run integration tests against staging environment
   - [ ] Perform load testing
   - [ ] Test failure scenarios (service unavailability)
   - [ ] Verify health checks work correctly

6. **Dependencies**
   - [ ] Ensure Subscription Service is deployed and healthy
   - [ ] Ensure Queue Service is deployed and healthy
   - [ ] Verify network connectivity between services

## Troubleshooting

### Check Logs

**Docker:**
```bash
docker logs <container-id>
docker logs -f events-connector  # Follow logs
```

**Kubernetes:**
```bash
kubectl logs -f deployment/events-connector -n sif-broker
kubectl logs <pod-name> --tail=100
```

**Log Levels:**

Adjust log levels in `application.yml` or via environment variables:

```yaml
logging:
  level:
    org.sifunity.eventsconnector: DEBUG  # DEBUG, INFO, WARN, ERROR
    org.springframework.web: INFO
```

Or via environment variable:
```bash
LOGGING_LEVEL_ORG_SIFUNITY_EVENTSCONNECTOR=DEBUG
```

### Common Issues

#### 1. Subscription Service Unavailable (503)

**Symptoms:**
- HTTP 503 Service Unavailable responses
- Error: "Failed to query subscriptions"
- Logs show connection errors to Subscription Service

**Diagnosis:**
```bash
# Check if Subscription Service is running
kubectl get pods -l app=subscription-service

# Check Subscription Service health
curl http://subscription-service:8083/actuator/health

# Check network connectivity
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -- \
  curl http://subscription-service:8083/actuator/health
```

**Solutions:**
- Ensure Subscription Service is deployed and running
- Verify service URL in ConfigMap is correct
- Check network policies allow traffic between services
- Review Subscription Service logs for errors
- Verify DNS resolution: `nslookup subscription-service`

#### 2. Queue Service Unavailable (503 or 207)

**Symptoms:**
- HTTP 207 Multi-Status with failed queue deliveries
- HTTP 503 if all queues fail
- Error: "Queue Service unavailable"
- Logs show connection errors to Queue Service

**Diagnosis:**
```bash
# Check if Queue Service is running
kubectl get pods -l app=queue-service

# Check Queue Service health
curl http://queue-service:8082/actuator/health

# Check network connectivity
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -- \
  curl http://queue-service:8082/actuator/health
```

**Solutions:**
- Ensure Queue Service is deployed and running
- Verify service URL in ConfigMap is correct
- Check network policies allow traffic between services
- Review Queue Service logs for errors
- Verify queues exist in Queue Service

#### 3. Circuit Breaker Open

**Symptoms:**
- Rapid failures without attempting service calls
- Logs show "Circuit breaker is open"
- Metrics show high failure rates

**Diagnosis:**
```bash
# Check circuit breaker metrics
curl http://localhost:8084/actuator/metrics/resilience4j.circuitbreaker.state

# Check failure rate
curl http://localhost:8084/actuator/metrics/resilience4j.circuitbreaker.failure.rate
```

**Solutions:**
- Wait for circuit breaker to half-open (default: 10 seconds)
- Fix underlying service issues causing failures
- Adjust circuit breaker thresholds if needed:
  ```yaml
  resilience4j:
    circuitbreaker:
      instances:
        subscriptionService:
          failure-rate-threshold: 50  # Increase if too sensitive
          wait-duration-in-open-state: 10s
  ```

#### 4. Timeout Errors

**Symptoms:**
- HTTP 504 Gateway Timeout
- Logs show "Read timeout" or "Connection timeout"
- Slow response times

**Diagnosis:**
```bash
# Check service response times
curl -w "@curl-format.txt" -o /dev/null -s http://localhost:8084/actuator/health

# Check metrics for latency
curl http://localhost:8084/actuator/metrics/subscription.query.latency
curl http://localhost:8084/actuator/metrics/queue.enqueue.latency
```

**Solutions:**
- Increase timeout configuration:
  ```yaml
  subscription-service:
    timeout:
      connection: 10000  # Increase from 5000
      read: 20000        # Increase from 10000
  ```
- Investigate slow downstream services
- Check network latency between services
- Consider scaling downstream services

#### 5. Missing Environment Context (400)

**Symptoms:**
- HTTP 400 Bad Request
- Error: "Missing environment context: X-Environment-Id header is required"

**Diagnosis:**
- Check if request is going through Gateway
- Verify Gateway is injecting environment headers

**Solutions:**
- Ensure all requests go through API Gateway
- Do not call Events Connector directly (bypass Gateway)
- Verify Gateway configuration for header injection
- Check Gateway logs for authentication issues

#### 6. High Memory Usage

**Symptoms:**
- Pods being OOMKilled
- High memory metrics
- Slow performance

**Diagnosis:**
```bash
# Check memory usage
kubectl top pods -l app=events-connector

# Check memory metrics
curl http://localhost:8084/actuator/metrics/jvm.memory.used
```

**Solutions:**
- Increase memory limits in deployment:
  ```yaml
  resources:
    limits:
      memory: "2Gi"  # Increase from 1Gi
  ```
- Check for memory leaks in logs
- Review heap dump if available
- Adjust JVM heap settings:
  ```bash
  JAVA_OPTS="-Xmx1g -Xms512m"
  ```

#### 7. Partial Delivery (207 Multi-Status)

**Symptoms:**
- HTTP 207 Multi-Status responses
- Some queues succeed, others fail
- Inconsistent event delivery

**Diagnosis:**
- Review `deliveryResults` in response
- Check Queue Service logs for specific queue errors
- Verify queue existence

**Solutions:**
- Investigate failed queues individually
- Check if queues exist in Queue Service
- Verify Queue Service has capacity
- Consider implementing retry logic in provider
- Monitor for patterns (same queues always failing)

#### 8. No Subscribers (200 OK but no delivery)

**Symptoms:**
- HTTP 200 OK response
- `deliveryStatus: NO_SUBSCRIBERS`
- `subscriberCount: 0`

**Diagnosis:**
- Verify subscriptions exist in Subscription Service:
  ```bash
  curl "http://subscription-service:8083/subscriptions?serviceName=StudentPersonal&zoneId=zone-1&contextId=DEFAULT"
  ```

**Solutions:**
- Create subscriptions in Subscription Service
- Verify serviceName, zoneId, contextId match subscription criteria
- Check if subscriptions are in correct environment
- Review subscription creation logs

### Performance Issues

#### Slow Event Distribution

**Diagnosis:**
```bash
# Check distribution latency
curl http://localhost:8084/actuator/metrics/events.distribution.latency

# Check fan-out size
curl http://localhost:8084/actuator/metrics/fan_out.size
```

**Solutions:**
- Verify concurrent processing is working (check logs for parallel enqueue)
- Increase connection pool size:
  ```yaml
  webclient:
    connection-pool:
      max-connections: 1000  # Increase from 500
  ```
- Scale Events Connector horizontally (increase replicas)
- Optimize downstream services (Subscription Service, Queue Service)

#### High CPU Usage

**Diagnosis:**
```bash
# Check CPU usage
kubectl top pods -l app=events-connector

# Check thread count
curl http://localhost:8084/actuator/metrics/jvm.threads.live
```

**Solutions:**
- Scale horizontally (increase replicas)
- Increase CPU limits
- Review for CPU-intensive operations in logs
- Check for infinite loops or excessive retries

### Health Check Failures

#### Liveness Probe Failing

**Symptoms:**
- Pods being restarted frequently
- Liveness probe failures in events

**Diagnosis:**
```bash
# Check liveness endpoint
curl http://localhost:8084/actuator/health/liveness

# Check pod events
kubectl describe pod <pod-name>
```

**Solutions:**
- Increase `initialDelaySeconds` if service takes longer to start
- Increase `timeoutSeconds` if health check is slow
- Review application logs for startup issues
- Check for deadlocks or blocking operations

#### Readiness Probe Failing

**Symptoms:**
- Pods not receiving traffic
- Service endpoints empty
- Readiness probe failures in events

**Diagnosis:**
```bash
# Check readiness endpoint
curl http://localhost:8084/actuator/health/readiness

# Check service endpoints
kubectl get endpoints events-connector
```

**Solutions:**
- Verify downstream services (Subscription Service, Queue Service) are healthy
- Check custom health indicators
- Review application logs for initialization issues
- Ensure database/cache connections are established

### Debugging Tips

1. **Enable Debug Logging:**
   ```bash
   kubectl set env deployment/events-connector LOGGING_LEVEL_ORG_SIFUNITY_EVENTSCONNECTOR=DEBUG
   ```

2. **Check Correlation IDs:**
   - All logs include correlation IDs for request tracing
   - Use correlation ID to trace request across services

3. **Review Metrics:**
   ```bash
   # All metrics
   curl http://localhost:8084/actuator/metrics
   
   # Specific metric
   curl http://localhost:8084/actuator/metrics/events.received.count
   ```

4. **Port Forward for Local Access:**
   ```bash
   kubectl port-forward service/events-connector 8084:8084
   ```

5. **Exec into Pod:**
   ```bash
   kubectl exec -it <pod-name> -- /bin/sh
   ```

6. **Check Environment Variables:**
   ```bash
   kubectl exec <pod-name> -- env | grep -i service
   ```

### Getting Help

If issues persist:

1. **Collect Information:**
   - Service logs (last 1000 lines)
   - Health check output
   - Metrics snapshot
   - Recent events: `kubectl get events --sort-by=.metadata.creationTimestamp`

2. **Check Documentation:**
   - [API Documentation](API-DOCUMENTATION.md)
   - [Kubernetes README](k8s/README.md)
   - Design document: `.kiro/specs/events-connector/design.md`

3. **Contact Support:**
   - Include correlation IDs from logs
   - Provide request/response examples
   - Share relevant metrics and health check output

## Development

### Project Structure

```
events-connector/
├── src/
│   ├── main/
│   │   ├── java/org/sifunity/eventsconnector/
│   │   │   ├── client/          # HTTP clients for external services
│   │   │   ├── config/          # Configuration classes
│   │   │   ├── controller/      # REST API controllers
│   │   │   ├── dto/             # Data Transfer Objects
│   │   │   ├── exception/       # Custom exceptions and handlers
│   │   │   ├── service/         # Business logic services
│   │   │   └── util/            # Utility classes
│   │   └── resources/
│   │       └── application.yml  # Application configuration
│   └── test/
│       ├── java/org/sifunity/eventsconnector/
│       │   ├── unit/            # Unit tests
│       │   ├── property/        # Property-based tests
│       │   └── integration/     # Integration tests
│       └── resources/
│           └── application-test.yml
├── pom.xml
└── README.md
```

### Code Style

- Follow standard Java naming conventions
- Use Lombok to reduce boilerplate
- Write comprehensive JavaDoc for public APIs
- Use meaningful variable and method names
- Keep methods focused and small

## License

This project is licensed under the MIT License. See [LICENSE](../LICENSE) for details.
