# Environment Service

The Environment Service is a foundational Core Service in the SIF Unity Broker system responsible for managing application registration, access control lists (ACLs), provider service registry, and providing authentication/authorization validation APIs for the API Gateway.

## Overview

As a Core Service, the Environment Service has direct database access and owns specific database tables. It provides both external APIs (for environment registration and management) and internal APIs (for Gateway authentication/authorization validation).

For detailed requirements and design specifications, see:
- [Requirements Document](.kiro/specs/environment-service/requirements.md)
- [Design Document](.kiro/specs/environment-service/design.md)
- [Implementation Tasks](.kiro/specs/environment-service/tasks.md)

### Key Responsibilities

1. **Environment Management**: Register applications, generate credentials (sessionToken and sharedSecret), manage lifecycle
2. **Access Control**: Manage ACL entries, validate permissions for service access
3. **Provider Registry**: Track provider service offerings, endpoints, and capabilities
4. **Zone/Context Management**: Organize services into logical groupings for multi-tenant operation
5. **Authentication Validation**: Validate sessionTokens and HMAC signatures for Gateway
6. **Authorization Validation**: Check ACL permissions for Gateway requests

## Technology Stack

- **Framework**: Spring Boot 3.x
- **Language**: Java 17+ (Amazon Corretto)
- **Database**: PostgreSQL with Spring Data JPA
- **Migration**: Flyway for schema versioning
- **Validation**: Bean Validation (javax.validation)
- **Security**: Spring Security (for internal API protection)
- **Shared Library**: sif-broker-common (DTOs, exceptions, utilities)
- **Testing**: JUnit 5, jqwik (property-based testing), Testcontainers

## API Endpoints

### External APIs (exposed through Gateway)

Most external APIs require authentication except for environment registration.

#### Environment Management

**Register Environment** (No authentication required)
```
POST /environments/environment
Content-Type: application/json

Request Body:
{
  "applicationName": "MyApp",
  "type": "DIRECT",  // DIRECT, BROKERED, or ADMIN — describes topology, not consumer/provider role
  "defaultZoneId": "default",
  "defaultContextId": "production"  // optional
}

Response (201 Created):
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "applicationName": "MyApp",
  "sessionToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "sharedSecret": "base64EncodedSecret...",  // Only returned on registration
  "createdAt": "2024-01-15T10:30:00Z",
  "defaultZoneId": "default",
  "defaultContextId": "production",
  "type": "DIRECT"
}
```

**Get Environment**
```
GET /environments/{id}

Response (200 OK):
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "applicationName": "MyApp",
  "createdAt": "2024-01-15T10:30:00Z",
  "defaultZoneId": "default",
  "defaultContextId": "production",
  "type": "DIRECT"
  // Note: sharedSecret is never returned in queries
}
```

**List Environments**
```
GET /environments?type=DIRECT&page=0&size=20

Response (200 OK):
{
  "content": [...],
  "totalElements": 42,
  "totalPages": 3,
  "size": 20,
  "number": 0
}
```

**Delete Environment**
```
DELETE /environments/{id}

Response (204 No Content)
// Cascades to delete all ACL entries and provider registrations
```

#### Access Control List (ACL) Management

**Create ACL Entry**
```
POST /acl-entries
Content-Type: application/json

Request Body:
{
  "environmentId": "550e8400-e29b-41d4-a716-446655440000",
  "serviceName": "StudentPersonals",
  "zoneId": "default",
  "contextId": "production",  // optional
  "accessType": "QUERY",  // QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, or PROVIDE
  "accessLevel": "APPROVED"  // APPROVED, SUPPORTED, REJECTED, UNSUPPORTED, or REQUESTED
}

Response (201 Created):
{
  "id": "660e8400-e29b-41d4-a716-446655440001",
  "environmentId": "550e8400-e29b-41d4-a716-446655440000",
  "serviceName": "StudentPersonals",
  "zoneId": "default",
  "contextId": "production",
  "accessType": "QUERY",
  "accessLevel": "APPROVED"
}
```

**List ACL Entries**
```
GET /acl-entries?environmentId=550e8400-e29b-41d4-a716-446655440000

Response (200 OK):
[
  {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "environmentId": "550e8400-e29b-41d4-a716-446655440000",
    "serviceName": "StudentPersonals",
    "zoneId": "default",
    "contextId": "production",
    "accessType": "QUERY",
    "accessLevel": "APPROVED"
  }
]
```

**Update ACL Entry**
```
PUT /acl-entries/{id}
Content-Type: application/json

Request Body:
{
  "accessLevel": "REJECTED"
}

Response (200 OK):
{
  "id": "660e8400-e29b-41d4-a716-446655440001",
  "accessLevel": "REJECTED",
  ...
}
```

**Delete ACL Entry**
```
DELETE /acl-entries/{id}

Response (204 No Content)
```

#### Provider Registry Management

**Register Provider**
```
POST /providers/provider
Content-Type: application/json

Request Body:
{
  "environmentId": "550e8400-e29b-41d4-a716-446655440000",
  "serviceName": "StudentPersonals",
  "zoneId": "default",
  "contextId": "production",  // optional
  "endpoint": "https://provider.example.com/api",
  "supportedPatterns": ["IMMEDIATE", "DELAYED"]  // IMMEDIATE, DELAYED, EVENT
}

Response (201 Created):
{
  "id": "770e8400-e29b-41d4-a716-446655440002",
  "environmentId": "550e8400-e29b-41d4-a716-446655440000",
  "serviceName": "StudentPersonals",
  "zoneId": "default",
  "contextId": "production",
  "endpoint": "https://provider.example.com/api",
  "supportedPatterns": ["IMMEDIATE", "DELAYED"],
  "registeredAt": "2024-01-15T10:35:00Z"
}
```

**Lookup Provider**
```
GET /providers/lookup?serviceName=StudentPersonals&zoneId=default&contextId=production

Response (200 OK):
{
  "id": "770e8400-e29b-41d4-a716-446655440002",
  "environmentId": "550e8400-e29b-41d4-a716-446655440000",
  "serviceName": "StudentPersonals",
  "zoneId": "default",
  "contextId": "production",
  "endpoint": "https://provider.example.com/api",
  "supportedPatterns": ["IMMEDIATE", "DELAYED"],
  "registeredAt": "2024-01-15T10:35:00Z"
}
```

