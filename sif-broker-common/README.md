# SIF Broker Common Library

A shared Java library providing common contracts, data models, exceptions, and utilities for all SIF Unity Broker microservices.

## Overview

The SIF Broker Common Library ensures consistency in data structures, error handling, and SIF 3.7 compliance across the entire SIF Unity Broker system. This library serves as the foundation for inter-service communication and provides standardized components that all microservices depend on.

### Purpose

This library was created to:
- **Ensure Consistency**: Standardize data structures, error handling, and SIF compliance across all microservices
- **Reduce Duplication**: Centralize common functionality to avoid code duplication
- **Enforce Standards**: Provide type-safe enumerations and validation utilities
- **Simplify Testing**: Offer test utilities and generators for consistent testing practices
- **Maintain SIF Compliance**: Ensure all components conform to SIF 3.7 specification

## Key Features

### 🏗️ Data Transfer Objects (DTOs)
- **Immutable DTOs** for all shared domain entities (Environment, Queue, Message, ACL, etc.)
- **Validation annotations** for automatic field validation
- **JSON serialization** with camelCase naming and ISO-8601 dates
- **Proper equals/hashCode** implementations for reliable comparisons

### 📨 SIF Message Envelope
- **Standardized message wrapper** conforming to SIF 3.7 specification
- **Auto-generation** of messageId and timestamp when not provided
- **Generic payload support** for flexible message content
- **Complete SIF header support** (required and optional headers)

### ⚠️ Exception Hierarchy
- **Consistent exception handling** with proper HTTP status code mapping
- **Categorized exceptions** for authentication, authorization, routing, validation, and storage
- **Rich error context** including request ID, environment ID, and service name
- **SIF-compliant error responses** with standardized error codes

### 🔧 Utility Classes
- **SIF Authentication**: HMAC-SHA256 signature computation and validation
- **Header Processing**: SIF header parsing and validation utilities
- **Storage Key Generation**: Hierarchical key generation for message storage
- **Validation Utilities**: Common validation for UUIDs, service names, zones, etc.
- **Logging Utilities**: Structured logging with correlation IDs and MDC context

### 📊 Constants and Enumerations
- **Type-safe enumerations** for message patterns, access types, service types, etc.
- **Centralized constants** for SIF headers, error codes, and default values
- **No magic strings** - all values are properly defined and documented

### 🧪 Test Utilities
- **Property-based test generators** using jqwik for randomized testing
- **Test builders** with fluent APIs for easy test data creation
- **Assertion helpers** for validating SIF compliance and data integrity
- **Comprehensive test coverage** with both unit and property-based tests

## Technology Stack

- **Java**: 17 (LTS)
- **Build Tool**: Maven 3.6+
- **Framework**: Spring Boot 3.2.0
- **JSON Processing**: Jackson (with JSR-310 support)
- **Validation**: Jakarta Bean Validation API
- **Code Generation**: Lombok
- **Testing**: JUnit 5, jqwik (property-based testing), AssertJ
- **Logging**: SLF4J

## Project Structure

```
sif-broker-common/
├── src/main/java/org/sifunity/broker/common/
│   ├── dto/              # Data Transfer Objects
│   │   ├── EnvironmentDTO.java
│   │   ├── QueueDTO.java
│   │   ├── MessageDTO.java
│   │   ├── AclEntryDTO.java
│   │   ├── SubscriptionDTO.java
│   │   ├── ProviderRegistrationDTO.java
│   │   └── JobDTO.java
│   ├── exception/        # Exception hierarchy
│   │   ├── SifBrokerException.java (base)
│   │   ├── InvalidTokenException.java
│   │   ├── InsufficientPermissionsException.java
│   │   ├── ProviderNotFoundException.java
│   │   ├── InvalidRequestException.java
│   │   ├── StorageException.java
│   │   └── ServiceUnavailableException.java
│   ├── model/            # Domain models and envelopes
│   │   ├── SifMessageEnvelope.java
│   │   └── SifErrorResponse.java
│   ├── constant/         # Constants and enumerations
│   │   ├── MessageExchangePattern.java
│   │   ├── AccessType.java
│   │   ├── MessageType.java
│   │   ├── ServiceType.java
│   │   ├── SifHeaders.java
│   │   ├── ErrorCodes.java
│   │   └── DefaultValues.java
│   ├── util/             # Utility classes
│   │   ├── SifHeaderUtils.java
│   │   ├── SifAuthenticationUtils.java
│   │   ├── StorageKeyGenerator.java
│   │   ├── ValidationUtils.java
│   │   └── LoggingUtils.java
│   └── config/           # Configuration classes
│       └── JacksonConfiguration.java
├── src/test/java/org/sifunity/broker/common/
│   ├── generator/        # Test data generators (jqwik)
│   │   └── TestDataGenerators.java
│   ├── builder/          # Test builders
│   │   └── TestBuilders.java
│   ├── assertion/        # Assertion helpers
│   │   └── SifAssertions.java
│   └── util/             # Test utilities
└── pom.xml
```

