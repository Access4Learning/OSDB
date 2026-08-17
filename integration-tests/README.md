# SIF Unity Broker - Integration Tests

Comprehensive end-to-end integration tests for the SIF Unity Broker system.

## Overview

This module contains integration tests that validate the complete SIF Unity Broker system working together. Tests cover:

- **Message Exchange Patterns**: IMMEDIATE, DELAYED, and EVENT patterns
- **SIF 3.7 Compliance**: Full compliance with SIF 3.7 Brokered Mode specification
- **Authentication/Authorization**: Token validation, HMAC signatures, ACL enforcement
- **Error Handling**: Provider failures, timeouts, validation errors, circuit breakers
- **Message Persistence**: Storage, recovery, at-least-once delivery guarantees

## Technology Stack

- **Test Framework**: JUnit 5 (Jupiter)
- **REST API Testing**: RestAssured
- **Infrastructure**: Testcontainers (PostgreSQL, Redis, LocalStack)
- **Mocking**: WireMock (Provider simulation)
- **Assertions**: AssertJ
- **Build Tool**: Maven

## Prerequisites

- Java 17 or higher
- Maven 3.8 or higher
- Docker (for Testcontainers)
- At least 4GB RAM available for tests

## Running Tests

### Run All Integration Tests

```bash
mvn clean verify
```

### Run Specific Test Category

```bash
# Message pattern tests
mvn test -Dtest="*PatternTest"

# Compliance tests
mvn test -Dtest="*ComplianceTest"

# Authentication tests
mvn test -Dtest="*AuthTest"

# Error handling tests
mvn test -Dtest="*ErrorTest"

# Persistence tests
mvn test -Dtest="*PersistenceTest"
```

### Run Single Test Class

```bash
mvn test -Dtest=ImmediatePatternTest
```

### Run with Debug Logging

```bash
mvn test -Dlogging.level.org.sifunity.broker=DEBUG
```

## Test Organization

```
integration-tests/
├── src/
│   ├── main/java/org/sifunity/integration/
│   │   ├── config/              # Test configuration
│   │   ├── container/           # Testcontainers setup
│   │   ├── service/             # Service starter utilities
│   │   ├── client/              # REST clients for services
│   │   ├── generator/           # Test data generators
│   │   ├── wiremock/            # WireMock stub configurations
│   │   └── util/                # Test utilities
│   └── test/java/org/sifunity/integration/
│       ├── pattern/             # Message exchange pattern tests
│       ├── compliance/          # SIF 3.7 compliance tests
│       ├── auth/                # Authentication/authorization tests
│       ├── error/               # Error handling tests
│       ├── persistence/         # Message persistence tests
│       └── base/                # Base test classes
├── pom.xml
└── README.md
```

## Test Infrastructure

### Testcontainers

Tests automatically start the following containers:

- **PostgreSQL 15**: Database for all microservices
- **Redis 7**: Caching and message streams
- **LocalStack**: S3 simulation for message storage

Containers are started once before all tests and stopped after all tests complete.

### Service Orchestration

Tests start all 9 microservices in the correct order:

1. Environment Service (foundational)
2. Queue Service, Subscription Service, Job Service (core services)
3. Requests, Responses, Events, Services Connectors (connector services)
4. API Gateway (entry point)

Each service starts with test-specific configuration and random ports to avoid conflicts.

### WireMock Provider Simulation

Tests use WireMock to simulate external Provider applications:

- **IMMEDIATE Pattern**: Synchronous HTTP responses
- **DELAYED Pattern**: Queue-based request/response
- **EVENT Pattern**: Event publication

## Writing New Tests

### Test Base Classes

**IntegrationTestBase** (Fast - ~30 seconds startup):
- Provides Testcontainers infrastructure only
- No microservices started
- Use for tests that only need database/Redis/S3
- Example: Data model tests, utility tests

**ServiceEnabledIntegrationTestBase** (Slow - ~2-3 minutes startup):
- Extends IntegrationTestBase
- Starts all 9 microservices
- Use for end-to-end integration tests
- Example: Message pattern tests, API tests