**List Provider Registrations**
```
GET /providers?environmentId=550e8400-e29b-41d4-a716-446655440000

Response (200 OK):
[...]
```

**Delete Provider Registration**
```
DELETE /providers/{id}

Response (204 No Content)
```

#### Zone and Context Management

**Create Zone**
```
POST /zones
Content-Type: application/json

Request Body:
{
  "zoneId": "school-district-1",  // alphanumeric, underscore, hyphen only
  "description": "School District 1 Zone"
}

Response (201 Created):
{
  "id": "880e8400-e29b-41d4-a716-446655440003",
  "zoneId": "school-district-1",
  "description": "School District 1 Zone",
  "createdAt": "2024-01-15T10:40:00Z"
}
```

**List Zones**
```
GET /zones

Response (200 OK):
[...]
```

**Create Context**
```
POST /zones/{zoneId}/contexts
Content-Type: application/json

Request Body:
{
  "contextId": "production",  // alphanumeric, underscore, hyphen only
  "description": "Production environment"
}

Response (201 Created):
{
  "id": "990e8400-e29b-41d4-a716-446655440004",
  "zoneId": "school-district-1",
  "contextId": "production",
  "description": "Production environment",
  "createdAt": "2024-01-15T10:45:00Z"
}
```

**List Contexts**
```
GET /zones/{zoneId}/contexts

Response (200 OK):
[...]
```

### Internal APIs (service-to-service, NOT exposed through Gateway)

These APIs are used by the API Gateway for authentication and authorization validation.

**Validate Token**
```
POST /internal/validate-token
Content-Type: application/json

Request Body:
{
  "sessionToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": "2024-01-15T10:50:00Z",
  "hash": "base64EncodedHmacHash..."
}

Response (200 OK):
{
  "environmentId": "550e8400-e29b-41d4-a716-446655440000",
  "applicationName": "MyApp",
  "type": "DIRECT",
  "defaultZoneId": "default",
  "defaultContextId": "production"
}

Response (401 Unauthorized) - if token invalid, HMAC mismatch, or timestamp outside tolerance
```

**Check Permission**
```
POST /internal/check-permission
Content-Type: application/json

Request Body:
{
  "environmentId": "550e8400-e29b-41d4-a716-446655440000",
  "serviceName": "StudentPersonals",
  "zoneId": "default",
  "contextId": "production",
  "accessType": "QUERY"
}

Response (200 OK):
{
  "granted": true
}

Response (403 Forbidden) - if no APPROVED ACL entry exists
```

## Database Schema

The Environment Service owns and manages the following PostgreSQL database tables. Schema migrations are managed by Flyway.

### Tables Overview

#### environments
Stores application registrations with authentication credentials.

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| application_name | VARCHAR(255) | Unique application name |
| session_token | VARCHAR(255) | Unique UUID for authentication |
| shared_secret | VARCHAR(512) | Base64-encoded secret for HMAC (256+ bits) |
| created_at | TIMESTAMP | Registration timestamp |
| expires_at | TIMESTAMP | Optional expiration timestamp |
| default_zone_id | VARCHAR(255) | Default zone for this environment |
| default_context_id | VARCHAR(255) | Optional default context |
| type | VARCHAR(50) | DIRECT, BROKERED, or ADMIN — describes topology per SIF 3.7 |

**Indexes:**
- `idx_environments_session_token` on `session_token` (for fast authentication lookups)
- `idx_environments_type` on `type` (for filtering)

**Constraints:**
- Unique constraint on `application_name`
- Unique constraint on `session_token`
- Check constraint on `type` (DIRECT, BROKERED, ADMIN)

#### zones
Defines logical zones for organizing services.

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| zone_id | VARCHAR(255) | Unique zone identifier (alphanumeric, _, -) |
| description | TEXT | Optional zone description |
| created_at | TIMESTAMP | Creation timestamp |

**Indexes:**
- `idx_zones_zone_id` on `zone_id`

**Constraints:**
- Unique constraint on `zone_id`

#### contexts
Defines contexts within zones for additional routing dimensions.

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| zone_id | VARCHAR(255) | Foreign key to zones.zone_id |
| context_id | VARCHAR(255) | Context identifier within zone |
| description | TEXT | Optional context description |
| created_at | TIMESTAMP | Creation timestamp |

**Indexes:**
- `idx_contexts_zone_id` on `zone_id`

**Constraints:**
- Foreign key to `zones(zone_id)` with CASCADE delete
- Unique constraint on `(zone_id, context_id)`

#### acl_entries
Access control list entries defining service permissions.

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| environment_id | UUID | Foreign key to environments.id |
| service_name | VARCHAR(255) | SIF service name (e.g., StudentPersonals) |
| zone_id | VARCHAR(255) | Zone for this permission |
| context_id | VARCHAR(255) | Optional context for this permission |
| access_type | VARCHAR(50) | QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, or PROVIDE |
| access_level | VARCHAR(50) | APPROVED, SUPPORTED, REJECTED, UNSUPPORTED, or REQUESTED |

**Indexes:**
- `idx_acl_environment_id` on `environment_id`
- `idx_acl_lookup` on `(environment_id, service_name, zone_id, context_id, access_type)` (for permission checks)

**Constraints:**
- Foreign key to `environments(id)` with CASCADE delete
- Unique constraint on `(environment_id, service_name, zone_id, context_id, access_type)`
- Check constraint on `access_type` (QUERY, CREATE, UPDATE, DELETE, SUBSCRIBE, PROVIDE)
- Check constraint on `access_level` (APPROVED, SUPPORTED, REJECTED, UNSUPPORTED, REQUESTED)

#### provider_registry
Registry of provider services and their endpoints.

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| environment_id | UUID | Foreign key to environments.id |
| service_name | VARCHAR(255) | SIF service name |
| zone_id | VARCHAR(255) | Zone where provider operates |
| context_id | VARCHAR(255) | Optional context |
| endpoint | VARCHAR(1024) | Provider service URL |
| supported_patterns | VARCHAR(255) | Comma-separated: IMMEDIATE,DELAYED,EVENT |
| registered_at | TIMESTAMP | Registration timestamp |