## Prerequisites

Before using this library, ensure you have:

- **Java 17 or higher** - The library uses Java 17 features and requires this version minimum
- **Maven 3.6 or higher** - For building and dependency management
- **Spring Boot 3.x** - The library is designed to work with Spring Boot 3.x applications

## Installation

### Maven Dependency

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.sifunity.broker</groupId>
    <artifactId>sif-broker-common</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Gradle Dependency

For Gradle projects, add to your `build.gradle`:

```gradle
implementation 'org.sifunity.broker:sif-broker-common:1.0.0-SNAPSHOT'
```

### Local Installation

To install the library to your local Maven repository:

```bash
cd sif-broker-common
mvn clean install
```

## Usage Examples

### Working with DTOs

#### Creating Environment DTOs

```java
import org.sifunity.broker.common.dto.EnvironmentDTO;
import org.sifunity.broker.common.constant.EnvironmentType;
import java.time.Instant;
import java.util.UUID;

// Create a consumer environment
EnvironmentDTO environment = new EnvironmentDTO(
    UUID.randomUUID(),
    "MySchoolApp",
    "session-token-abc123",
    Instant.now(),
    Instant.now().plusSeconds(3600), // expires in 1 hour
    "default",
    "school-district-1",
    EnvironmentType.DIRECT
);

// Access immutable fields
String appName = environment.getApplicationName();
UUID envId = environment.getId();
```

#### Creating Queue DTOs

```java
import org.sifunity.broker.common.dto.QueueDTO;

QueueDTO queue = new QueueDTO(
    UUID.randomUUID(),
    "student-updates",
    environment.getId(), // owner environment ID
    Instant.now(),
    3,      // max retries
    5000L,  // retry delay in ms
    0L      // initial message count
);
```

#### Creating ACL Entries

```java
import org.sifunity.broker.common.dto.AclEntryDTO;
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.AccessLevel;

AclEntryDTO aclEntry = new AclEntryDTO(
    UUID.randomUUID(),
    environment.getId(),
    "StudentPersonal",
    "default",
    "school-district-1",
    AccessType.QUERY,
    AccessLevel.APPROVED
);
```

### Working with SIF Message Envelopes

#### Creating Request Messages

```java
import org.sifunity.broker.common.model.SifMessageEnvelope;
import org.sifunity.broker.common.constant.MessageType;
import org.sifunity.broker.common.constant.ServiceType;

// Create a request envelope (messageId and timestamp auto-generated)
SifMessageEnvelope<String> requestEnvelope = SifMessageEnvelope.<String>builder()
    .messageType(MessageType.REQUEST)
    .serviceName("StudentPersonal")
    .serviceType(ServiceType.OBJECT)
    .zoneId("default")
    .contextId("school-district-1")
    .payload("<?xml version=\"1.0\"?><StudentPersonals>...</StudentPersonals>")
    .build();

// Access envelope properties
UUID messageId = requestEnvelope.getMessageId(); // auto-generated
Instant timestamp = requestEnvelope.getTimestamp(); // auto-generated
```

#### Creating Response Messages

```java
// Create a response envelope with explicit request correlation
SifMessageEnvelope<String> responseEnvelope = SifMessageEnvelope.<String>builder()
    .messageType(MessageType.RESPONSE)
    .serviceName("StudentPersonal")
    .serviceType(ServiceType.OBJECT)
    .zoneId("default")
    .contextId("school-district-1")
    .requestId(requestEnvelope.getMessageId()) // correlate with request
    .payload("<?xml version=\"1.0\"?><StudentPersonal>...</StudentPersonal>")
    .build();
```