### 1. Extend Appropriate Base Class

For infrastructure-only tests:
```java
@Tag("smoke")
public class MyInfrastructureTest extends IntegrationTestBase {
    
    @Test
    public void testDatabaseAccess() {
        // Containers available, services NOT started
    }
}
```

For full integration tests:
```java
@Tag("message-pattern")
public class MyIntegrationTest extends ServiceEnabledIntegrationTestBase {
    
    @Test
    public void testEndToEndFlow() {
        // Containers AND services available
    }
}
```

### 2. Create Test Data

```java
// Create environment
Environment consumer = environmentGenerator.createEnvironment("test-consumer");

// Create ACL entry
aclGenerator.createAclEntry(
    consumer.getId(),
    "StudentPersonals",
    "test-zone",
    "DEFAULT",
    AccessType.QUERY,
    AccessLevel.APPROVED
);

// Register provider
providerGenerator.registerProvider(
    providerEnv.getId(),
    "StudentPersonals",
    "test-zone",
    "DEFAULT",
    wireMockServer.baseUrl() + "/StudentPersonals",
    List.of(MessagePattern.IMMEDIATE)
);
```

### 3. Configure WireMock Stubs

```java
wireMockServer.stubFor(
    post("/StudentPersonals")
        .willReturn(aResponse()
            .withStatus(200)
            .withBody(responseBody))
);
```

### 4. Execute Test Scenario

```java
String authHeader = environmentGenerator.generateAuthHeader(
    consumer,
    Instant.now().toString()
);

Response response = given()
    .header("Authorization", authHeader)
    .header("zoneId", "test-zone")
    .header("contextId", "DEFAULT")
    .contentType("application/json")
    .body(requestBody)
.when()
    .post("/requests/StudentPersonals")
.then()
    .statusCode(200)
    .extract().response();
```

### 5. Make Assertions

```java
assertThat(response.getBody().asString())
    .contains("expectedValue");

wireMockServer.verify(
    postRequestedFor(urlEqualTo("/StudentPersonals"))
        .withHeader("Authorization", matching("SIF_HMACSHA256.*"))
);
```

## Test Data Cleanup

Test data is automatically cleaned up after each test:

- Environments deleted
- ACL entries removed
- Provider registrations removed
- Queues deleted
- Subscriptions removed
- Redis cache cleared
- S3 bucket cleared

## Troubleshooting

### Tests Fail to Start Containers

**Problem**: Docker not running or insufficient resources

**Solution**:
- Ensure Docker is running
- Increase Docker memory to at least 4GB
- Check Docker logs for errors

### Tests Timeout

**Problem**: Services take too long to start

**Solution**:
- Increase timeout in test configuration
- Check service logs for startup errors
- Ensure sufficient system resources

### Port Conflicts

**Problem**: Services fail to start due to port conflicts

**Solution**:
- Tests use random ports by default
- Ensure no other services are using the test port range
- Check for zombie processes from previous test runs

### Test Data Not Cleaned Up

**Problem**: Test data persists between tests

**Solution**:
- Check `@AfterEach` cleanup methods are running
- Verify database connection is working
- Check for exceptions during cleanup

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Integration Tests

on: [push, pull_request]

jobs:
  integration-tests:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'corretto'
      
      - name: Run Integration Tests
        run: mvn clean verify -pl integration-tests
      
      - name: Upload Test Reports
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-reports
          path: integration-tests/target/surefire-reports/
```

## Performance

**Expected Test Execution Time**:
- Infrastructure setup: < 2 minutes
- Service startup: < 3 minutes
- Test execution: < 10 minutes
- **Total: < 15 minutes**

## Contributing

When adding new integration tests:

1. Follow the existing test organization structure
2. Use appropriate test tags (@Tag)
3. Extend IntegrationTestBase for infrastructure
4. Use test data generators for consistent data creation
5. Clean up test data in @AfterEach
6. Add clear assertion messages
7. Document complex test scenarios

## Support

For questions or issues:
- Check existing tests for examples
- Review the design document in `.kiro/specs/integration-tests/design.md`
- Contact the SIF Unity Broker team