**Indexes:**
- `idx_provider_environment_id` on `environment_id`
- `idx_provider_lookup` on `(service_name, zone_id, context_id)` (for provider discovery)

**Constraints:**
- Foreign key to `environments(id)` with CASCADE delete
- Unique constraint on `(service_name, zone_id, context_id)` (one provider per service/zone/context)

### Database Migrations

Flyway migration files are located in `src/main/resources/db/migration/`:

- `V1__create_environments_table.sql` - Creates environments table
- `V2__create_zones_and_contexts_tables.sql` - Creates zones and contexts tables
- `V3__create_acl_entries_table.sql` - Creates acl_entries table
- `V4__create_provider_registry_table.sql` - Creates provider_registry table

To run migrations manually:
```bash
./mvnw flyway:migrate
```

## Setup and Running

### Prerequisites

- **Java 17 or higher** (Amazon Corretto recommended)
- **PostgreSQL 12 or higher**
- **Maven 3.9+** (or use included Maven wrapper `./mvnw`)
- **Docker** (optional, for containerized deployment)

### Local Development Setup

#### 1. Database Setup

Create PostgreSQL database and user:

```sql
-- Connect to PostgreSQL as superuser
psql -U postgres

-- Create database and user
CREATE DATABASE sif_broker;
CREATE USER sif_broker WITH PASSWORD 'changeme';
GRANT ALL PRIVILEGES ON DATABASE sif_broker TO sif_broker;

-- Grant schema privileges (PostgreSQL 15+)
\c sif_broker
GRANT ALL ON SCHEMA public TO sif_broker;
```

#### 2. Configure Environment

Set database credentials:

```bash
export DB_USERNAME=sif_broker
export DB_PASSWORD=changeme
```

Or create `.env` file in project root:
```
DB_USERNAME=sif_broker
DB_PASSWORD=changeme
```

#### 3. Build the Application

```bash
# Clean and build
./mvnw clean package

# Skip tests for faster build
./mvnw clean package -DskipTests
```

#### 4. Run Database Migrations

Flyway will automatically run migrations on startup, or run manually:

```bash
./mvnw flyway:migrate
```

To check migration status:
```bash
./mvnw flyway:info
```

#### 5. Run the Application

```bash
# Using Maven wrapper
./mvnw spring-boot:run

# Or run the JAR directly
java -jar target/environment-service-1.0.0-SNAPSHOT.jar

# With custom profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# With JVM options
JAVA_OPTS="-Xmx512m -Xms256m" ./mvnw spring-boot:run
```

The service will start on **http://localhost:8081**

#### 6. Verify Service is Running

```bash
# Check health endpoint
curl http://localhost:8081/actuator/health

# Expected response:
# {"status":"UP"}
```

### Docker Deployment

#### Build Docker Image

```bash
# Build the application first
./mvnw clean package

# Build Docker image
docker build -t environment-service:latest .

# Or use docker-compose to build
docker-compose build
```

#### Run with Docker Compose

The `docker-compose.yml` includes both PostgreSQL and the Environment Service:

```bash
# Start all services
docker-compose up

# Start in detached mode
docker-compose up -d

# View logs
docker-compose logs -f environment-service

# Stop services
docker-compose down

# Stop and remove volumes (clears database)
docker-compose down -v
```

#### Run Docker Container Manually

```bash
# Run PostgreSQL
docker run -d \
  --name postgres \
  -e POSTGRES_DB=sif_broker \
  -e POSTGRES_USER=sif_broker \
  -e POSTGRES_PASSWORD=changeme \
  -p 5432:5432 \
  postgres:15-alpine

# Run Environment Service
docker run -d \
  --name environment-service \
  --link postgres:postgres \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/sif_broker \
  -e SPRING_DATASOURCE_USERNAME=sif_broker \
  -e SPRING_DATASOURCE_PASSWORD=changeme \
  -p 8081:8081 \
  environment-service:latest
```

### Kubernetes Deployment

For Kubernetes deployment, see the deployment configuration files in the `k8s/` directory (if available) or refer to the deployment specification document.

### Troubleshooting

**Database Connection Issues:**
```bash
# Check PostgreSQL is running
pg_isready -h localhost -p 5432

# Check database exists
psql -U sif_broker -d sif_broker -c "SELECT 1"

# View connection pool metrics
curl http://localhost:8081/actuator/metrics/hikaricp.connections
```

**Migration Issues:**
```bash
# Check migration status
./mvnw flyway:info

# Repair failed migrations
./mvnw flyway:repair

# Baseline existing database
./mvnw flyway:baseline
```

**Port Already in Use:**
```bash
# Change port via environment variable
export SERVER_PORT=8082
./mvnw spring-boot:run

# Or in application.yml
server:
  port: 8082
```

## Configuration

### Application Properties

The service is configured via `application.yml`. Key configuration sections:

#### Database Configuration
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/sif_broker
    username: ${DB_USERNAME:sif_broker}
    password: ${DB_PASSWORD:changeme}
    hikari:
      maximum-pool-size: 10        # Max connections
      minimum-idle: 2               # Min idle connections
      connection-timeout: 30000     # 30 seconds
      idle-timeout: 600000          # 10 minutes
      max-lifetime: 1800000         # 30 minutes
```

#### JPA/Hibernate Configuration
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate            # Flyway manages schema
    show-sql: false                 # Set to true for SQL logging
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc:
          batch_size: 20            # Batch insert/update size
        order_inserts: true         # Optimize batch operations
        order_updates: true
```

#### Flyway Configuration
```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true       # Allow migration on existing DB
    validate-on-migrate: true       # Validate migrations
```

#### Server Configuration
```yaml
server:
  port: 8081                        # HTTP port
  compression:
    enabled: true                   # Enable response compression
    mime-types: application/json,application/xml,text/html
```

#### Authentication Configuration
```yaml
authentication:
  timestamp-tolerance-minutes: 5    # HMAC timestamp tolerance
```

#### Management Endpoints
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    export:
      prometheus:
        enabled: true               # Enable Prometheus metrics
```

#### Logging Configuration
```yaml
logging:
  level:
    org.sifunity.broker.environment: INFO
    org.springframework.web: INFO
    org.springframework.security: INFO
    org.hibernate.SQL: WARN         # Set to DEBUG to see SQL
    org.flywaydb: INFO
