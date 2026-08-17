# API Gateway

The API Gateway is the single entry point for all external requests to the SIF Unity Broker. Built on Spring Cloud Gateway, it provides centralized authentication, authorization, and routing to backend microservices.

## Overview

The Gateway is the **ONLY** component in the system with authentication and authorization logic, ensuring consistent security enforcement across all services. It has **NO** direct database access and **NO** business logic beyond authentication, authorization, and routing.

### Key Responsibilities

1. **Authentication**: Validate sessionToken and HMAC signatures via Environment Service
2. **Authorization**: Check ACL permissions via Environment Service
3. **Context Injection**: Add environment context headers (X-Environment-Id, X-Environment-Name, X-Correlation-Id)
4. **Routing**: Route requests to appropriate backend services based on path
5. **Rate Limiting**: Enforce request rate limits per environment
6. **Error Handling**: Provide consistent error responses
7. **Observability**: Log all auth/authz decisions and track metrics

## Architecture

### Filter Chain

The Gateway uses a filter chain pattern with the following filters (in order):

1. **CorrelationIdFilter** (Order 1): Generates or preserves correlation IDs for request tracing
2. **AuthenticationFilter** (Order 2): Validates sessionToken and HMAC signatures via Environment Service
3. **AuthorizationFilter** (Order 3): Checks ACL permissions via Environment Service
4. **EnvironmentContextFilter** (Order 4): Injects environment context headers for backend services
5. **RateLimitFilter** (Order 5): Enforces request rate limits per environment

### Routing Rules

| Path Prefix | Target Service | Auth Required | Description |
|-------------|---------------|---------------|-------------|
| `/environments` | Environment Service | No (POST only) | Environment registration |
| `/acl-entries` | Environment Service | Yes | ACL management |
| `/providers` | Environment Service | Yes | Provider registry |
| `/zones` | Environment Service | Yes | Zone/context management |
| `/requests` | Requests Connector | Yes | Request routing |
| `/responses` | Responses Connector | Yes | Response routing |
| `/events` | Events Connector | Yes | Event distribution |
| `/services` | Services Connector | Yes | Job/service management |
| `/queues` | Queue Service | Yes | Queue management |
| `/subscriptions` | Subscription Service | Yes | Subscription management |
| `/jobs` | Job Service | Yes | Job state management |
| `/actuator/health` | All Services | No | Health checks |
| `/internal/*` | BLOCKED | N/A | Internal APIs not exposed |

## Technology Stack

- **Framework**: Spring Cloud Gateway (reactive, non-blocking)
- **Language**: Java 17+ (Amazon Corretto)
- **Reactive Stack**: Spring WebFlux (Project Reactor)
- **HTTP Client**: WebClient (reactive HTTP client for Environment Service calls)
- **Shared Library**: sif-broker-common (DTOs, exceptions, utilities)
- **Rate Limiting**: Redis (distributed rate limiting)
- **Testing**: JUnit 5, jqwik (property-based testing), WebTestClient

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `ENVIRONMENT_SERVICE_URL` | URL of the Environment Service | `http://localhost:8081` |
| `REDIS_HOST` | Redis host for rate limiting | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `REDIS_PASSWORD` | Redis password | (empty) |

### Rate Limits

Rate limits are configured per environment type in `application.yml`:

```yaml
rate-limit:
  requests-per-second:
    CONSUMER: 100
    PROVIDER: 100
    BROKER: 200
```

## Running Locally

### Prerequisites

- Java 17+
- Maven 3.8+
- Redis (for rate limiting)
- Environment Service running on port 8081

### Build

```bash
./mvnw clean package
```

### Run

```bash
./mvnw spring-boot:run
```

The Gateway will start on port 8080.

### Run with Docker Compose

```bash
docker-compose up
```

## Testing

### Run All Tests

```bash
./mvnw test
```

### Run Unit Tests Only

```bash
./mvnw test -Dtest="*Test"
```

### Run Property-Based Tests Only

```bash
./mvnw test -Dtest="*Properties"
```

### Run Integration Tests Only

```bash
./mvnw test -Dtest="*IntegrationTest"
```

## API Endpoints

### Health Check

```bash
GET /actuator/health
```

Returns the health status of the Gateway and its dependencies.

### Metrics

```bash
GET /actuator/metrics
```

Returns Prometheus-compatible metrics including:
- Request count per route
- Authentication success/failure rate
- Authorization success/failure rate
- Response times per route
- Rate limiting events

