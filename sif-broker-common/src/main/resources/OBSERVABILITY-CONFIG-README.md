# Observability Configuration Guide

## Overview

This directory contains observability configuration files for the SIF Unity Broker platform. These files configure distributed tracing, centralized logging, and metrics collection across all microservices.

## Configuration Files

### observability-defaults.yml
Default configuration that applies to all environments. Contains sensible defaults and documents all available configuration properties.

### observability-dev.yml
Development environment configuration with:
- 100% trace sampling for complete visibility
- Verbose logging (DEBUG level)
- All actuator endpoints exposed
- Local Jaeger endpoint (localhost:14250)

### observability-test.yml
Test environment configuration with:
- 100% trace sampling for test coverage
- Balanced logging (INFO/DEBUG)
- Health and metrics endpoints exposed
- Jaeger endpoint for test infrastructure

### observability-prod.yml
Production environment configuration with:
- 10% trace sampling (configurable via environment variable)
- Minimal logging (WARN/INFO)
- Only essential endpoints exposed
- Production Jaeger endpoint
- Cardinality limiting for metrics

## Usage

### Option 1: Import in application.yml (Recommended)

Add to your service's `application.yml`:

```yaml
spring:
  config:
    import:
      - classpath:observability-defaults.yml
      - classpath:observability-${spring.profiles.active}.yml
```

This will:
1. Load default configuration
2. Override with environment-specific configuration based on active profile

### Option 2: Direct Import

For specific environments:

```yaml
spring:
  config:
    import: classpath:observability-prod.yml
```

### Option 3: Selective Override

Import defaults and override specific properties:

```yaml
spring:
  config:
    import: classpath:observability-defaults.yml

observability:
  tracing:
    sampling:
      probability: 0.05  # Override to 5% sampling
```

## Environment Variables

All configuration properties can be overridden via environment variables:

| Environment Variable | Description | Default | Example |
|---------------------|-------------|---------|---------|
| `OBSERVABILITY_ENABLED` | Enable/disable observability | `true` | `true` |
| `TRACING_ENABLED` | Enable/disable tracing | `true` | `true` |
| `JAEGER_ENDPOINT` | Jaeger collector endpoint | `http://localhost:14250` | `http://jaeger:14250` |
| `TRACING_SAMPLING_RATE` | Sampling probability (0.0-1.0) | `1.0` | `0.1` |
| `JAEGER_TIMEOUT` | Span export timeout (seconds) | `10` | `15` |
| `JAEGER_MAX_QUEUE_SIZE` | Max spans to buffer | `2048` | `4096` |
| `JAEGER_MAX_EXPORT_BATCH_SIZE` | Max spans per batch | `512` | `1024` |
| `JAEGER_SCHEDULE_DELAY` | Batch export delay (seconds) | `5` | `10` |
| `LOGSTASH_HOST` | Logstash server hostname | `localhost` | `logstash` |
| `LOGSTASH_PORT` | Logstash server port | `5000` | `5000` |

### Example: Docker Compose

```yaml
services:
  environment-service:
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - JAEGER_ENDPOINT=http://jaeger:14250
      - TRACING_SAMPLING_RATE=1.0
```

### Example: Kubernetes

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: observability-config
data:
  SPRING_PROFILES_ACTIVE: "prod"
  JAEGER_ENDPOINT: "http://jaeger-collector:14250"
  TRACING_SAMPLING_RATE: "0.1"
  LOGSTASH_HOST: "logstash"
  LOGSTASH_PORT: "5000"
```

## Sampling Rate Guidelines

### Development
- **Rate**: 100% (1.0)
- **Reason**: Complete visibility for debugging
- **Impact**: High storage usage, acceptable for local development

### Test
- **Rate**: 100% (1.0)
- **Reason**: Complete trace coverage for integration tests
- **Impact**: High storage usage, acceptable for test environments

### Staging
- **Rate**: 50% (0.5)
- **Reason**: Balance between visibility and storage
- **Impact**: Moderate storage usage

### Production (Low Traffic)
- **Rate**: 10-20% (0.1-0.2)
- **Reason**: Sufficient sampling for debugging
- **Impact**: Low storage usage

### Production (High Traffic)
- **Rate**: 1-5% (0.01-0.05)
- **Reason**: Minimize storage and performance impact
- **Impact**: Very low storage usage

## Performance Impact

### Tracing Overhead
- **100% sampling**: ~3-5% latency overhead
- **10% sampling**: ~0.5-1% latency overhead
- **1% sampling**: ~0.1-0.2% latency overhead

### Memory Overhead
- **Per service**: ~50-100MB additional heap usage
- **Span buffer**: Configurable via `max-queue-size`

### Network Overhead
- **Batch export**: Minimizes network calls
- **Compression**: Enabled by default in Jaeger exporter

## Troubleshooting

### Traces Not Appearing in Jaeger

1. **Check Jaeger endpoint**:
   ```bash
   curl http://localhost:14250
   ```

2. **Verify sampling rate**:
   - Ensure sampling rate > 0
   - Check environment variable: `echo $TRACING_SAMPLING_RATE`

3. **Check logs for errors**:
   ```bash
   grep "OpenTelemetry" application.log
   grep "Jaeger" application.log
   ```

4. **Verify span export**:
   - Enable DEBUG logging: `logging.level.io.opentelemetry=DEBUG`
   - Look for "Exporting spans" messages

### High Memory Usage

1. **Reduce queue size**:
   ```yaml
   observability:
     tracing:
       jaeger:
         max-queue-size: 1024  # Reduce from 2048
   ```

2. **Increase export frequency**:
   ```yaml
   observability:
     tracing:
       jaeger:
         schedule-delay: 2  # Reduce from 5 seconds
   ```

3. **Reduce sampling rate**:
   ```yaml
   observability:
     tracing:
       sampling:
         probability: 0.05  # Reduce to 5%
   ```

### Slow Performance

1. **Reduce sampling rate** (see above)

2. **Disable tracing temporarily**:
   ```yaml
   observability:
     tracing:
       enabled: false
   ```

3. **Check Jaeger backend performance**:
   - Ensure Jaeger has sufficient resources
   - Check Jaeger storage backend (Cassandra, Elasticsearch)

## Validation

### Verify Configuration Loading

Add to your service startup:

```java
@Component
@Slf4j
public class ObservabilityConfigValidator {
    
    @Value("${observability.tracing.sampling.probability}")
    private double samplingRate;
    
    @Value("${observability.tracing.jaeger.endpoint}")
    private String jaegerEndpoint;
    
    @PostConstruct
    public void validate() {
        log.info("Observability Configuration:");
        log.info("  Sampling Rate: {}", samplingRate);
        log.info("  Jaeger Endpoint: {}", jaegerEndpoint);
        
        if (samplingRate < 0.0 || samplingRate > 1.0) {
            throw new IllegalArgumentException(
                "Sampling rate must be between 0.0 and 1.0, got: " + samplingRate
            );
        }
    }
}
```

### Test Trace Generation

```java
@RestController
public class TraceTestController {
    
    @Autowired
    private TracingUtils tracingUtils;
    
    @GetMapping("/test/trace")
    public String testTrace() {
        return tracingUtils.traceOperation(
            "test.trace",
            Map.of("test", "true"),
            () -> "Trace generated successfully"
        );
    }
}
```

Access `http://localhost:8080/test/trace` and verify the trace appears in Jaeger UI.

## References

- [OpenTelemetry Java Documentation](https://opentelemetry.io/docs/instrumentation/java/)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [Micrometer Documentation](https://micrometer.io/docs)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