```

### Environment Variables

The following environment variables can be used to override configuration:

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_USERNAME` | Database username | `sif_broker` |
| `DB_PASSWORD` | Database password | `changeme` |
| `SPRING_DATASOURCE_URL` | Full JDBC URL | `jdbc:postgresql://localhost:5432/sif_broker` |
| `SERVER_PORT` | HTTP port | `8081` |
| `AUTHENTICATION_TIMESTAMP_TOLERANCE_MINUTES` | HMAC timestamp tolerance | `5` |
| `JAVA_OPTS` | JVM options | (see Dockerfile) |
| `LOGGING_LEVEL_ORG_SIFUNITY_BROKER_ENVIRONMENT` | Log level | `INFO` |

### Profile-Specific Configuration

**Development Profile** (`application-dev.yml`)
- Verbose logging
- SQL statement logging
- H2 console enabled (if using H2)

**Test Profile** (`application-test.yml`)
- Testcontainers configuration
- In-memory or test database
- Reduced connection pool size

**Production Profile** (`application-prod.yml`)
- Optimized connection pooling
- Minimal logging
- Security hardening
- Metrics and monitoring enabled

Activate a profile:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Or via environment variable:
```bash
export SPRING_PROFILES_ACTIVE=prod
./mvnw spring-boot:run
```

## Testing

The service includes comprehensive testing with three complementary approaches:

### Test Types

#### 1. Unit Tests
Test individual components in isolation with mocked dependencies.

**Controller Tests** (`src/test/java/.../controller/`)
- Test REST endpoints using MockMvc
- Verify request/response mapping and validation
- Mock service layer dependencies

**Service Tests** (`src/test/java/.../service/`)
- Test business logic with mocked repositories
- Verify transaction boundaries
- Test error handling and validation

**Repository Tests** (`src/test/java/.../repository/`)
- Test data access with Testcontainers PostgreSQL
- Verify custom queries and relationships
- Test cascade deletes and constraints

#### 2. Property-Based Tests
Test universal correctness properties across randomized inputs using jqwik.

**30 Correctness Properties** (`src/test/java/.../property/`)
- Each property runs minimum 100 iterations
- Tests universal behaviors that should hold for all valid inputs
- Examples:
  - Environment creation persistence
  - SessionToken uniqueness and format
  - SharedSecret security requirements (256+ bits)
  - ACL permission checking logic
  - HMAC signature validation
  - Timestamp tolerance validation

**Example Property Test:**
```java
@Property
void environmentCreationPersistence(
    @ForAll("validEnvironmentRequests") EnvironmentRegistrationRequest request
) {
    // Create environment
    EnvironmentRegistrationResponse response = environmentService.registerEnvironment(request);
    
    // Retrieve by ID
    EnvironmentDTO retrieved = environmentService.getEnvironment(response.id());
    
    // Verify data matches
    assertThat(retrieved.getApplicationName()).isEqualTo(request.applicationName());
    assertThat(retrieved.getType()).isEqualTo(request.type());
}
```

#### 3. Integration Tests
Test end-to-end flows with full Spring context and real database.

**Integration Test Scenarios** (`src/test/java/.../integration/`)
- Environment lifecycle (register → query → delete)
- Authentication flow (token validation with HMAC)
- Authorization flow (ACL permission checking)
- Provider registry (register → lookup → delete)
- Uses Testcontainers for PostgreSQL
- Tests both H2 (fast) and PostgreSQL (production-like)

### Running Tests

**Run all tests:**
```bash
./mvnw test
```

**Run only unit tests:**
```bash
./mvnw test -Dtest="**/*Test"
```

**Run only property-based tests:**
```bash
./mvnw test -Dtest="**/*PropertyTest"
```

**Run only integration tests:**
```bash
./mvnw test -Dtest="**/*IntegrationTest"
```

**Run specific test class:**
```bash
./mvnw test -Dtest=EnvironmentServiceTest
```

**Run tests with coverage:**
```bash
./mvnw test jacoco:report
# View report at target/site/jacoco/index.html
```

### Test Coverage

The service maintains high test coverage:
- **Unit Tests**: Cover all service methods, controllers, and repositories
- **Property Tests**: Validate 30 correctness properties from design document
- **Integration Tests**: Cover 8 end-to-end scenarios
- **Target Coverage**: 80%+ code coverage

### Test Data Generators

Property-based tests use custom generators (`src/test/java/.../generator/`) for:
- Valid environment registration requests
- Random sessionTokens (UUIDs) and sharedSecrets (256+ bits)
- ACL entries with various permissions
- Provider registrations with different patterns
- Zone IDs and context IDs following naming patterns
- Invalid data for validation testing

## Health Checks

The service exposes health check endpoints via Spring Boot Actuator:

### Available Endpoints

- **`GET /actuator/health`** - Overall application health status
  ```json
  {
    "status": "UP",
    "components": {
      "db": {
        "status": "UP",
        "details": {
          "database": "PostgreSQL",
          "validationQuery": "isValid()"
        }
      },
      "diskSpace": {
        "status": "UP"
      }
    }
  }
  ```

- **`GET /actuator/info`** - Application information
- **`GET /actuator/metrics`** - Available metrics list
- **`GET /actuator/metrics/{metric}`** - Specific metric details
- **`GET /actuator/prometheus`** - Prometheus-formatted metrics

### Docker Health Check

The Dockerfile includes a health check that polls the `/actuator/health` endpoint:

```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1
```

### Kubernetes Liveness and Readiness Probes

Example Kubernetes probe configuration:

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8081
  initialDelaySeconds: 60
  periodSeconds: 10
  
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8081
  initialDelaySeconds: 30
  periodSeconds: 5
```

## Authentication and Authorization Flow

### How It Works

The Environment Service provides the authentication and authorization backend for the API Gateway:

#### 1. Environment Registration
```
Application → Gateway → Environment Service
  POST /environments/environment
  ← Returns: sessionToken + sharedSecret
```

#### 2. Authenticated Request Flow
```
1. Application computes HMAC:
   data = sessionToken + ":" + timestamp
   hash = HMAC-SHA256(data, sharedSecret)
   
2. Application sends request:
   Authorization: SIF_HMACSHA256 <sessionToken>:<timestamp>:<hash>
   