## Development

### Project Structure

```
gateway/
├── src/
│   ├── main/
│   │   ├── java/org/sifunity/gateway/
│   │   │   ├── filter/          # Gateway filters
│   │   │   ├── client/          # HTTP clients
│   │   │   ├── config/          # Configuration classes
│   │   │   ├── exception/       # Custom exceptions
│   │   │   └── GatewayApplication.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── logback-spring.xml
│   └── test/
│       ├── java/org/sifunity/gateway/
│       │   ├── filter/          # Filter unit tests
│       │   ├── client/          # Client unit tests
│       │   ├── config/          # Configuration tests
│       │   ├── property/        # Property-based tests
│       │   ├── integration/     # Integration tests
│       │   └── util/            # Test utilities
│       └── resources/
│           └── application-test.yml
├── pom.xml
└── README.md
```

### Adding a New Filter

1. Create a new filter class in `src/main/java/org/sifunity/gateway/filter/`
2. Implement `GlobalFilter` interface
3. Add `@Component` and `@Order(n)` annotations
4. Implement the `filter()` method
5. Write unit tests in `src/test/java/org/sifunity/gateway/filter/`

### Adding a New Route

1. Update `GatewayRouteConfiguration` in `src/main/java/org/sifunity/gateway/config/`
2. Add a new route using the `RouteLocatorBuilder`
3. Write tests in `src/test/java/org/sifunity/gateway/config/`

## Monitoring and Observability

### Logging

All log entries include the correlation ID for request tracing:

```
2024-01-15 10:30:45 [reactor-http-nio-2] INFO  o.s.g.filter.AuthenticationFilter [abc-123-def] - Authentication successful for environment: env-456
```

### Metrics

The Gateway exposes Prometheus metrics at `/actuator/metrics` including:
- `gateway.requests.total` - Total request count per route
- `gateway.auth.success` - Authentication success count
- `gateway.auth.failure` - Authentication failure count
- `gateway.authz.success` - Authorization success count
- `gateway.authz.failure` - Authorization failure count
- `gateway.ratelimit.exceeded` - Rate limit exceeded count

### Distributed Tracing

The Gateway propagates correlation IDs to all backend services via the `X-Correlation-Id` header, enabling distributed tracing across the entire system.

## Security

### Authentication

The Gateway validates all incoming requests using sessionToken and HMAC signatures. Authentication is delegated to the Environment Service, which:
1. Looks up the environment by sessionToken
2. Computes the HMAC signature using the sharedSecret
3. Validates the timestamp is within tolerance (5 minutes)
4. Returns environment details if valid

### Authorization

After authentication, the Gateway checks ACL permissions by calling the Environment Service, which:
1. Queries the ACL table for the environment
2. Checks if the environment has permission for the requested service, zone, context, and access type
3. Returns permission granted/denied

### Internal API Protection

The Gateway blocks all requests to `/internal/*` paths, preventing external access to internal APIs. Internal APIs are only accessible within the cluster.

## Error Handling

All error responses use the SIF-compliant error format:

```json
{
  "code": "AUTH_001",
  "message": "Invalid token",
  "scope": "BROKER",
  "timestamp": "2024-01-15T10:30:45Z"
}
```

### Error Codes

- `AUTH_001`: Invalid token
- `AUTH_002`: Invalid timestamp
- `AUTH_003`: Malformed authorization header
- `AUTHZ_001`: Insufficient permissions
- `AUTHZ_002`: Zone access denied
- `SERVICE_002`: Service unavailable
- `RATE_001`: Rate limit exceeded

## License

This project is licensed under the MIT License. See [LICENSE](../LICENSE) for details.


## Deployment

### Docker

Build and run the Gateway using Docker:

```bash
# Build the Docker image
docker build -t sif-unity/gateway:latest .

# Run the container
docker run -d \
  -p 8080:8080 \
  -e REDIS_HOST=redis \
  -e REDIS_PORT=6379 \
  -e ENVIRONMENT_SERVICE_URL=http://environment-service:8081 \
  --name api-gateway \
  sif-unity/gateway:latest

# View logs
docker logs -f api-gateway

# Stop and remove container
docker stop api-gateway
docker rm api-gateway
```

### Docker Compose

For local development with all dependencies:

