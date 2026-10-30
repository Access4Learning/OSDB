# Custom Instrumentation Guide

## Table of Contents

1. [Overview](#overview)
2. [Creating Custom Spans](#creating-custom-spans)
3. [Adding Span Attributes](#adding-span-attributes)
4. [Creating Custom Metrics](#creating-custom-metrics)
5. [Adding Structured Log Fields](#adding-structured-log-fields)
6. [Best Practices](#best-practices)
7. [Examples](#examples)

## Overview

The SIF Unity Broker observability platform provides automatic instrumentation for HTTP requests, database queries, and cache operations. However, you may need to add custom instrumentation for business-specific operations that aren't automatically captured.

This guide shows you how to:
- Create custom spans for tracing business operations
- Add custom attributes to spans for context
- Create custom metrics for business events
- Add structured fields to logs

### Prerequisites

All services in the SIF Unity Broker platform have access to:
- `TracingUtils`: For creating custom spans
- `BusinessMetricsService`: For creating custom metrics
- `Logger` with MDC: For structured logging

These are automatically configured and available via dependency injection.

## Creating Custom Spans

Custom spans allow you to trace business operations that aren't automatically instrumented.

### When to Create Custom Spans

Create custom spans for:
- **Business Operations**: Queue processing, message transformation, validation
- **External Service Calls**: Calls to SIS providers, external APIs
- **Batch Operations**: Processing multiple items
- **Complex Workflows**: Multi-step business processes

**Don't create spans for**:
- HTTP requests (automatically instrumented)
- Database queries (automatically instrumented)
- Redis operations (automatically instrumented)

### Using TracingUtils

#### Synchronous Operations

**Basic Usage**:
```java
@Service
@RequiredArgsConstructor
public class QueueService {
    
    private final TracingUtils tracingUtils;
    
    public void processMessage(Message message) {
        tracingUtils.traceOperation(
            "queue.process_message",
            Map.of(
                "queue.name", message.getQueueName(),
                "message.id", message.getId(),
                "message.type", message.getType()
            ),
            () -> {
                // Your business logic here
                validateMessage(message);
                transformMessage(message);
                storeMessage(message);
            }
        );
    }
}
```

**With Return Value**:
```java
public String processMessage(Message message) {
    return tracingUtils.traceOperation(
        "queue.process_message",
        Map.of(
            "queue.name", message.getQueueName(),
            "message.id", message.getId()
        ),
        () -> {
            // Your business logic here
            String result = transformMessage(message);
            return result;
        }
    );
}
```

#### Reactive Operations

**Basic Usage**:
```java
@Service
@RequiredArgsConstructor
public class ProviderService {
    
    private final TracingUtils tracingUtils;
    private final WebClient webClient;
    
    public Mono<String> callProvider(String providerId) {
        Mono<String> operation = webClient
            .get()
            .uri("/api/provider/" + providerId)
            .retrieve()
            .bodyToMono(String.class);
        
        return tracingUtils.traceReactiveOperation(
            "provider.call",
            Map.of(
                "provider.id", providerId,
                "provider.url", "/api/provider/" + providerId
            ),
            operation
        );
    }
}
```

### Span Naming Conventions

Use descriptive, hierarchical names:

**Format**: `<component>.<operation>`

**Examples**:
- `queue.process_message`
- `subscription.create`
- `provider.call`
- `message.transform`
- `validation.check_schema`

**Avoid**:
- Generic names: `process`, `handle`, `execute`
- Too specific: `process_message_from_immediate_queue_for_student_personal`
- Camel case: `processMessage` (use snake_case)

### Nested Spans

Spans automatically nest when created within another span's context:

```java
public void processMessage(Message message) {
    tracingUtils.traceOperation(
        "queue.process_message",
        Map.of("message.id", message.getId()),
        () -> {
            // This span is the parent
            
            // Child span 1
            tracingUtils.traceOperation(
                "message.validate",
                Map.of("schema", message.getSchema()),
                () -> validateMessage(message)
            );
            
            // Child span 2
            tracingUtils.traceOperation(
                "message.transform",
                Map.of("format", "json"),
                () -> transformMessage(message)
            );
        }
    );
}
```

**Result in Jaeger**:
```
queue.process_message (100ms)
  ├─ message.validate (20ms)
  └─ message.transform (30ms)
```

## Adding Span Attributes

Attributes provide context about what the span is doing. They appear as tags in Jaeger and can be used for filtering and analysis.

### Adding Attributes at Span Creation

**Recommended Approach**:
```java
tracingUtils.traceOperation(
    "queue.process_message",
    Map.of(
        "queue.name", "immediate-requests",
        "message.id", "msg-12345",
        "message.type", "StudentPersonal",
        "zone.id", "test-zone",
        "context.id", "test-context"
    ),
    () -> processMessage(message)
);
```

### Adding Attributes Dynamically

Add attributes during operation execution:

```java
tracingUtils.traceOperation(
    "subscription.create",
    Map.of("zone.id", subscription.getZoneId()),
    () -> {
        // Add attribute after some processing
        tracingUtils.addAttribute("subscription.id", subscription.getId());
        
        // Add attribute based on condition
        if (subscription.isActive()) {
            tracingUtils.addAttribute("subscription.status", "active");
        }
        
        return createSubscription(subscription);
    }
);
```

### Attribute Naming Conventions

Use namespaced, descriptive names:

**Format**: `<namespace>.<attribute>`

**Common Namespaces**:
- `queue.*`: Queue-related attributes
- `message.*`: Message attributes
- `subscription.*`: Subscription attributes
- `provider.*`: Provider attributes
- `http.*`: HTTP attributes
- `db.*`: Database attributes
- `cache.*`: Cache attributes

**Examples**:
```java
// Good
"queue.name", "immediate-requests"
"message.type", "StudentPersonal"
"provider.id", "sis-provider-1"
"http.status_code", "200"

// Avoid
"queueName", "immediate-requests"  // Use dots, not camelCase
"type", "StudentPersonal"          // Too generic
"id", "123"                        // Too generic
```

### Standard Attributes

Use these standard attributes for consistency:

**Queue Operations**:
```java
Map.of(
    "queue.name", queueName,
    "message.id", messageId,
    "message.type", messageType,
    "message.size", messageSize
)
```

**SIF Operations**:
```java
Map.of(
    "zone.id", zoneId,
    "context.id", contextId,
    "message.type", messageType,
    "provider.id", providerId
)
```

**HTTP Operations**:
```java
Map.of(
    "http.method", "GET",
    "http.url", url,
    "http.status_code", statusCode
)
```

**Database Operations**:
```java
Map.of(
    "db.operation", "SELECT",
    "db.table", tableName,
    "db.rows_affected", rowCount
)
```

### Adding Events

Events are timestamped annotations within a span:

```java
tracingUtils.traceOperation(
    "message.process",
    Map.of("message.id", messageId),
    () -> {
        tracingUtils.addEvent("validation_started");
        validateMessage(message);
        
        tracingUtils.addEvent("validation_completed");
        
        tracingUtils.addEvent("transformation_started");
        transformMessage(message);
        
        tracingUtils.addEvent("transformation_completed");
    }
);
```

**Result in Jaeger**: Events appear as markers on the span timeline

### Recording Exceptions

Exceptions are automatically recorded when using `traceOperation`, but you can also record them manually:

```java
try {
    riskyOperation();
} catch (Exception e) {
    tracingUtils.recordException(e);
    // Handle exception
}
```

## Creating Custom Metrics

Custom metrics allow you to track business events and measurements.

### When to Create Custom Metrics

Create custom metrics for:
- **Business Events**: Messages processed, subscriptions created, jobs completed
- **Business Measurements**: Queue depth, cache hit rate, processing time
- **Error Tracking**: Validation failures, provider errors, timeout counts

### Using BusinessMetricsService

#### Counters

Counters track the number of times something happens.

**Queue Message Processing**:
```java
@Service
@RequiredArgsConstructor
public class QueueService {
    
    private final BusinessMetricsService metricsService;
    
    public void processMessage(Message message) {
        try {
            // Process message
            processMessageInternal(message);
            
            // Increment success counter
            metricsService.incrementQueueMessageProcessed(
                message.getQueueName(),
                "success"
            );
        } catch (Exception e) {
            // Increment error counter
            metricsService.incrementQueueMessageProcessed(
                message.getQueueName(),
                "error"
            );
            throw e;
        }
    }
}
```

**Cache Operations**:
```java
public String getCachedValue(String key) {
    String value = cache.get(key);
    
    if (value != null) {
        metricsService.incrementCacheOperation("subscription-cache", "get", "hit");
        return value;
    } else {
        metricsService.incrementCacheOperation("subscription-cache", "get", "miss");
        return fetchFromDatabase(key);
    }
}
```

**Error Tracking**:
```java
try {
    validateMessage(message);
} catch (ValidationException e) {
    metricsService.incrementError("validation", "queue-service");
    throw e;
}
```

**SIF Message Processing**:
```java
metricsService.incrementSifMessageProcessed(
    "StudentPersonal",  // message type
    "test-zone",        // zone
    "success"           // status
);
```

#### Gauges

Gauges track current values that can go up or down.

**Queue Depth**:
```java
@Scheduled(fixedRate = 5000)  // Update every 5 seconds
public void updateQueueMetrics() {
    for (String queueName : queueNames) {
        int depth = getQueueDepth(queueName);
        metricsService.recordQueueDepth(queueName, depth);
    }
}
```

**Database Connection Pool**:
```java
@Scheduled(fixedRate = 10000)  // Update every 10 seconds
public void updateConnectionPoolMetrics() {
    HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
    
    metricsService.recordDatabaseConnectionPool(
        "primary",
        pool.getActiveConnections(),
        pool.getIdleConnections(),
        pool.getTotalConnections()
    );
}
```

#### Timers

Timers measure how long operations take.

**Basic Usage**:
```java
String result = metricsService.timeOperation(
    "provider.call",
    Map.of("provider", "sis-provider"),
    () -> callProvider()
);
```

**With No Return Value**:
```java
metricsService.timeOperation(
    "message.transform",
    Map.of("format", "json"),
    () -> transformMessage(message)
);
```

**Manual Timing**:
```java
Timer.Sample sample = Timer.start(registry);
try {
    performOperation();
} finally {
    sample.stop(Timer.builder("operation.duration")
        .tag("operation", "process")
        .register(registry));
}
```

### Metric Naming Conventions

Use descriptive, hierarchical names:

**Format**: `<component>.<metric>.<unit>`

**Examples**:
```
queue.messages.processed      (counter)
queue.depth                   (gauge)
cache.operations              (counter)
provider.call.duration        (timer)
db.pool.active                (gauge)
errors.total                  (counter)
```

**Avoid**:
- Camel case: `queueMessagesProcessed`
- Too generic: `count`, `total`
- Too specific: `immediate_queue_student_personal_messages_processed`

### Metric Tags

Tags allow you to filter and group metrics:

**Good Tags** (low cardinality):
```java
.tag("queue", "immediate-requests")     // Limited number of queues
.tag("status", "success")               // Limited statuses
.tag("message_type", "StudentPersonal") // Limited message types
.tag("service", "queue-service")        // Limited services
```

**Bad Tags** (high cardinality):
```java
.tag("message_id", messageId)           // Unbounded
.tag("user_id", userId)                 // Unbounded
.tag("timestamp", timestamp)            // Unbounded
.tag("url", fullUrl)                    // Unbounded
```

**Cardinality Limit**: Keep total unique tag combinations < 100 per metric

## Adding Structured Log Fields

Structured logging allows you to add custom fields to log entries that can be searched and filtered in Kibana.

### Using SLF4J with Structured Arguments

**Basic Usage**:
```java
@Slf4j
@Service
public class QueueService {
    
    public void processMessage(Message message) {
        log.info("Processing message from queue",
            kv("queue_name", message.getQueueName()),
            kv("message_id", message.getId()),
            kv("message_type", message.getType())
        );
    }
}
```

**Import**:
```java
import static net.logstash.logback.argument.StructuredArguments.kv;
```

**Result in Kibana**:
```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "message": "Processing message from queue",
  "queue_name": "immediate-requests",
  "message_id": "msg-12345",
  "message_type": "StudentPersonal"
}
```

### Using MDC (Mapped Diagnostic Context)

MDC automatically adds fields to all log entries in the current thread:

**Setting MDC Values**:
```java
import org.slf4j.MDC;

public void processMessage(Message message) {
    try {
        MDC.put("queue_name", message.getQueueName());
        MDC.put("message_id", message.getId());
        
        // All logs in this method will include these fields
        log.info("Starting message processing");
        validateMessage(message);
        log.info("Message validated");
        transformMessage(message);
        log.info("Message transformed");
        
    } finally {
        // Always clear MDC
        MDC.remove("queue_name");
        MDC.remove("message_id");
    }
}
```

**Note**: MDC is automatically populated with `trace_id`, `span_id`, and `correlation_id` by the observability platform.

### Structured Logging Best Practices

**1. Use Consistent Field Names**:
```java
// Good
log.info("Message processed", kv("message_id", id));
log.info("Message failed", kv("message_id", id));

// Avoid
log.info("Message processed", kv("message_id", id));
log.info("Message failed", kv("msg_id", id));  // Inconsistent
```

**2. Use Appropriate Log Levels**:
```java
log.debug("Detailed processing info", kv("step", "validation"));
log.info("Message processed", kv("message_id", id));
log.warn("Slow processing detected", kv("duration_ms", duration));
log.error("Processing failed", kv("error", e.getMessage()), e);
```

**3. Don't Log Sensitive Data**:
```java
// Good
log.info("User authenticated", kv("user_id", userId));

// Bad - password will be redacted but avoid logging it
log.info("User login", kv("username", username), kv("password", password));
```

**4. Use Structured Arguments for Searchable Data**:
```java
// Good - searchable in Kibana
log.info("Message processed", 
    kv("message_id", id),
    kv("queue_name", queueName),
    kv("duration_ms", duration)
);

// Avoid - not easily searchable
log.info("Message {} processed from queue {} in {}ms", id, queueName, duration);
```

### Logging Exceptions

**With Structured Fields**:
```java
try {
    processMessage(message);
} catch (Exception e) {
    log.error("Message processing failed",
        kv("message_id", message.getId()),
        kv("queue_name", message.getQueueName()),
        kv("error_type", e.getClass().getSimpleName()),
        e  // Exception must be last parameter
    );
}
```

**Result**: Stack trace is included in log entry

## Best Practices

### Tracing Best Practices

1. **Create Spans for Business Operations**: Focus on business logic, not infrastructure
2. **Use Descriptive Names**: `queue.process_message` not `process`
3. **Add Relevant Attributes**: Include context needed for debugging
4. **Keep Spans Focused**: One span per logical operation
5. **Avoid Over-instrumentation**: Don't create spans for trivial operations

### Metrics Best Practices

1. **Use Appropriate Metric Types**:
   - Counter: Events that only increase (messages processed)
   - Gauge: Values that go up and down (queue depth)
   - Timer: Operation duration (processing time)

2. **Keep Cardinality Low**: Limit unique tag combinations to < 100
3. **Use Consistent Tags**: Same tag names across related metrics
4. **Document Metrics**: Add descriptions to metrics
5. **Monitor Metric Growth**: Check for cardinality explosions

### Logging Best Practices

1. **Use Structured Logging**: Add searchable fields with `kv()`
2. **Use Appropriate Levels**: DEBUG < INFO < WARN < ERROR
3. **Don't Log Sensitive Data**: Passwords, tokens, PII
4. **Include Context**: Add trace_id, correlation_id, message_id
5. **Log at Boundaries**: Service entry/exit, external calls, errors

### Performance Considerations

1. **Tracing Overhead**: ~3-5% with 100% sampling, ~0.5% with 10% sampling
2. **Metrics Overhead**: ~0.1ms per metric update
3. **Logging Overhead**: ~1ms per log statement (async)
4. **Avoid in Hot Paths**: Don't add instrumentation in tight loops
5. **Use Sampling**: Reduce sampling rate in production

## Examples

### Example 1: Queue Message Processing

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {
    
    private final TracingUtils tracingUtils;
    private final BusinessMetricsService metricsService;
    
    public void processMessage(Message message) {
        tracingUtils.traceOperation(
            "queue.process_message",
            Map.of(
                "queue.name", message.getQueueName(),
                "message.id", message.getId(),
                "message.type", message.getType()
            ),
            () -> {
                log.info("Processing message",
                    kv("queue_name", message.getQueueName()),
                    kv("message_id", message.getId())
                );
                
                try {
                    // Validate
                    tracingUtils.traceOperation(
                        "message.validate",
                        Map.of("schema", message.getSchema()),
                        () -> validateMessage(message)
                    );
                    
                    // Transform
                    tracingUtils.traceOperation(
                        "message.transform",
                        Map.of("format", "json"),
                        () -> transformMessage(message)
                    );
                    
                    // Store
                    tracingUtils.traceOperation(
                        "message.store",
                        Map.of("destination", "database"),
                        () -> storeMessage(message)
                    );
                    
                    // Metrics
                    metricsService.incrementQueueMessageProcessed(
                        message.getQueueName(),
                        "success"
                    );
                    
                    log.info("Message processed successfully",
                        kv("message_id", message.getId())
                    );
                    
                } catch (Exception e) {
                    metricsService.incrementQueueMessageProcessed(
                        message.getQueueName(),
                        "error"
                    );
                    
                    log.error("Message processing failed",
                        kv("message_id", message.getId()),
                        kv("error_type", e.getClass().getSimpleName()),
                        e
                    );
                    
                    throw e;
                }
            }
        );
    }
}
```

### Example 2: External Provider Call

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderService {
    
    private final TracingUtils tracingUtils;
    private final BusinessMetricsService metricsService;
    private final WebClient webClient;
    
    public Mono<ProviderResponse> callProvider(String providerId, String request) {
        log.info("Calling provider",
            kv("provider_id", providerId),
            kv("request_size", request.length())
        );
        
        Mono<ProviderResponse> operation = webClient
            .post()
            .uri("/api/provider/" + providerId)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ProviderResponse.class);
        
        return tracingUtils.traceReactiveOperation(
            "provider.call",
            Map.of(
                "provider.id", providerId,
                "provider.url", "/api/provider/" + providerId,
                "request.size", String.valueOf(request.length())
            ),
            operation
        )
        .doOnSuccess(response -> {
            log.info("Provider call successful",
                kv("provider_id", providerId),
                kv("response_size", response.getData().length())
            );
        })
        .doOnError(error -> {
            metricsService.incrementError("provider_call", "provider-service");
            
            log.error("Provider call failed",
                kv("provider_id", providerId),
                kv("error_type", error.getClass().getSimpleName()),
                error
            );
        });
    }
}
```

### Example 3: Subscription Creation

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {
    
    private final TracingUtils tracingUtils;
    private final BusinessMetricsService metricsService;
    
    public Subscription createSubscription(SubscriptionRequest request) {
        return tracingUtils.traceOperation(
            "subscription.create",
            Map.of(
                "zone.id", request.getZoneId(),
                "context.id", request.getContextId(),
                "message.type", request.getMessageType()
            ),
            () -> {
                log.info("Creating subscription",
                    kv("zone_id", request.getZoneId()),
                    kv("message_type", request.getMessageType())
                );
                
                // Validate
                tracingUtils.addEvent("validation_started");
                validateRequest(request);
                tracingUtils.addEvent("validation_completed");
                
                // Create
                Subscription subscription = new Subscription();
                subscription.setZoneId(request.getZoneId());
                subscription.setContextId(request.getContextId());
                subscription.setMessageType(request.getMessageType());
                
                // Save
                tracingUtils.addEvent("save_started");
                Subscription saved = subscriptionRepository.save(subscription);
                tracingUtils.addEvent("save_completed");
                
                // Add subscription ID to span
                tracingUtils.addAttribute("subscription.id", saved.getId());
                
                // Metrics
                metricsService.incrementSifMessageProcessed(
                    request.getMessageType(),
                    request.getZoneId(),
                    "success"
                );
                
                log.info("Subscription created",
                    kv("subscription_id", saved.getId()),
                    kv("zone_id", saved.getZoneId())
                );
                
                return saved;
            }
        );
    }
}
```

### Example 4: Batch Processing

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchProcessor {
    
    private final TracingUtils tracingUtils;
    private final BusinessMetricsService metricsService;
    
    public void processBatch(List<Message> messages) {
        tracingUtils.traceOperation(
            "batch.process",
            Map.of(
                "batch.size", String.valueOf(messages.size()),
                "batch.type", "message_processing"
            ),
            () -> {
                log.info("Processing batch",
                    kv("batch_size", messages.size())
                );
                
                int successCount = 0;
                int errorCount = 0;
                
                for (Message message : messages) {
                    try {
                        // Each message gets its own span
                        tracingUtils.traceOperation(
                            "batch.process_item",
                            Map.of("message.id", message.getId()),
                            () -> processMessage(message)
                        );
                        successCount++;
                    } catch (Exception e) {
                        errorCount++;
                        log.warn("Message processing failed in batch",
                            kv("message_id", message.getId()),
                            kv("error", e.getMessage())
                        );
                    }
                }
                
                // Add summary to span
                tracingUtils.addAttribute("batch.success_count", String.valueOf(successCount));
                tracingUtils.addAttribute("batch.error_count", String.valueOf(errorCount));
                
                log.info("Batch processing completed",
                    kv("batch_size", messages.size()),
                    kv("success_count", successCount),
                    kv("error_count", errorCount)
                );
            }
        );
    }
}
```

## Additional Resources

- [Configuration Guide](./configuration-guide.md)
- [Jaeger Usage Guide](./jaeger-usage-guide.md)
- [Kibana Usage Guide](./kibana-usage-guide.md)
- [Grafana Dashboard Guide](./grafana-dashboard-guide.md)
- [OpenTelemetry Java Documentation](https://opentelemetry.io/docs/instrumentation/java/)
- [Micrometer Documentation](https://micrometer.io/docs)
- [Logback Documentation](https://logback.qos.ch/documentation.html)