3. Gateway extracts credentials and calls:
   POST /internal/validate-token
   {
     "sessionToken": "<sessionToken>",
     "timestamp": "<timestamp>",
     "hash": "<hash>"
   }
   
4. Environment Service validates:
   - Looks up environment by sessionToken
   - Validates timestamp is within tolerance (5 minutes)
   - Computes expected HMAC using stored sharedSecret
   - Compares computed hash with provided hash
   
5. If valid:
   ← Returns environment details
   Gateway proceeds with request
   
6. If invalid:
   ← Returns 401 Unauthorized
   Gateway rejects request
```

#### 3. Authorization Check Flow
```
1. Gateway has authenticated environment
2. Gateway extracts service/zone/context from request
3. Gateway calls:
   POST /internal/check-permission
   {
     "environmentId": "<uuid>",
     "serviceName": "StudentPersonals",
     "zoneId": "default",
     "contextId": "production",
     "accessType": "QUERY"
   }
   
4. Environment Service checks ACL:
   - Queries acl_entries table
   - Looks for exact match on all parameters
   - Checks if accessLevel = APPROVED
   
5. If APPROVED entry exists:
   ← Returns {"granted": true}
   Gateway allows request
   
6. If no APPROVED entry:
   ← Returns {"granted": false}
   Gateway returns 403 Forbidden
```

### HMAC Computation

The service uses HMAC-SHA256 for authentication:

```java
// Compute HMAC (from sif-broker-common)
String data = sessionToken + ":" + timestamp;
Mac mac = Mac.getInstance("HmacSHA256");
SecretKeySpec key = new SecretKeySpec(sharedSecret.getBytes(), "HmacSHA256");
mac.init(key);
byte[] hmacBytes = mac.doFinal(data.getBytes());
String hash = Base64.getEncoder().encodeToString(hmacBytes);
```

### Timestamp Tolerance

- Default tolerance: **5 minutes**
- Configurable via `authentication.timestamp-tolerance-minutes`
- Prevents replay attacks while allowing for clock skew
- Timestamps must be in ISO-8601 format

### Security Considerations

- **SharedSecret Storage**: Stored in database, never logged or exposed except during registration
- **Internal APIs**: Should only be accessible within the cluster (use network policies)
- **Audit Logging**: All authentication attempts and ACL checks are logged
- **HMAC Algorithm**: Uses SHA-256 with minimum 256-bit secrets

## Development

### Project Structure

```
environment-service/
├── src/
│   ├── main/
│   │   ├── java/org/sifunity/broker/environment/
│   │   │   ├── controller/          # REST controllers
│   │   │   │   ├── EnvironmentController.java
│   │   │   │   ├── AclController.java
│   │   │   │   ├── ProviderController.java
│   │   │   │   ├── ZoneController.java
│   │   │   │   ├── InternalAuthController.java
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   ├── service/             # Business logic services
│   │   │   │   ├── EnvironmentService.java
│   │   │   │   ├── AclService.java
│   │   │   │   ├── ProviderService.java
│   │   │   │   ├── ZoneService.java
│   │   │   │   ├── AuthenticationService.java
│   │   │   │   └── AuthorizationService.java
│   │   │   ├── repository/          # Spring Data JPA repositories
│   │   │   │   ├── EnvironmentRepository.java
│   │   │   │   ├── AclEntryRepository.java
│   │   │   │   ├── ProviderRegistrationRepository.java
│   │   │   │   ├── ZoneRepository.java
│   │   │   │   └── ContextRepository.java
│   │   │   ├── domain/              # JPA entity models
│   │   │   │   ├── Environment.java
│   │   │   │   ├── AclEntry.java
│   │   │   │   ├── ProviderRegistration.java
│   │   │   │   ├── Zone.java
│   │   │   │   └── Context.java
│   │   │   ├── dto/                 # Request/response DTOs
│   │   │   │   ├── EnvironmentRegistrationRequest.java
│   │   │   │   ├── EnvironmentRegistrationResponse.java
│   │   │   │   ├── AclEntryCreateRequest.java
│   │   │   │   ├── ProviderRegistrationRequest.java
│   │   │   │   ├── ZoneCreateRequest.java
│   │   │   │   ├── ContextCreateRequest.java
│   │   │   │   ├── TokenValidationRequest.java
│   │   │   │   ├── TokenValidationResponse.java
│   │   │   │   ├── PermissionCheckRequest.java
│   │   │   │   └── PermissionCheckResponse.java
│   │   │   ├── config/              # Spring configuration classes
│   │   │   │   └── SecurityConfiguration.java
│   │   │   └── exception/           # Custom exception classes
│   │   │       ├── EnvironmentNotFoundException.java
│   │   │       ├── AclEntryNotFoundException.java
│   │   │       ├── ProviderRegistrationNotFoundException.java
│   │   │       ├── ZoneNotFoundException.java
│   │   │       ├── DuplicateApplicationNameException.java
│   │   │       ├── DuplicateZoneException.java
│   │   │       ├── DuplicateAclEntryException.java
│   │   │       ├── DuplicateProviderException.java
│   │   │       └── DuplicateContextException.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-test.yml
│   │       └── db/migration/        # Flyway migrations
│   │           ├── V1__create_environments_table.sql
│   │           ├── V2__create_zones_and_contexts_tables.sql
│   │           ├── V3__create_acl_entries_table.sql
│   │           └── V4__create_provider_registry_table.sql
│   └── test/
│       └── java/org/sifunity/broker/environment/
│           ├── controller/          # Controller unit tests
│           ├── service/             # Service layer unit tests
│           ├── repository/          # Repository integration tests
│           ├── property/            # Property-based tests (30 properties)
│           ├── integration/         # End-to-end integration tests
│           └── generator/           # Test data generators
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── README.md
└── .gitignore
```

### Code Style and Conventions

**Java Conventions:**
- Follow standard Java naming conventions
- Use meaningful variable and method names
- Keep methods focused and small (single responsibility)
- Maximum line length: 120 characters

**Lombok Usage:**
- Use `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` for entities
- Use `@Slf4j` for logging
- Avoid `@ToString` on entities with relationships (prevents lazy loading issues)

**Documentation:**
- Comprehensive JavaDoc for public APIs
- Document complex business logic
- Include examples in JavaDoc where helpful

**Validation:**
- Use Bean Validation annotations on all request DTOs
- Validate business rules in service layer
- Return clear error messages

**Error Handling:**
- Use custom exceptions from sif-broker-common
- Never swallow exceptions
- Log errors with context
- Return SIF-compliant error responses

### Building and Packaging

**Build JAR:**
```bash
./mvnw clean package
```

**Build without tests:**
```bash
./mvnw clean package -DskipTests
```

**Build with specific profile:**
```bash
./mvnw clean package -Pprod
```

**Install to local Maven repository:**
```bash
./mvnw clean install
```

### Code Quality Tools

**Run Checkstyle:**
```bash
./mvnw checkstyle:check
```

**Run SpotBugs:**
```bash
./mvnw spotbugs:check
```

**Generate test coverage report:**
```bash
./mvnw test jacoco:report
open target/site/jacoco/index.html
```

### Database Management

**Create new migration:**
1. Create file: `src/main/resources/db/migration/V{version}__{description}.sql`
2. Follow naming convention: `V5__add_new_column.sql`
3. Test migration: `./mvnw flyway:migrate`

**Rollback migration:**
Flyway doesn't support automatic rollback. Create a new migration to undo changes.

**Clean database (development only):**
```bash
./mvnw flyway:clean
./mvnw flyway:migrate
```

### Debugging

**Run with remote debugging:**
```bash
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