#### Creating Event Messages

```java
// Create an event envelope
SifMessageEnvelope<String> eventEnvelope = SifMessageEnvelope.<String>builder()
    .messageType(MessageType.EVENT)
    .serviceName("StudentPersonal")
    .serviceType(ServiceType.OBJECT)
    .zoneId("default")
    .contextId("school-district-1")
    .sourceId(UUID.randomUUID()) // source of the event
    .payload("<?xml version=\"1.0\"?><StudentPersonal eventAction=\"Add\">...</StudentPersonal>")
    .build();
```

### Using Utility Classes

#### SIF Authentication

```java
import org.sifunity.broker.common.util.SifAuthenticationUtils;

// Compute HMAC signature for authentication
String sessionToken = "abc123";
String timestamp = "2024-01-15T10:30:00Z";
String sharedSecret = "my-shared-secret";

String hmacSignature = SifAuthenticationUtils.computeHmac(
    sessionToken, 
    timestamp, 
    sharedSecret
);

// Parse authorization header
String authHeader = "SIF_HMACSHA256 " + sessionToken + ":" + timestamp + ":" + hmacSignature;
SifAuthenticationUtils.AuthHeader parsed = SifAuthenticationUtils.parseAuthHeader(authHeader);

// Validate timestamp (within 5-minute tolerance by default)
SifAuthenticationUtils.validateTimestamp(timestamp);

// Validate with custom tolerance
SifAuthenticationUtils.validateTimestamp(timestamp, 10); // 10 minutes
```

#### SIF Header Processing

```java
import org.sifunity.broker.common.util.SifHeaderUtils;
import org.springframework.http.HttpHeaders;

HttpHeaders headers = new HttpHeaders();
headers.set("Authorization", "SIF_HMACSHA256 token:timestamp:hash");
headers.set("serviceName", "StudentPersonal");
headers.set("zoneId", "default");

// Extract required headers (throws exception if missing)
String serviceName = SifHeaderUtils.getRequiredHeader(headers, "serviceName");
String zoneId = SifHeaderUtils.getRequiredHeader(headers, "zoneId");

// Extract optional headers (returns null if missing)
String contextId = SifHeaderUtils.getOptionalHeader(headers, "contextId");

// Extract UUID headers
UUID requestId = SifHeaderUtils.getUuidHeader(headers, "requestId");

// Validate all required SIF headers at once
SifHeaderUtils.validateRequiredHeaders(headers);
```

#### Storage Key Generation

```java
import org.sifunity.broker.common.util.StorageKeyGenerator;

UUID messageId = UUID.randomUUID();

// Generate hierarchical storage key
String storageKey = StorageKeyGenerator.generateKey(
    "prod-environment",
    "default",
    "school-district-1",
    "StudentPersonal",
    messageId
);
// Result: "prod-environment/default/school-district-1/StudentPersonal/550e8400-e29b-41d4-a716-446655440000.json"

// Generate key without context (uses "default")
String simpleKey = StorageKeyGenerator.generateKey(
    "prod-environment",
    "default",
    "StudentPersonal",
    messageId
);
// Result: "prod-environment/default/default/StudentPersonal/550e8400-e29b-41d4-a716-446655440000.json"
```

#### Validation Utilities

```java
import org.sifunity.broker.common.util.ValidationUtils;

// Validate UUIDs
ValidationUtils.validateUuid("550e8400-e29b-41d4-a716-446655440000", "environmentId");

// Validate service names (must start with letter, alphanumeric + underscore/hyphen)
ValidationUtils.validateServiceName("StudentPersonal"); // valid
ValidationUtils.validateServiceName("Student_Personal"); // valid
ValidationUtils.validateServiceName("123Invalid"); // throws InvalidParameterException

// Validate zone IDs
ValidationUtils.validateZoneId("default"); // valid
ValidationUtils.validateZoneId("school-zone-1"); // valid

// Validate enumerations
ValidationUtils.validateMessageExchangePattern("IMMEDIATE"); // valid
ValidationUtils.validateAccessType("QUERY"); // valid
```

#### Logging with Correlation