```bash
# Start all services (Gateway + Redis)
docker-compose up -d

# View logs
docker-compose logs -f gateway

# Stop all services
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

### Kubernetes

Example Kubernetes deployment:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: gateway
  namespace: sif-unity
spec:
  replicas: 3
  selector:
    matchLabels:
      app: gateway
  template:
    metadata:
      labels:
        app: gateway
    spec:
      containers:
      - name: gateway
        image: sif-unity/gateway:latest
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: REDIS_HOST
          value: "redis-service"
        - name: REDIS_PORT
          value: "6379"
        - name: ENVIRONMENT_SERVICE_URL
          value: "http://environment-service:8081"
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 40
          periodSeconds: 30
          timeoutSeconds: 3
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 3
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
---
apiVersion: v1
kind: Service
metadata:
  name: gateway
  namespace: sif-unity
spec:
  type: LoadBalancer
  selector:
    app: gateway
  ports:
  - port: 80
    targetPort: 8080
    protocol: TCP
    name: http
```

## Troubleshooting

### Gateway returns 503 Service Unavailable

**Symptoms**: Requests fail with HTTP 503 and error code `SERVICE_002`

**Possible Causes**:
- Environment Service is not running or not accessible
- Network connectivity issues between Gateway and Environment Service
- Environment Service is overloaded or unhealthy

**Solutions**:
1. Check Environment Service health:
   ```bash
   curl http://localhost:8081/actuator/health
   ```
2. Verify the `ENVIRONMENT_SERVICE_URL` environment variable
3. Check Gateway logs for connection errors:
   ```bash
   docker logs api-gateway | grep "Environment Service"
   ```
4. Verify network connectivity (if using Docker, check network configuration)

### Rate limiting not working

**Symptoms**: Requests are not being rate limited even when exceeding configured limits

**Possible Causes**:
- Redis is not running or not accessible
- Redis connection settings are incorrect
- Rate limit configuration is not loaded properly

**Solutions**:
1. Verify Redis is running:
   ```bash
   docker ps | grep redis
   redis-cli ping  # Should return PONG
   ```
2. Check Redis connection in Gateway logs:
   ```bash
   docker logs api-gateway | grep -i redis
   ```
3. Verify rate limit configuration:
   ```bash
   curl http://localhost:8080/actuator/configprops | jq '.["rate-limit"]'
   ```
4. Test Redis connectivity from Gateway container:
   ```bash
   docker exec api-gateway sh -c "nc -zv redis 6379"
   ```

### Authentication always fails

**Symptoms**: All requests return HTTP 401 with error code `AUTH_001`

**Possible Causes**:
- Environment Service is not running or not accessible
- Authorization header format is incorrect
- sessionToken is invalid or expired
- HMAC signature is incorrect
- Timestamp is outside tolerance window (5 minutes)

**Solutions**:
1. Verify Environment Service is running and accessible
2. Check Authorization header format:
   ```
   Authorization: SIF_HMACSHA256 {sessionToken}:{timestamp}:{hash}
   ```
3. Verify the sessionToken exists in the Environment Service database
4. Check Gateway logs for authentication errors:
   ```bash
   docker logs api-gateway | grep "Authentication failed"
   ```
5. Verify the timestamp is within the tolerance window (current time ± 5 minutes)
6. Test with a known-good sessionToken and HMAC signature

### High memory usage

**Symptoms**: Gateway container uses excessive memory or runs out of memory

**Possible Causes**:
- JVM heap size is too large for container
- Memory leak in application code
- Too many concurrent requests
- Connection pool exhaustion

**Solutions**:
1. Adjust JVM memory settings in Dockerfile:
   ```dockerfile
   ENV JAVA_OPTS="-XX:MaxRAMPercentage=50.0 -XX:InitialRAMPercentage=25.0"
   ```
2. Monitor memory usage:
   ```bash
   docker stats api-gateway
   ```
3. Check for memory leaks using JVM profiling tools
4. Review rate limiting configuration to prevent overload
5. Increase container memory limits in Kubernetes/Docker

### Correlation IDs not appearing in logs

**Symptoms**: Log entries don't include correlation IDs in the expected format

**Possible Causes**:
- Logging pattern is not configured correctly
- MDC context is not being set properly
- CorrelationIdFilter is not running

**Solutions**:
1. Verify logging pattern in `application.yml`:
   ```yaml
   logging:
     pattern:
       console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} [%X{correlationId}] - %msg%n"
   ```
2. Check that CorrelationIdFilter is registered and running (Order 1)
3. Verify LoggingUtils is setting MDC correctly
4. Check Gateway logs for filter initialization messages

### Internal APIs are accessible