**Debug in Docker:**
```yaml
# Add to docker-compose.yml
environment:
  JAVA_OPTS: "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
ports:
  - "5005:5005"
```

**Enable SQL logging:**
```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### Contributing

1. Create a feature branch from `main`
2. Write tests for new functionality
3. Ensure all tests pass: `./mvnw test`
4. Follow code style conventions
5. Update documentation as needed
6. Submit pull request with clear description

### Common Development Tasks

**Add new endpoint:**
1. Create request/response DTOs in `dto/` package
2. Add service method in appropriate service class
3. Add controller method with `@Valid` annotation
4. Write unit tests for controller and service
5. Write integration test for end-to-end flow
6. Update this README with endpoint documentation

**Add new entity:**
1. Create JPA entity in `domain/` package
2. Create repository interface in `repository/` package
3. Create Flyway migration in `db/migration/`
4. Write repository tests with Testcontainers
5. Update database schema section in README

**Add new validation:**
1. Add validation annotation to DTO field
2. Add custom validator if needed (implement `ConstraintValidator`)
3. Write test for validation in controller test
4. Ensure error message is clear and helpful

## Monitoring and Observability

### Logging

The service uses structured logging with correlation IDs for request tracing.

#### Log Levels

- **ERROR**: Failures and exceptions
- **WARN**: Retries and recoverable issues
- **INFO**: Key events (registration, authentication, ACL changes)
- **DEBUG**: Detailed flow and SQL statements

#### Key Logged Events

- Environment registration and deletion
- All authentication attempts (success and failure)
- All ACL permission checks (for security auditing)
- Provider registration and lookup
- Database connection issues
- Validation errors

#### Log Configuration

```yaml
logging:
  level:
    org.sifunity.broker.environment: INFO
    org.springframework.web: INFO
    org.hibernate.SQL: WARN  # Set to DEBUG to see SQL
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

#### Viewing Logs

```bash
# Application logs (if running locally)
tail -f logs/environment-service.log

# Docker logs
docker-compose logs -f environment-service

# Kubernetes logs
kubectl logs -f deployment/environment-service
```

### Metrics

The service exposes metrics via Spring Boot Actuator and Prometheus.

#### Key Metrics Tracked

**Application Metrics:**
- Environment registration rate
- Authentication success/failure rate
- ACL permission check rate and results
- Provider registry query rate
- API response times per endpoint

**JVM Metrics:**
- Heap memory usage
- Garbage collection statistics
- Thread count and states
- CPU usage

**Database Metrics:**
- HikariCP connection pool utilization
- Active connections
- Pending connection requests
- Connection acquisition time
- Query execution time

**HTTP Metrics:**
- Request count per endpoint
- Response time percentiles (p50, p95, p99)
- Error rate by status code

#### Accessing Metrics

**List all available metrics:**
```bash
curl http://localhost:8081/actuator/metrics
```

**Get specific metric:**
```bash
# Authentication success rate
curl http://localhost:8081/actuator/metrics/authentication.success

# Database connection pool
curl http://localhost:8081/actuator/metrics/hikaricp.connections.active

# HTTP request duration
curl http://localhost:8081/actuator/metrics/http.server.requests
```

**Prometheus format:**
```bash
curl http://localhost:8081/actuator/prometheus
```

### Prometheus Integration

Add the service as a scrape target in Prometheus:

```yaml
scrape_configs:
  - job_name: 'environment-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['environment-service:8081']
```

### Grafana Dashboards

Recommended dashboards for monitoring:
- **Spring Boot 2.x Statistics** (Dashboard ID: 6756)
- **JVM (Micrometer)** (Dashboard ID: 4701)
- **HikariCP Connection Pool** (Dashboard ID: 11681)

### Alerting

Recommended alerts:

**High Authentication Failure Rate:**
```yaml
- alert: HighAuthenticationFailureRate
  expr: rate(authentication_failure_total[5m]) > 10
  for: 5m
  annotations:
    summary: "High authentication failure rate detected"
```

**Database Connection Pool Exhaustion:**
```yaml
- alert: DatabaseConnectionPoolExhausted
  expr: hikaricp_connections_active / hikaricp_connections_max > 0.9
  for: 2m
  annotations:
    summary: "Database connection pool near capacity"
```

**High API Response Time:**
```yaml
- alert: HighAPIResponseTime
  expr: histogram_quantile(0.95, http_server_requests_seconds_bucket) > 1
  for: 5m
  annotations:
    summary: "95th percentile response time exceeds 1 second"
```

### Distributed Tracing

For distributed tracing integration (e.g., with Zipkin or Jaeger), add Spring Cloud Sleuth:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>
```

Configure trace sampling:
```yaml
spring:
  sleuth:
    sampler:
      probability: 0.1  # Sample 10% of requests
