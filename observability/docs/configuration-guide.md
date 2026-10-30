# Observability Platform Configuration Guide

## Table of Contents

1. [Overview](#overview)
2. [Configuration Properties](#configuration-properties)
3. [Environment-Specific Configuration](#environment-specific-configuration)
4. [Environment Variable Overrides](#environment-variable-overrides)
5. [Configuration Examples](#configuration-examples)
6. [Validation and Troubleshooting](#validation-and-troubleshooting)

## Overview

The SIF Unity Broker observability platform provides comprehensive distributed tracing, centralized logging, and metrics collection across all 9 microservices. This guide documents all configuration properties and provides examples for different deployment environments.

### Configuration Files

- **observability-defaults.yml**: Default configuration with sensible defaults for all environments
- **logback-spring.xml**: Logging configuration for structured JSON logs
- **application.yml**: Service-specific configuration that imports observability defaults

### Configuration Hierarchy

Configuration is loaded in the following order (later sources override earlier ones):

1. `observability-defaults.yml` (lowest priority)
2. `application.yml` service-specific settings
3. Environment variables (highest priority)

## Configuration Properties

### Distributed Tracing Configuration

#### Core Tracing Settings

```yaml
observability:
  enabled: true                    # Master switch for all observability features
  tracing:
    enabled: true                  # Enable/disable distributed tracing
    jaeger:
      endpoint: http://localhost:14250  # Jaeger collector gRPC endpoint
      timeout: 10                  # Span export timeout (seconds)
      max-queue-size: 2048         # Maximum spans to buffer when Jaeger unavailable
      max-export-batch-size: 512   # Maximum spans per export batch
      schedule-delay: 5            # Batch export delay (seconds)
```

**Property Descriptions:**

- **observability.enabled**: Master switch. When `false`, returns no-op implementations to avoid overhead
- **tracing.enabled**: Controls distributed tracing. Can be disabled independently of other features
- **jaeger.endpoint**: Jaeger collector endpoint. Format: `http://hostname:port` or `https://hostname:port`
- **jaeger.timeout**: Maximum time to wait for span export before timing out
- **jaeger.max-queue-size**: Buffer size for spans when Jaeger is unavailable. Oldest spans are dropped when full
- **jaeger.max-export-batch-size**: Number of spans sent in each batch to optimize network usage
- **jaeger.schedule-delay**: How often to export batched spans

#### Sampling Configuration

```yaml
observability:
  tracing:
    sampling:
      probability: 1.0             # Sampling rate: 0.0 (0%) to 1.0 (100%)
```

**Sampling Guidelines:**

| Environment | Recommended Rate | Rationale |
|-------------|-----------------|-----------|
| Development | 1.0 (100%) | Complete visibility for debugging |
| Test | 1.0 (100%) | Full trace coverage for integration tests |
| Staging | 0.5 (50%) | Balance between visibility and storage |
| Production (Low Traffic) | 0.1-0.2 (10-20%) | Sufficient sampling for debugging |
| Production (High Traffic) | 0.01-0.05 (1-5%) | Minimize storage and performance impact |

**Performance Impact:**
- 100% sampling: ~3-5% latency overhead
- 10% sampling: ~0.5-1% latency overhead
- 1% sampling: ~0.1-0.2% latency overhead

#### Retry Configuration

```yaml
observability:
  tracing:
    jaeger:
      retry:
        enabled: true              # Enable retry on export failure
        initial-delay: 1           # Initial retry delay (seconds)
        max-delay: 60              # Maximum retry delay (seconds)
        multiplier: 2.0            # Exponential backoff multiplier
        max-attempts: 5            # Maximum retry attempts
```

**Retry Behavior:**
- Failed exports trigger exponential backoff: 1s, 2s, 4s, 8s, 16s, 32s, 60s (capped)
- After max attempts, spans are dropped and a warning is logged
- Service continues operating normally during Jaeger outages

#### Trace Context Propagation

```yaml
observability:
  tracing:
    propagation:
      format: w3c                  # Propagation format: w3c, b3, or jaeger
      include-baggage: true        # Include baggage in propagation
```

**Supported Formats:**
- **w3c**: W3C Trace Context standard (recommended)
- **b3**: Zipkin B3 format (for compatibility)
- **jaeger**: Jaeger native format

### Centralized Logging Configuration

#### Logstash Connection

```yaml
observability:
  logging:
    logstash:
      host: localhost              # Logstash server hostname
      port: 5000                   # Logstash TCP port
      connection-timeout: 5000     # Connection timeout (milliseconds)
      retry:
        enabled: true              # Enable retry on connection failure
        initial-delay: 1000        # Initial retry delay (milliseconds)
        max-delay: 60000           # Maximum retry delay (milliseconds)
        multiplier: 2.0            # Exponential backoff multiplier
```

**Connection Behavior:**
- Logs are sent to Logstash via TCP connection
- Failed connections trigger exponential backoff
- Logs are buffered locally during Logstash outages
- Service continues operating normally

#### Async Logging

```yaml
observability:
  logging:
    async:
      queue-size: 512              # Async logging queue size
      discarding-threshold: 0      # Threshold for discarding logs (0 = never discard)
      include-caller-data: false   # Include caller class/method (expensive)
```

**Queue Behavior:**
- Logs are written asynchronously to avoid blocking request threads
- When queue is full and `discarding-threshold` is 0, oldest logs are dropped
- `include-caller-data` adds overhead; disable in production

#### Sensitive Data Masking

```yaml
observability:
  logging:
    sensitive-fields:
      - password
      - token
      - authorization
      - secret
      - apiKey
      - api_key
      - sessionId
      - session_id
      - creditCard
      - credit_card
      - ssn
      - socialSecurityNumber
```

**Masking Behavior:**
- Fields matching these names have values replaced with `[REDACTED]`
- PII patterns (email, SSN, credit card) are automatically masked
- Masking occurs before logs are written to any destination

#### JSON Logging

```yaml
observability:
  logging:
    json:
      enabled: true                # Enable JSON-formatted logs
      include-mdc: true            # Include MDC (trace IDs, correlation IDs)
      include-stack-trace: true    # Include exception stack traces
      timestamp-format: "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"  # ISO 8601 format
```

**JSON Log Structure:**
```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "service": "queue-service",
  "environment": "production",
  "level": "INFO",
  "logger": "org.sifunity.broker.queue.QueueService",
  "thread": "http-nio-8080-exec-1",
  "message": "Processing message from queue",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span_id": "00f067aa0ba902b7",
  "correlation_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Metrics Configuration

#### Core Metrics Settings

```yaml
observability:
  metrics:
    enabled: true                  # Enable metrics collection
    cardinality:
      limit: 100                   # Maximum unique tag combinations per metric
      action: replace-with-other   # Action when limit exceeded: drop or replace-with-other
    prefixes:
      business: "sif.business"     # Prefix for business metrics
      queue: "sif.queue"           # Prefix for queue metrics
      cache: "sif.cache"           # Prefix for cache metrics
      database: "sif.database"     # Prefix for database metrics
```

**Cardinality Management:**
- High cardinality can cause memory issues and slow queries
- When limit is exceeded, tag values are replaced with "other"
- Monitor cardinality with `micrometer_cardinality_exceeded_total` metric

#### Spring Boot Actuator

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics  # Exposed endpoints
      base-path: /actuator         # Base path for actuator endpoints
  endpoint:
    health:
      show-details: when-authorized  # Show detailed health info
      show-components: when-authorized  # Show component health
    prometheus:
      enabled: true                # Enable Prometheus endpoint
  metrics:
    export:
      prometheus:
        enabled: true              # Enable Prometheus export
        step: 1m                   # Metrics publication interval
```

**Actuator Endpoints:**
- `/actuator/health`: Service health status
- `/actuator/info`: Service information
- `/actuator/prometheus`: Prometheus metrics in text format
- `/actuator/metrics`: Available metrics list

#### Histogram Configuration

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true  # Enable histogram for HTTP requests
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99  # Calculate these percentiles
      slo:
        http.server.requests: 10ms,50ms,100ms,200ms,500ms,1s,2s,5s  # SLO boundaries
```

**Histogram Metrics:**
- Enables calculation of percentiles (p50, p95, p99)
- SLO boundaries create buckets for latency analysis
- Useful for identifying slow requests and performance degradation

#### Metric Tags

```yaml
management:
  metrics:
    tags:
      application: ${spring.application.name}  # Service name tag
      environment: ${spring.profiles.active:default}  # Environment tag
```

**Common Tags:**
- Applied to all metrics from the service
- Useful for filtering and grouping in Prometheus/Grafana
- Keep tag cardinality low to avoid performance issues

#### JVM and System Metrics

```yaml
management:
  metrics:
    enable:
      jvm: true                    # JVM metrics (heap, GC, threads)
      process: true                # Process metrics (CPU, file descriptors)
      system: true                 # System metrics (CPU, disk)
      tomcat: true                 # Tomcat metrics (connections, threads)
      logback: true                # Logback metrics (log events)
      jdbc: true                   # JDBC metrics (connections, queries)
```

**Enabled Metrics:**
- **jvm**: Heap usage, GC pause time, thread count
- **process**: CPU usage, file descriptors, uptime
- **system**: System CPU, disk space
- **tomcat**: Active connections, thread pool usage
- **logback**: Log events by level
- **jdbc**: Connection pool usage, query execution time

### Log Levels

```yaml
logging:
  level:
    root: INFO                     # Default log level
    org.sifunity: DEBUG            # SIF Unity Broker services
    org.springframework.web: INFO  # Spring Web
    org.springframework.data: INFO  # Spring Data
    org.hibernate: WARN            # Hibernate
    io.opentelemetry: INFO         # OpenTelemetry
    io.micrometer: INFO            # Micrometer
    io.lettuce: INFO               # Lettuce (Redis client)
```

**Log Level Guidelines:**

| Environment | Root Level | SIF Services | Spring Framework |
|-------------|-----------|--------------|------------------|
| Development | DEBUG | DEBUG | DEBUG |
| Test | INFO | DEBUG | INFO |
| Staging | INFO | INFO | INFO |
| Production | WARN | INFO | WARN |

## Environment-Specific Configuration

### Development Environment

**File**: `application-dev.yml`

```yaml
spring:
  config:
    import: classpath:observability-defaults.yml

observability:
  tracing:
    sampling:
      probability: 1.0             # 100% sampling for complete visibility
    jaeger:
      endpoint: http://localhost:14250  # Local Jaeger instance

logging:
  level:
    root: DEBUG
    org.sifunity: DEBUG
    org.springframework.web: DEBUG

management:
  endpoints:
    web:
      exposure:
        include: "*"               # Expose all actuator endpoints
```

**Characteristics:**
- Complete trace sampling for debugging
- Verbose logging for development
- All actuator endpoints exposed
- Local observability stack

### Test Environment

**File**: `application-test.yml`

```yaml
spring:
  config:
    import: classpath:observability-defaults.yml

observability:
  tracing:
    sampling:
      probability: 1.0             # 100% sampling for test coverage
    jaeger:
      endpoint: http://jaeger-test:14250  # Test infrastructure

logging:
  level:
    root: INFO
    org.sifunity: DEBUG

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
```

**Characteristics:**
- Complete trace sampling for integration tests
- Balanced logging (INFO/DEBUG)
- Essential actuator endpoints
- Test infrastructure endpoints

### Production Environment

**File**: `application-prod.yml`

```yaml
spring:
  config:
    import: classpath:observability-defaults.yml

observability:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_RATE:0.1}  # 10% default, configurable
    jaeger:
      endpoint: ${JAEGER_ENDPOINT:http://jaeger-collector:14250}
  logging:
    logstash:
      host: ${LOGSTASH_HOST:logstash}
      port: ${LOGSTASH_PORT:5000}
    async:
      include-caller-data: false   # Disable for performance

logging:
  level:
    root: WARN
    org.sifunity: INFO
    org.springframework: WARN

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus  # Only essential endpoints
  endpoint:
    health:
      show-details: never          # Hide details for security
```

**Characteristics:**
- Low sampling rate (10% default)
- Minimal logging (WARN/INFO)
- Only essential endpoints exposed
- Production-grade security
- Environment variable configuration

## Environment Variable Overrides

All configuration properties can be overridden using environment variables. This is the recommended approach for production deployments.

### Complete Environment Variable Reference

| Environment Variable | Configuration Property | Default | Description |
|---------------------|----------------------|---------|-------------|
| `OBSERVABILITY_ENABLED` | `observability.enabled` | `true` | Master switch for observability |
| `TRACING_ENABLED` | `observability.tracing.enabled` | `true` | Enable distributed tracing |
| `JAEGER_ENDPOINT` | `observability.tracing.jaeger.endpoint` | `http://localhost:14250` | Jaeger collector endpoint |
| `TRACING_SAMPLING_RATE` | `observability.tracing.sampling.probability` | `1.0` | Sampling rate (0.0-1.0) |
| `JAEGER_TIMEOUT` | `observability.tracing.jaeger.timeout` | `10` | Span export timeout (seconds) |
| `JAEGER_MAX_QUEUE_SIZE` | `observability.tracing.jaeger.max-queue-size` | `2048` | Maximum spans to buffer |
| `JAEGER_MAX_EXPORT_BATCH_SIZE` | `observability.tracing.jaeger.max-export-batch-size` | `512` | Maximum spans per batch |
| `JAEGER_SCHEDULE_DELAY` | `observability.tracing.jaeger.schedule-delay` | `5` | Batch export delay (seconds) |
| `LOGSTASH_HOST` | `observability.logging.logstash.host` | `localhost` | Logstash server hostname |
| `LOGSTASH_PORT` | `observability.logging.logstash.port` | `5000` | Logstash TCP port |
| `LOGGING_ASYNC_QUEUE_SIZE` | `observability.logging.async.queue-size` | `512` | Async logging queue size |
| `LOGGING_ASYNC_DISCARDING_THRESHOLD` | `observability.logging.async.discarding-threshold` | `0` | Log discarding threshold |
| `METRICS_ENABLED` | `observability.metrics.enabled` | `true` | Enable metrics collection |
| `METRICS_CARDINALITY_LIMIT` | `observability.metrics.cardinality.limit` | `100` | Max unique tag combinations |
| `SPRING_PROFILES_ACTIVE` | `spring.profiles.active` | `default` | Active Spring profile |

### Setting Environment Variables

#### Docker Compose

```yaml
services:
  queue-service:
    image: sif-unity-broker/queue-service:latest
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - JAEGER_ENDPOINT=http://jaeger:14250
      - TRACING_SAMPLING_RATE=0.1
      - LOGSTASH_HOST=logstash
      - LOGSTASH_PORT=5000
      - METRICS_CARDINALITY_LIMIT=100
```

#### Kubernetes ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: observability-config
  namespace: sif-unity-broker
data:
  SPRING_PROFILES_ACTIVE: "prod"
  JAEGER_ENDPOINT: "http://jaeger-collector.observability.svc.cluster.local:14250"
  TRACING_SAMPLING_RATE: "0.1"
  LOGSTASH_HOST: "logstash.observability.svc.cluster.local"
  LOGSTASH_PORT: "5000"
  METRICS_CARDINALITY_LIMIT: "100"
```

#### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: queue-service
spec:
  template:
    spec:
      containers:
      - name: queue-service
        image: sif-unity-broker/queue-service:latest
        envFrom:
        - configMapRef:
            name: observability-config
```

#### Shell Export

```bash
export SPRING_PROFILES_ACTIVE=prod
export JAEGER_ENDPOINT=http://jaeger:14250
export TRACING_SAMPLING_RATE=0.1
export LOGSTASH_HOST=logstash
export LOGSTASH_PORT=5000
```

## Configuration Examples

### Example 1: Local Development

**Scenario**: Developer running services locally with Docker Compose observability stack

**Configuration**:

```yaml
# application.yml
spring:
  application:
    name: queue-service
  profiles:
    active: dev
  config:
    import: classpath:observability-defaults.yml

# No additional configuration needed - defaults work for local development
```

**Environment Variables** (optional):

```bash
# Use defaults or override specific values
export TRACING_SAMPLING_RATE=1.0
export JAEGER_ENDPOINT=http://localhost:14250
export LOGSTASH_HOST=localhost
export LOGSTASH_PORT=5000
```

**Result**:
- 100% trace sampling
- All traces sent to local Jaeger (localhost:14250)
- All logs sent to local Logstash (localhost:5000)
- All metrics exposed at `/actuator/prometheus`

### Example 2: Integration Testing

**Scenario**: CI/CD pipeline running integration tests with test infrastructure

**Configuration**:

```yaml
# application-test.yml
spring:
  config:
    import: classpath:observability-defaults.yml

observability:
  tracing:
    sampling:
      probability: 1.0
    jaeger:
      endpoint: http://jaeger-test:14250
  logging:
    logstash:
      host: logstash-test
      port: 5000
```

**Environment Variables**:

```bash
export SPRING_PROFILES_ACTIVE=test
export JAEGER_ENDPOINT=http://jaeger-test:14250
export LOGSTASH_HOST=logstash-test
```

**Result**:
- 100% trace sampling for complete test coverage
- Traces sent to test Jaeger instance
- Logs sent to test Logstash instance
- All integration test traces captured

### Example 3: Production Deployment (Low Traffic)

**Scenario**: Production deployment with moderate traffic (~1000 req/min)

**Configuration**:

```yaml
# application-prod.yml
spring:
  config:
    import: classpath:observability-defaults.yml

observability:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_RATE:0.2}  # 20% sampling
```

**Environment Variables**:

```bash
export SPRING_PROFILES_ACTIVE=prod
export JAEGER_ENDPOINT=http://jaeger-collector.observability.svc.cluster.local:14250
export TRACING_SAMPLING_RATE=0.2
export LOGSTASH_HOST=logstash.observability.svc.cluster.local
export LOGSTASH_PORT=5000
```

**Result**:
- 20% trace sampling (200 traces/min)
- Sufficient sampling for debugging
- Moderate storage usage
- ~1% latency overhead

### Example 4: Production Deployment (High Traffic)

**Scenario**: Production deployment with high traffic (~100,000 req/min)

**Configuration**:

```yaml
# application-prod.yml
spring:
  config:
    import: classpath:observability-defaults.yml

observability:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_RATE:0.01}  # 1% sampling
    jaeger:
      max-queue-size: 4096         # Larger buffer for high traffic
      max-export-batch-size: 1024  # Larger batches
```

**Environment Variables**:

```bash
export SPRING_PROFILES_ACTIVE=prod
export JAEGER_ENDPOINT=http://jaeger-collector.observability.svc.cluster.local:14250
export TRACING_SAMPLING_RATE=0.01
export JAEGER_MAX_QUEUE_SIZE=4096
export JAEGER_MAX_EXPORT_BATCH_SIZE=1024
export LOGSTASH_HOST=logstash.observability.svc.cluster.local
export LOGSTASH_PORT=5000
```

**Result**:
- 1% trace sampling (1000 traces/min)
- Minimal storage usage
- ~0.1% latency overhead
- Larger buffers handle traffic spikes

### Example 5: Temporary Tracing Disable

**Scenario**: Temporarily disable tracing during incident to reduce load

**Environment Variables**:

```bash
# Disable tracing without redeploying
kubectl set env deployment/queue-service TRACING_ENABLED=false

# Or disable all observability
kubectl set env deployment/queue-service OBSERVABILITY_ENABLED=false
```

**Result**:
- Tracing immediately disabled
- Service continues operating normally
- No code changes or redeployment required
- Can be re-enabled quickly

## Validation and Troubleshooting

### Validating Configuration on Startup

Add a configuration validator to your service:

```java
@Component
@Slf4j
public class ObservabilityConfigValidator {
    
    @Value("${observability.enabled}")
    private boolean observabilityEnabled;
    
    @Value("${observability.tracing.enabled}")
    private boolean tracingEnabled;
    
    @Value("${observability.tracing.sampling.probability}")
    private double samplingRate;
    
    @Value("${observability.tracing.jaeger.endpoint}")
    private String jaegerEndpoint;
    
    @Value("${observability.logging.logstash.host}")
    private String logstashHost;
    
    @Value("${observability.logging.logstash.port}")
    private int logstashPort;
    
    @PostConstruct
    public void validate() {
        log.info("=== Observability Configuration ===");
        log.info("Observability Enabled: {}", observabilityEnabled);
        log.info("Tracing Enabled: {}", tracingEnabled);
        log.info("Sampling Rate: {}", samplingRate);
        log.info("Jaeger Endpoint: {}", jaegerEndpoint);
        log.info("Logstash: {}:{}", logstashHost, logstashPort);
        log.info("===================================");
        
        // Validate sampling rate
        if (samplingRate < 0.0 || samplingRate > 1.0) {
            throw new IllegalArgumentException(
                "Sampling rate must be between 0.0 and 1.0, got: " + samplingRate
            );
        }
        
        // Validate Jaeger endpoint format
        if (!jaegerEndpoint.startsWith("http://") && !jaegerEndpoint.startsWith("https://")) {
            throw new IllegalArgumentException(
                "Jaeger endpoint must start with http:// or https://, got: " + jaegerEndpoint
            );
        }
        
        // Validate Logstash port
        if (logstashPort < 1 || logstashPort > 65535) {
            throw new IllegalArgumentException(
                "Logstash port must be between 1 and 65535, got: " + logstashPort
            );
        }
    }
}
```

### Common Configuration Issues

#### Issue 1: Traces Not Appearing in Jaeger

**Symptoms**:
- No traces visible in Jaeger UI
- Service logs show no errors

**Possible Causes**:

1. **Sampling rate is 0**
   ```bash
   # Check sampling rate
   curl http://localhost:8080/actuator/configprops | jq '.observability.tracing.sampling'
   ```
   **Solution**: Set `TRACING_SAMPLING_RATE` to a value > 0

2. **Jaeger endpoint is incorrect**
   ```bash
   # Test Jaeger connectivity
   curl http://jaeger-endpoint:14250
   ```
   **Solution**: Verify `JAEGER_ENDPOINT` is correct and accessible

3. **Tracing is disabled**
   ```bash
   # Check if tracing is enabled
   curl http://localhost:8080/actuator/configprops | jq '.observability.tracing.enabled'
   ```
   **Solution**: Set `TRACING_ENABLED=true`

#### Issue 2: Logs Not Appearing in Kibana

**Symptoms**:
- No logs visible in Kibana
- Service logs show connection errors

**Possible Causes**:

1. **Logstash is not reachable**
   ```bash
   # Test Logstash connectivity
   telnet logstash-host 5000
   ```
   **Solution**: Verify `LOGSTASH_HOST` and `LOGSTASH_PORT` are correct

2. **Logback configuration is incorrect**
   ```bash
   # Check for Logback errors in service logs
   grep "Logback" application.log
   ```
   **Solution**: Verify `logback-spring.xml` is present and valid

3. **Elasticsearch indices not created**
   ```bash
   # Check Elasticsearch indices
   curl http://elasticsearch:9200/_cat/indices
   ```
   **Solution**: Verify Logstash pipeline is configured correctly

#### Issue 3: High Memory Usage

**Symptoms**:
- Service memory usage is high
- OutOfMemoryError exceptions

**Possible Causes**:

1. **Span queue is too large**
   ```yaml
   # Reduce queue size
   observability:
     tracing:
       jaeger:
         max-queue-size: 1024  # Reduce from 2048
   ```

2. **Sampling rate is too high**
   ```yaml
   # Reduce sampling rate
   observability:
     tracing:
       sampling:
         probability: 0.05  # Reduce to 5%
   ```

3. **Async logging queue is too large**
   ```yaml
   # Reduce logging queue size
   observability:
     logging:
       async:
         queue-size: 256  # Reduce from 512
   ```

#### Issue 4: Slow Performance

**Symptoms**:
- Increased request latency
- High CPU usage

**Possible Causes**:

1. **Sampling rate is too high**
   **Solution**: Reduce sampling rate to 10% or lower

2. **Caller data is enabled in logging**
   ```yaml
   # Disable caller data
   observability:
     logging:
       async:
         include-caller-data: false
   ```

3. **Too many metrics with high cardinality**
   ```yaml
   # Reduce cardinality limit
   observability:
     metrics:
       cardinality:
         limit: 50  # Reduce from 100
   ```

### Testing Configuration

#### Test Trace Generation

```bash
# Generate a test trace
curl http://localhost:8080/test/trace

# Verify trace appears in Jaeger
# Open http://localhost:16686 and search for service
```

#### Test Log Generation

```bash
# Generate test logs
curl http://localhost:8080/test/log

# Verify logs appear in Kibana
# Open http://localhost:5601 and search for service
```

#### Test Metrics Exposure

```bash
# Check Prometheus endpoint
curl http://localhost:8080/actuator/prometheus

# Verify metrics are present
curl http://localhost:8080/actuator/prometheus | grep http_server_requests
```

### Health Check Endpoints

```bash
# Check overall service health
curl http://localhost:8080/actuator/health

# Check observability backend health
curl http://localhost:8080/actuator/health | jq '.components.observability'
```

**Expected Response**:
```json
{
  "status": "UP",
  "components": {
    "observability": {
      "status": "UP",
      "details": {
        "jaeger": "UP",
        "logstash": "UP"
      }
    }
  }
}
```

## Best Practices

### 1. Use Environment Variables in Production

Always use environment variables for production configuration:
- Easier to change without redeployment
- Better security (no secrets in code)
- Consistent across environments

### 2. Start with High Sampling, Then Reduce

For new deployments:
1. Start with 100% sampling to verify everything works
2. Reduce to 50% after initial validation
3. Reduce to 10-20% for normal operation
4. Reduce to 1-5% for high traffic

### 3. Monitor Observability Backend Health

Set up alerts for:
- Jaeger unavailability
- Logstash connection failures
- High span queue depth
- High log queue depth

### 4. Test Configuration Changes in Staging

Always test configuration changes in staging before production:
- Verify traces appear correctly
- Verify logs are formatted correctly
- Verify metrics are exposed
- Check performance impact

### 5. Document Environment-Specific Settings

Maintain documentation for each environment:
- Sampling rates
- Backend endpoints
- Log levels
- Retention policies

## Additional Resources

- [OpenTelemetry Java Documentation](https://opentelemetry.io/docs/instrumentation/java/)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [Logback Documentation](https://logback.qos.ch/documentation.html)
- [Micrometer Documentation](https://micrometer.io/docs)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Troubleshooting Runbooks](./troubleshooting-runbooks.md)
- [Jaeger Usage Guide](./jaeger-usage-guide.md)
- [Kibana Usage Guide](./kibana-usage-guide.md)