```java
import org.sifunity.broker.common.util.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

Logger logger = LoggerFactory.getLogger(MyService.class);

// Set correlation ID for request tracing
LoggingUtils.setCorrelationId(UUID.randomUUID());

// Set full request context
LoggingUtils.setRequestContext(
    environment.getId().toString(),
    "StudentPersonal",
    "default",
    "school-district-1"
);

// All subsequent log entries will include the context
logger.info("Processing student data request");

// Clear context when done (important for thread reuse)
LoggingUtils.clearContext();
```

### Exception Handling

#### Throwing Specific Exceptions

```java
import org.sifunity.broker.common.exception.*;

// Authentication errors (HTTP 401)
throw new InvalidTokenException("Session token has expired");
throw new InvalidTimestampException("Request timestamp is too old");

// Authorization errors (HTTP 403)
throw new InsufficientPermissionsException("No QUERY permission for StudentPersonal");
throw new ZoneAccessDeniedException("Access denied to zone: restricted");

// Routing errors (HTTP 404)
throw new ProviderNotFoundException("No provider found for StudentPersonal in zone default");
throw new QueueNotFoundException("Queue not found: " + queueId);

// Validation errors (HTTP 400)
throw new InvalidRequestException("Malformed XML payload");
throw new InvalidParameterException("zoneId", "Zone ID cannot be empty");

// Storage errors (HTTP 500/503)
throw new StorageException("Failed to save message", cause);
throw new StorageUnavailableException("Message storage is temporarily unavailable");
```

#### Converting Exceptions to SIF Error Responses

```java
import org.sifunity.broker.common.model.SifErrorResponse;

try {
    // Some operation that might fail
    processStudentData();
} catch (SifBrokerException ex) {
    // Convert to SIF-compliant error response
    SifErrorResponse errorResponse = SifErrorResponse.fromException(ex);
    
    // Error response contains:
    // - code: "AUTH_001", "VAL_003", etc.
    // - message: Exception message
    // - scope: "BROKER"
    // - timestamp: Current time
    
    return ResponseEntity
        .status(ex.getHttpStatus())
        .body(errorResponse);
}
```

### Working with Constants and Enumerations

#### Message Exchange Patterns

```java
import org.sifunity.broker.common.constant.MessageExchangePattern;

// Use type-safe enumerations
MessageExchangePattern pattern = MessageExchangePattern.IMMEDIATE;

switch (pattern) {
    case IMMEDIATE -> handleSynchronousRequest();
    case DELAYED -> queueForAsynchronousProcessing();
    case EVENT -> publishToSubscribers();
}

// JSON serialization produces: "IMMEDIATE", "DELAYED", "EVENT"
```

#### Access Control

```java
import org.sifunity.broker.common.constant.AccessType;
import org.sifunity.broker.common.constant.AccessLevel;

AccessType accessType = AccessType.QUERY;
AccessLevel accessLevel = AccessLevel.APPROVED;

if (accessLevel == AccessLevel.APPROVED && accessType == AccessType.QUERY) {
    // Allow data query
}
```

#### SIF Headers

```java
import org.sifunity.broker.common.constant.SifHeaders;

// Use constants instead of magic strings
String authHeader = headers.getFirst(SifHeaders.AUTHORIZATION);
String messageId = headers.getFirst(SifHeaders.MESSAGE_ID);
String zoneId = headers.getFirst(SifHeaders.ZONE_ID);

// Internal headers (added by Gateway)
String environmentId = headers.getFirst(SifHeaders.X_ENVIRONMENT_ID);
String correlationId = headers.getFirst(SifHeaders.X_CORRELATION_ID);
```

## Building and Testing

### Build Commands

```bash
# Clean and compile the project
mvn clean compile

# Run all tests (unit tests + property-based tests)
mvn test

# Run only unit tests
mvn test -Dgroups="!property-based"

# Run only property-based tests
mvn test -Dgroups="property-based"

# Package the library (creates JAR file)
mvn package

# Install to local Maven repository
mvn install

# Generate JavaDoc documentation
mvn javadoc:javadoc

# Generate source and JavaDoc JARs
mvn package -Dmaven.test.skip=true

# Clean everything
mvn clean
```

### Test Coverage

The library includes comprehensive testing:

- **Unit Tests**: Test individual classes and methods with specific examples
- **Property-Based Tests**: Test universal properties across randomized inputs using jqwik
- **Integration Tests**: Test Jackson configuration and utility interactions
- **Validation Tests**: Test Bean Validation annotations and constraints

### Running Specific Test Categories