```

## Security Considerations

### Credential Security

**SharedSecret Storage:**
- Stored in database (consider encryption at rest for production)
- Never logged or exposed in responses except during initial registration
- Minimum 256 bits (32 bytes) of cryptographic randomness
- Generated using `SecureRandom`

**SessionToken Security:**
- UUID format (128-bit random identifier)
- Indexed for fast lookup
- Unique across all environments
- Used as authentication identifier

### HMAC Authentication

**Algorithm:** HMAC-SHA256
- Industry-standard message authentication
- Prevents tampering and replay attacks
- Timestamp-based to prevent replay (5-minute tolerance)

**Computation:**
```
data = sessionToken + ":" + timestamp
hash = HMAC-SHA256(data, sharedSecret)
```

### Internal API Protection

**Network Isolation:**
- Internal APIs (`/internal/**`) should only be accessible within the cluster
- Use Kubernetes NetworkPolicies to restrict access
- Consider mutual TLS for internal service communication

**Example NetworkPolicy:**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: environment-service-internal-api
spec:
  podSelector:
    matchLabels:
      app: environment-service
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: api-gateway
      ports:
        - protocol: TCP
          port: 8081
```

### Audit Logging

All security-relevant events are logged:
- Environment registration and deletion
- Authentication attempts (success and failure)
- ACL permission checks (granted and denied)
- Provider registration changes

**Log Format:**
```
2024-01-15 10:30:00 [INFO] Authentication attempt: sessionToken=abc123, result=SUCCESS
2024-01-15 10:30:01 [INFO] ACL check: environmentId=uuid, service=StudentPersonals, result=GRANTED
```

### Database Security

**Connection Security:**
- Use SSL/TLS for database connections in production
- Store credentials in secrets management system (e.g., Kubernetes Secrets, AWS Secrets Manager)
- Use least-privilege database user

**SQL Injection Prevention:**
- All queries use JPA/Hibernate parameterized queries
- No raw SQL with string concatenation
- Input validation on all user inputs

### Input Validation

**Request Validation:**
- Bean Validation annotations on all DTOs
- Pattern validation for zone IDs and context IDs
- URL validation for provider endpoints
- Enum validation for types and access levels

**Sanitization:**
- All inputs are validated before processing
- No user input is directly used in SQL queries
- Error messages don't expose internal details

### Rate Limiting

Consider implementing rate limiting for:
- Environment registration endpoint (prevent abuse)
- Authentication validation endpoint (prevent brute force)
- ACL query endpoints (prevent enumeration)

**Example with Spring Cloud Gateway:**
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: environment-service
          uri: http://environment-service:8081
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
```

### Secrets Management

**Development:**
- Use environment variables or `.env` files
- Never commit secrets to version control

**Production:**
- Use Kubernetes Secrets or cloud provider secrets management
- Rotate secrets regularly
- Use separate secrets per environment

**Example Kubernetes Secret:**
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: environment-service-db
type: Opaque
stringData:
  username: sif_broker
  password: <strong-random-password>
```

### HTTPS/TLS

**Production Deployment:**
- Always use HTTPS for external communication
- Use TLS 1.2 or higher
- Use strong cipher suites
- Implement certificate rotation

**Kubernetes Ingress Example:**
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: environment-service
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  tls:
    - hosts:
        - api.example.com
      secretName: environment-service-tls
  rules:
    - host: api.example.com
      http:
        paths:
          - path: /environments
            pathType: Prefix
            backend:
              service:
                name: environment-service
                port:
                  number: 8081
```

## Performance Optimization

### Database Optimization

**Indexes:**
- All foreign keys are indexed
- Composite index on ACL lookup: `(environment_id, service_name, zone_id, context_id, access_type)`
- Composite index on provider lookup: `(service_name, zone_id, context_id)`
- Index on `session_token` for authentication lookups

**Connection Pooling:**
- HikariCP (default in Spring Boot)
- Configured for optimal performance
- Monitor pool utilization via metrics

**Query Optimization:**
- Use JPA fetch strategies appropriately
- Avoid N+1 query problems
- Use batch operations where possible

### Caching Strategy

**Recommended Caching:**
- Environment lookups by sessionToken (high read, low write)
- ACL entries by environment ID (high read, low write)
- Provider registry lookups (moderate read, moderate write)

**Implementation with Redis:**
```java
@Cacheable(value = "environments", key = "#sessionToken")
public Environment findBySessionToken(String sessionToken) {
    return environmentRepository.findBySessionToken(sessionToken)
        .orElseThrow(() -> new EnvironmentNotFoundException(sessionToken));
}

@CacheEvict(value = "environments", key = "#environment.sessionToken")
public void deleteEnvironment(Environment environment) {
    environmentRepository.delete(environment);
}
```

### JVM Tuning

**Recommended JVM Options:**
```bash
JAVA_OPTS="
  -XX:+UseContainerSupport
  -XX:MaxRAMPercentage=75.0
  -XX:InitialRAMPercentage=50.0
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
  -XX:+HeapDumpOnOutOfMemoryError
  -XX:HeapDumpPath=/tmp/heapdump.hprof
"
```

### Horizontal Scaling

The service is stateless and can be horizontally scaled:

```yaml
# Kubernetes Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: environment-service
spec:
  replicas: 3  # Scale to 3 instances
  selector:
    matchLabels:
      app: environment-service
  template:
    metadata:
      labels:
        app: environment-service
    spec:
      containers:
        - name: environment-service
          image: environment-service:latest
          resources:
            requests:
              memory: "512Mi"
              cpu: "500m"
            limits:
              memory: "1Gi"
              cpu: "1000m"
```

## API Examples

### Complete Registration and Usage Flow

**1. Register Environment:**
```bash
curl -X POST http://localhost:8081/environments/environment \
  -H "Content-Type: application/json" \
  -d '{
    "applicationName": "MySchoolApp",
    "type": "DIRECT",
    "defaultZoneId": "default",
    "defaultContextId": "production"
  }'

# Response:
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "applicationName": "MySchoolApp",
  "sessionToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "sharedSecret": "dGVzdHNlY3JldGZvcmhtYWN2YWxpZGF0aW9u",
  "createdAt": "2024-01-15T10:30:00Z",
  "defaultZoneId": "default",
  "defaultContextId": "production",
  "type": "DIRECT"
}
```

**2. Create ACL Entry:**
```bash
curl -X POST http://localhost:8081/acl-entries \
  -H "Content-Type: application/json" \
  -d '{
    "environmentId": "550e8400-e29b-41d4-a716-446655440000",
    "serviceName": "StudentPersonals",
    "zoneId": "default",
    "contextId": "production",
    "accessType": "QUERY",
    "accessLevel": "APPROVED"
  }'
```

**3. Validate Token (Internal API):**
```bash
# Compute HMAC first (example in Python)
import hmac
import hashlib
import base64
from datetime import datetime

session_token = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
shared_secret = "dGVzdHNlY3JldGZvcmhtYWN2YWxpZGF0aW9u"
timestamp = datetime.utcnow().isoformat() + "Z"

data = f"{session_token}:{timestamp}"
hash_value = base64.b64encode(
    hmac.new(
        shared_secret.encode(),
        data.encode(),
        hashlib.sha256
    ).digest()
).decode()

# Call validate-token
curl -X POST http://localhost:8081/internal/validate-token \
  -H "Content-Type: application/json" \
  -d "{
    \"sessionToken\": \"${session_token}\",
    \"timestamp\": \"${timestamp}\",
    \"hash\": \"${hash_value}\"
  }"
```

**4. Check Permission (Internal API):**
```bash
curl -X POST http://localhost:8081/internal/check-permission \
  -H "Content-Type: application/json" \
  -d '{
    "environmentId": "550e8400-e29b-41d4-a716-446655440000",
    "serviceName": "StudentPersonals",
    "zoneId": "default",
    "contextId": "production",
    "accessType": "QUERY"
  }'

# Response:
{
  "granted": true
}
```

## Troubleshooting

### Common Issues

**Issue: Database connection refused**
```
Solution:
1. Check PostgreSQL is running: pg_isready -h localhost -p 5432
2. Verify credentials: psql -U sif_broker -d sif_broker
3. Check firewall rules
4. Verify SPRING_DATASOURCE_URL is correct
```

**Issue: Flyway migration fails**
```
Solution:
1. Check migration status: ./mvnw flyway:info
2. Repair checksums: ./mvnw flyway:repair
3. If needed, baseline: ./mvnw flyway:baseline
4. Check migration file syntax
```

**Issue: Authentication fails with 401**
```
Solution:
1. Verify sessionToken exists in database
2. Check timestamp is within tolerance (5 minutes)
3. Verify HMAC computation is correct
4. Check sharedSecret matches
5. Enable DEBUG logging for AuthenticationService
```

**Issue: Permission denied with 403**
```
Solution:
1. Verify ACL entry exists: GET /acl-entries?environmentId=<uuid>
2. Check accessLevel is APPROVED
3. Verify exact match on service, zone, context, accessType
4. Check logs for ACL check details
```

**Issue: High memory usage**
```
Solution:
1. Check for memory leaks: jmap -heap <pid>
2. Analyze heap dump: jhat heapdump.hprof
3. Adjust JVM options: -Xmx512m
4. Check for large result sets (add pagination)
5. Monitor connection pool: /actuator/metrics/hikaricp
```

**Issue: Slow queries**
```
Solution:
1. Enable SQL logging: logging.level.org.hibernate.SQL=DEBUG
2. Check query execution plans: EXPLAIN ANALYZE
3. Verify indexes exist: \d+ table_name
4. Add missing indexes
5. Consider query optimization
```

## Related Documentation

### Specification Documents
- [Requirements Document](.kiro/specs/environment-service/requirements.md) - Detailed requirements and acceptance criteria
- [Design Document](.kiro/specs/environment-service/design.md) - Architecture, API specifications, and correctness properties
- [Implementation Tasks](.kiro/specs/environment-service/tasks.md) - Task breakdown and implementation plan

### Related Services
- **API Gateway** - Routes external requests and calls Environment Service for auth/authz
- **sif-broker-common** - Shared library with DTOs, exceptions, and utilities
- **Connector Services** - Query provider registry for service discovery

### External Resources
- [SIF 3.7 Specification](https://specification.sifassociation.org/) - SIF Infrastructure specification
- [Spring Boot Documentation](https://spring.io/projects/spring-boot) - Spring Boot framework
- [jqwik Documentation](https://jqwik.net/) - Property-based testing framework
- [Testcontainers](https://www.testcontainers.org/) - Docker containers for testing
- [Flyway Documentation](https://flywaydb.org/documentation/) - Database migrations

## FAQ

**Q: Why are there two types of APIs (external and internal)?**
A: External APIs are exposed through the Gateway and require authentication. Internal APIs are used by the Gateway itself for authentication/authorization validation and should not be exposed externally.

**Q: How is the sharedSecret generated?**
A: Using `SecureRandom` to generate 32 bytes (256 bits) of cryptographic randomness, then Base64-encoded for storage and transmission.

**Q: Can I change the timestamp tolerance?**
A: Yes, configure `authentication.timestamp-tolerance-minutes` in `application.yml`. Default is 5 minutes.

**Q: What happens when an environment is deleted?**
A: All associated ACL entries and provider registrations are cascade deleted due to foreign key constraints.

**Q: How do I add a new zone or context?**
A: Use the zone management APIs: `POST /zones` to create a zone, then `POST /zones/{zoneId}/contexts` to create contexts within that zone.

**Q: Can one provider register multiple services?**
A: Yes, a provider environment can register multiple services, but each service/zone/context combination can only have one provider.

**Q: How do I rotate credentials?**
A: Currently, you need to delete the environment and re-register. Future versions may support credential rotation.

**Q: What database versions are supported?**
A: PostgreSQL 12 or higher. The service uses standard SQL features compatible with modern PostgreSQL versions.

**Q: Can I use H2 for development?**
A: Yes, H2 is included as a test dependency. Configure `spring.datasource.url` to use H2, but note that some PostgreSQL-specific features may not work.

**Q: How do I enable SQL logging?**
A: Set `logging.level.org.hibernate.SQL=DEBUG` in `application.yml` or via environment variable.

## Version History

### 1.0.0-SNAPSHOT (Current)
- Initial implementation
- Environment registration and management
- ACL management
- Provider registry
- Zone and context management
- Internal authentication and authorization APIs
- 30 correctness properties with property-based testing
- Comprehensive integration tests
- Docker and docker-compose support

## License

This project is licensed under the MIT License. See [LICENSE](../LICENSE) for details.