# Test Compilation Issues - Events Connector

## Summary
The tests fail to compile due to constructor signature mismatches between test code and actual DTOs. This happened because the DTO definitions in `sif-broker-common` were updated after the tests were written.

## Issues Found

### 1. SubscriptionDTO Constructor Mismatch
**Expected (correct):**
```java
new SubscriptionDTO(
    UUID id,
    UUID queueId,
    String serviceName,
    String zoneId,
    String contextId,
    SubscriptionType type,      // MISSING in tests
    Instant createdAt            // MISSING in tests
)
```

**Used in tests (incorrect):**
```java
new SubscriptionDTO(
    UUID id,
    UUID queueId,
    String serviceName,
    String zoneId,
    String contextId,
    UUID someOtherId  // WRONG - should be SubscriptionType and Instant
)
```

**Affected Files:**
- `EventDistributionServiceTest.java` (9 occurrences)
- `SubscriptionQueryServiceTest.java` (2 occurrences)
- `EventDistributionPropertiesTest.java` (6 occurrences)
- `MessageConstructionPropertiesTest.java` (2 occurrences)
- `ServiceIntegrationPropertiesTest.java` (3 occurrences)
- `ObservabilityAndDeduplicationPropertiesTest.java` (6 occurrences)

### 2. EnqueueResponse Constructor Mismatch
**Expected (correct):**
```java
new EnqueueResponse(
    UUID messageId,
    UUID queueId,
    String storageKey,
    Instant enqueuedAt,
    String status
)
```

**Used in tests (incorrect):**
```java
new EnqueueResponse(
    String queueId,      // WRONG type - should be UUID
    UUID messageId,      // WRONG order
    String storageKey,
    String status        // MISSING enqueuedAt parameter
)
```

**Affected Files:**
- `QueueEnqueueServiceTest.java` (6 occurrences)

### 3. Missing Imports
**Affected Files:**
- `ServiceIntegrationPropertiesTest.java`
  - Missing: `import java.util.List;`
  - Missing: `import static org.assertj.core.api.Assertions.assertThat;`

## Fix Strategy

### Option 1: Fix All Tests Manually (Recommended)
Update all test files to use correct constructor signatures with proper parameters.

### Option 2: Use Builder Pattern or Factory Methods
Modify tests to use static factory methods like `EnqueueResponse.of()` where available.

## Next Steps
1. Fix SubscriptionDTO usages (add `SubscriptionType.EVENT` and `Instant.now()` parameters)
2. Fix EnqueueResponse usages (correct parameter order and types)
3. Add missing imports to ServiceIntegrationPropertiesTest
4. Recompile tests with `mvn test-compile`
5. Run tests with `mvn test`

## Estimated Effort
- 46 compilation errors across 7 test files
- Approximately 30-45 minutes to fix all issues