```bash
# Run tests for a specific package
mvn test -Dtest="org.sifunity.broker.common.dto.*"

# Run tests for a specific class
mvn test -Dtest="EnvironmentDTOTest"

# Run property-based tests only
mvn test -Dtest="**/*Properties"
```

## Configuration

### Jackson Configuration

The library provides a pre-configured Jackson `ObjectMapper` that you can use in your Spring Boot applications:

```java
import org.sifunity.broker.common.config.JacksonConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(JacksonConfiguration.class)
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

The configuration provides:
- **camelCase property naming** for JSON serialization
- **ISO-8601 date formatting** for all date/time fields
- **Null value exclusion** during serialization
- **Strict deserialization** (fails on unknown properties)
- **Java 8 time support** (LocalDateTime, Instant, etc.)

### Validation Configuration

To enable Bean Validation in your Spring Boot application:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

Then use `@Valid` annotations in your controllers:

```java
@PostMapping("/environments/environment")
public ResponseEntity<EnvironmentDTO> createEnvironment(@Valid @RequestBody EnvironmentDTO environment) {
    // Validation is automatically performed
    return ResponseEntity.ok(environment);
}
```

### Logging Configuration

For structured logging with correlation IDs, configure your `logback-spring.xml`:

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <timestamp/>
                <logLevel/>
                <loggerName/>
                <mdc/>
                <message/>
            </providers>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

## Testing Utilities

### Using Test Data Generators

```java
import org.sifunity.broker.common.generator.TestDataGenerators;
import net.jqwik.api.*;

@Property
void testEnvironmentProcessing(@ForAll("environments") EnvironmentDTO environment) {
    // Test with randomly generated environment data
    assertThat(environment.getId()).isNotNull();
    assertThat(environment.getApplicationName()).isNotBlank();
}
```

### Using Test Builders

```java
import org.sifunity.broker.common.builder.TestBuilders;

@Test
void testQueueCreation() {
    // Create test data with sensible defaults
    EnvironmentDTO environment = TestBuilders.environment()
        .applicationName("TestApp")
        .type(EnvironmentType.DIRECT)
        .build();
    
    QueueDTO queue = TestBuilders.queue()
        .name("test-queue")
        .ownerId(environment.getId())
        .maxRetries(5)
        .build();
    
    assertThat(queue.getName()).isEqualTo("test-queue");
    assertThat(queue.getMaxRetries()).isEqualTo(5);
}
```

### Using Assertion Helpers

```java
import org.sifunity.broker.common.assertion.SifAssertions;