**Symptoms**: Requests to `/internal/*` paths are not being blocked

**Possible Causes**:
- Route configuration is incorrect
- Internal API blocking route is not registered
- Route order is incorrect

**Solutions**:
1. Verify the internal API blocking route in `GatewayRouteConfiguration`:
   ```java
   .route("block-internal", r -> r
       .path("/internal/**")
       .filters(f -> f.setStatus(HttpStatus.FORBIDDEN))
       .uri("no://op"))
   ```
2. Check Gateway logs for route registration messages
3. Test internal API blocking:
   ```bash
   curl -X POST http://localhost:8080/internal/validate-token
   # Should return 403 Forbidden
   ```

## Performance Tuning

### Connection Pooling

The Gateway uses WebClient with connection pooling for Environment Service calls. Adjust pool settings if needed:

```java
@Bean
public WebClient.Builder webClientBuilder() {
    HttpClient httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
        .responseTimeout(Duration.ofSeconds(5))
        .option(ChannelOption.SO_KEEPALIVE, true)
        .option(ChannelOption.TCP_NODELAY, true);
    
    return WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient));
}
```

### Rate Limiting

Adjust rate limits based on your environment capacity and expected load:

```yaml
rate-limit:
  requests-per-second:
    CONSUMER: 200  # Increase for higher throughput
    PROVIDER: 200
    BROKER: 400
```

### Caching (Future Enhancement)

Consider implementing caching for:
- Token validation results (short TTL, e.g., 30 seconds)
- ACL permission checks (short TTL, e.g., 30 seconds)

**Note**: Caching is not currently implemented but can be added for performance optimization. Ensure cache invalidation on environment deletion or ACL changes.

### JVM Tuning

For production deployments, tune JVM settings based on container resources:

```dockerfile
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=50.0 \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -XX:+ParallelRefProcEnabled"
```

## Testing Strategy

The Gateway uses a comprehensive testing approach with three types of tests:

### Unit Tests

Test individual components in isolation with mocked dependencies:
- Filter tests (AuthenticationFilter, AuthorizationFilter, etc.)
- Client tests (EnvironmentServiceClient)
- Configuration tests (GatewayRouteConfiguration, RateLimitConfiguration)

### Property-Based Tests

Test universal properties across randomized inputs using jqwik:
- Authentication properties (header parsing, token validation)
- Authorization properties (access type determination, permission checking)
- Context injection properties (header injection, correlation ID handling)
- Routing properties (route matching, internal API blocking)
- Error handling properties (SIF error format, logging)
- Rate limiting properties (per-environment tracking, type-based limits)

Each property test runs a minimum of 100 iterations with randomized inputs.

### Integration Tests

Test end-to-end flows with WireMock for Environment Service:
- Full authentication flow (correlation ID → auth → authz → context → routing)
- Authentication exemptions (POST /environments, /actuator/health)
- Error scenarios (503, 504, 401, 403, 429)
- Internal API blocking
- Rate limiting

## Contributing

When contributing to the Gateway:

1. **Follow existing code style**: Use the same patterns and conventions
2. **Write comprehensive tests**: Include unit, property-based, and integration tests
3. **Update documentation**: Update this README if adding new features
4. **Ensure all tests pass**: Run `mvn test` before submitting PR
5. **Add Javadoc comments**: Document all public methods and classes
6. **Reference requirements**: Include requirement references in test comments
7. **Use meaningful commit messages**: Follow conventional commit format

### Code Style

- Use 4 spaces for indentation (no tabs)
- Maximum line length: 120 characters
- Use descriptive variable and method names
- Add comments for complex logic
- Follow Spring Boot and Spring Cloud Gateway best practices

### Pull Request Process

1. Create a feature branch from `main`
2. Make your changes with appropriate tests
3. Ensure all tests pass: `mvn clean test`
4. Update documentation if needed
5. Submit PR with clear description of changes
6. Address review comments
7. Squash commits before merging

## Additional Resources

- [Spring Cloud Gateway Documentation](https://spring.io/projects/spring-cloud-gateway)
- [Spring WebFlux Documentation](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Project Reactor Documentation](https://projectreactor.io/docs)
- [jqwik Property-Based Testing](https://jqwik.net/)
- [SIF Specification](https://specification.sifassociation.org/)

## Support

For issues, questions, or contributions:
- Create an issue in the project repository
- Contact the SIF Unity Broker development team
- Refer to the project wiki for additional documentation