@Test
void testSifEnvelopeCreation() {
    SifMessageEnvelope<String> envelope = createTestEnvelope();
    
    // Validate SIF compliance
    SifAssertions.assertValidSifEnvelope(envelope);
    
    // Validate storage key format
    String storageKey = StorageKeyGenerator.generateKey("env", "zone", "context", "service", UUID.randomUUID());
    SifAssertions.assertStorageKeyFormat(storageKey);
}
```

## API Reference

### Core DTOs

| DTO | Purpose | Key Fields |
|-----|---------|------------|
| `EnvironmentDTO` | Application environment registration | id, applicationName, sessionToken, type |
| `QueueDTO` | Message queue configuration | id, name, ownerId, maxRetries, retryDelayMs |
| `MessageDTO` | Queued message metadata | id, queueId, storageKey, status, deliveryAttempts |
| `AclEntryDTO` | Access control permissions | environmentId, serviceName, accessType, accessLevel |
| `SubscriptionDTO` | Event/request subscriptions | queueId, serviceName, zoneId, type |
| `ProviderRegistrationDTO` | Service provider registration | environmentId, serviceName, endpoint, supportedPatterns |
| `JobDTO` | Functional service job | environmentId, serviceName, state, currentPhase |

### Exception Hierarchy

| Exception | HTTP Status | Error Code | Usage |
|-----------|-------------|------------|-------|
| `InvalidTokenException` | 401 | AUTH_001 | Invalid or expired session token |
| `InvalidTimestampException` | 401 | AUTH_002 | Request timestamp outside tolerance |
| `MalformedAuthHeaderException` | 401 | AUTH_003 | Malformed Authorization header |
| `InsufficientPermissionsException` | 403 | AUTHZ_001 | Missing required permissions |
| `ZoneAccessDeniedException` | 403 | AUTHZ_002 | Access denied to zone |
| `ProviderNotFoundException` | 404 | ROUTE_001 | No provider found for service |
| `QueueNotFoundException` | 404 | ROUTE_002 | Queue not found |
| `SubscriptionNotFoundException` | 404 | ROUTE_003 | Subscription not found |
| `InvalidRequestException` | 400 | VAL_001 | Invalid request format/content |
| `InvalidParameterException` | 400 | VAL_003 | Invalid parameter value |
| `StorageException` | 500 | STORAGE_001 | Storage operation failed |
| `StorageUnavailableException` | 503 | STORAGE_002 | Storage temporarily unavailable |
| `ProviderUnavailableException` | 503 | SERVICE_001 | Provider service unavailable |

### Utility Classes

| Utility | Purpose | Key Methods |
|---------|---------|-------------|
| `SifHeaderUtils` | SIF header processing | `getRequiredHeader()`, `validateRequiredHeaders()` |
| `SifAuthenticationUtils` | HMAC authentication | `computeHmac()`, `parseAuthHeader()`, `validateTimestamp()` |
| `StorageKeyGenerator` | Storage key generation | `generateKey()` |
| `ValidationUtils` | Input validation | `validateUuid()`, `validateServiceName()`, `validateZoneId()` |
| `LoggingUtils` | Structured logging | `setCorrelationId()`, `setRequestContext()` |

### Constants and Enumerations

| Enum/Constant | Values | Purpose |
|---------------|--------|---------|
| `MessageExchangePattern` | IMMEDIATE, DELAYED, EVENT | Message delivery patterns |
| `AccessType` | QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE | Permission types (SIF 3.7 `rightTypeType`) |
| `AccessLevel` | APPROVED, SUPPORTED, REJECTED, UNSUPPORTED, REQUESTED | Permission status (SIF 3.7 `rightValueType`) |
| `MessageType` | REQUEST, RESPONSE, EVENT | SIF message types |
| `ServiceType` | OBJECT, FUNCTIONAL, UTILITY | SIF service categories |
| `EnvironmentType` | DIRECT, BROKERED, ADMIN | Environment topology (SIF 3.7 `environmentTypeType`) |
| `MessageStatus` | PENDING, DELIVERED, ACKNOWLEDGED, FAILED | Message delivery status |
| `SifHeaders` | Header name constants | Standard SIF header names |
| `ErrorCodes` | Error code constants | Standardized error codes |

## Dependency Configuration

### Required Dependencies

When using this library, your project should include these dependencies:

```xml
<dependencies>
    <!-- SIF Broker Common Library -->
    <dependency>
        <groupId>org.sifunity.broker</groupId>
        <artifactId>sif-broker-common</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    
    <!-- Spring Boot Starter (if using Spring Boot) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    
    <!-- Bean Validation (for DTO validation) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- Jackson (for JSON processing) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-json</artifactId>
    </dependency>
</dependencies>
```

### Optional Dependencies

For enhanced functionality:

```xml
<!-- For structured JSON logging -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>

<!-- For property-based testing in your own tests -->
<dependency>
    <groupId>net.jqwik</groupId>
    <artifactId>jqwik</artifactId>
    <version>1.8.2</version>
    <scope>test</scope>
</dependency>
```

### Version Compatibility

| Component | Version | Notes |
|-----------|---------|-------|
| Java | 17+ | Required minimum version |
| Spring Boot | 3.2.x | Tested and supported |
| Jackson | 2.15.x | Included with Spring Boot |
| Bean Validation | 3.0.x | Jakarta EE 9+ |
| Maven | 3.6+ | For building |

## Documentation

### Generated Documentation

- **JavaDoc**: Complete API documentation is generated during build
  ```bash
  mvn javadoc:javadoc
  # View at: target/site/apidocs/index.html
  ```

### Specification Documents

- **Requirements**: [.kiro/specs/sif-broker-common/requirements.md](.kiro/specs/sif-broker-common/requirements.md)
- **Design**: [.kiro/specs/sif-broker-common/design.md](.kiro/specs/sif-broker-common/design.md)
- **Tasks**: [.kiro/specs/sif-broker-common/tasks.md](.kiro/specs/sif-broker-common/tasks.md)

### SIF 3.7 Compliance

This library implements components that comply with the SIF 3.7 specification:
- Message envelope structure follows SIF 3.7 standards
- Error response format matches SIF 3.7 error specification
- Authentication uses SIF 3.7 HMAC-SHA256 method
- Header names and values conform to SIF 3.7 requirements

## Versioning and Releases

### Semantic Versioning

This library follows [Semantic Versioning](https://semver.org/):

- **MAJOR** (x.0.0): Breaking changes that require code updates
  - Removing or renaming public classes, methods, or fields
  - Changing method signatures or return types
  - Changing DTO field types or validation rules

- **MINOR** (0.x.0): New features that are backward-compatible
  - Adding new classes, methods, or fields
  - Adding new DTOs or enumerations
  - Adding new utility methods or constants

- **PATCH** (0.0.x): Bug fixes that are backward-compatible
  - Fixing bugs without changing public APIs
  - Improving performance or documentation
  - Updating dependencies (non-breaking)

### Release Process

1. **Development**: Work on `develop` branch
2. **Testing**: Ensure all tests pass and coverage is maintained
3. **Documentation**: Update JavaDoc and README as needed
4. **Version**: Update version in `pom.xml`
5. **Tag**: Create git tag for release
6. **Publish**: Deploy to artifact repository

### Current Version

- **Version**: 1.0.0-SNAPSHOT
- **Status**: Development
- **Target**: First stable release

## Contributing

### Development Guidelines

When contributing to this library:

1. **Code Style**: Follow existing code conventions and formatting
2. **Testing**: Add comprehensive tests for new functionality
   - Unit tests for specific behavior
   - Property-based tests for universal properties
   - Integration tests for complex interactions
3. **Documentation**: Update JavaDoc for all public APIs
4. **Backward Compatibility**: Avoid breaking changes in minor/patch releases
5. **SIF Compliance**: Ensure all changes maintain SIF 3.7 compliance

### Pull Request Process

1. Create feature branch from `develop`
2. Implement changes with tests
3. Update documentation
4. Ensure all tests pass: `mvn test`
5. Submit pull request with clear description

### Code Quality Standards

- **Test Coverage**: Maintain >80% code coverage
- **JavaDoc**: All public classes and methods must have documentation
- **Validation**: Use Bean Validation annotations where appropriate
- **Immutability**: Keep DTOs immutable (final fields, no setters)
- **Thread Safety**: Ensure utility classes are thread-safe

## Troubleshooting

### Common Issues

#### Build Failures

**Problem**: Maven build fails with compilation errors
```
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin
```

**Solution**: Ensure Java 17 is installed and `JAVA_HOME` is set correctly
```bash
java -version  # Should show Java 17
echo $JAVA_HOME  # Should point to Java 17 installation
```

#### Test Failures

**Problem**: Property-based tests fail intermittently
```
[ERROR] Property test failed with shrunk sample
```

**Solution**: Property-based tests use randomized inputs. Check the failing example and adjust test constraints or fix the underlying issue.

#### Dependency Conflicts

**Problem**: Jackson version conflicts in consuming applications
```
[ERROR] Conflicting Jackson versions detected
```

**Solution**: Use Spring Boot's dependency management to align versions:
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>3.2.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

#### Validation Not Working

**Problem**: Bean Validation annotations are ignored
```java
@NotNull String field; // Not validated
```

**Solution**: Ensure validation starter is included and `@Valid` is used:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

```java
@PostMapping("/endpoint")
public ResponseEntity<?> create(@Valid @RequestBody MyDTO dto) {
    // Validation happens automatically
}
```

### Getting Help

For issues with this library:

1. **Check Documentation**: Review this README and JavaDoc
2. **Search Issues**: Look for similar problems in project issues
3. **Create Issue**: Submit detailed bug report or feature request
4. **Contact Team**: Reach out to SIF Unity Broker development team

## License

This project is licensed under the MIT License. See [LICENSE](../LICENSE) for details.

This library is part of the SIF Unity Broker system and is subject to the project's licensing terms.

## Contact

For questions, issues, or contributions:

- **Project**: SIF Unity Broker
- **Team**: SIF Unity Broker Development Team
- **Documentation**: See `.kiro/specs/sif-broker-common/` for detailed specifications

---

*This README was generated for sif-broker-common library version 1.0.0-SNAPSHOT*
