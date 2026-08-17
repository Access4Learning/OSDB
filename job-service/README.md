# Job Service

## Overview

The Job Service is a core microservice within the SIF Unity Broker system that manages Job state objects for Functional Services. It provides comprehensive job lifecycle management including creation, state transitions, phase tracking, and querying capabilities.

## Features

- **Job Creation**: Create jobs with environment context and service metadata
- **State Management**: Track job state transitions (NOTSTARTED → INPROGRESS → COMPLETED/FAILED)
- **Phase Tracking**: Manage detailed job phases with independent state tracking
- **Job Querying**: List and filter jobs by environment, state, and service name
- **Concurrency Safety**: Optimistic locking prevents concurrent update conflicts
- **Referential Integrity**: Cascade deletion ensures data consistency

## Technology Stack

- **Framework**: Spring Boot 3.2.0
- **JVM**: Java 17 (Amazon Corretto recommended)
- **Database**: PostgreSQL
- **ORM**: Spring Data JPA with Hibernate
- **Validation**: Bean Validation (Jakarta Validation)
- **Testing**: JUnit 5, jqwik (property-based testing)
- **Build Tool**: Maven

## Prerequisites

- Java 17 or higher
- Maven 3.8+
- PostgreSQL 15+
- Docker (optional, for containerized deployment)

## Building the Project

```bash
# Build the project
mvn clean install

# Run tests
mvn test

# Package as JAR
mvn package
```

## Running the Service

### Local Development

```bash
# Set environment variables
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=sif_broker
export DB_USERNAME=postgres
export DB_PASSWORD=postgres

# Run the application
mvn spring-boot:run
```

### Using Docker Compose

```bash
# Start the service with PostgreSQL
docker-compose up -d

# View logs
docker-compose logs -f job-service

# Stop the service
docker-compose down
```

## Configuration

The service can be configured using environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL host | localhost |
| `DB_PORT` | PostgreSQL port | 5432 |
| `DB_NAME` | Database name | sif_broker |
| `DB_USERNAME` | Database username | postgres |
| `DB_PASSWORD` | Database password | postgres |
| `SERVER_PORT` | HTTP server port | 8084 |

## API Endpoints

### Job Management

- `POST /jobs` - Create a new job
- `GET /jobs/{jobId}` - Get job by ID
- `GET /jobs?environmentId={uuid}&state={state}&serviceName={name}` - List jobs with filters
- `PUT /jobs/{jobId}/state` - Update job state
- `DELETE /jobs/{jobId}` - Delete a job

### Job Phase Management

- `POST /jobs/{jobId}/phases` - Create a new phase
- `GET /jobs/{jobId}/phases` - List all phases for a job
- `PUT /jobs/{jobId}/phases/{phaseId}/state` - Update phase state

### Health & Monitoring

- `GET /actuator/health` - Health check endpoint
- `GET /actuator/metrics` - Metrics endpoint
- `GET /actuator/prometheus` - Prometheus metrics

## Database Schema

### jobs table

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| environment_id | UUID | Environment that owns the job |
| service_name | VARCHAR(255) | Functional service name |
| zone_id | VARCHAR(255) | Zone identifier |
| context_id | VARCHAR(255) | Optional context identifier |
| state | VARCHAR(50) | Job state (NOTSTARTED, INPROGRESS, COMPLETED, FAILED) |
| created_at | TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | Last update timestamp |
| current_phase | VARCHAR(100) | Current phase name |
| metadata | JSONB | Optional JSON metadata |
| version | INTEGER | Optimistic locking version |

### job_phases table

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| job_id | UUID | Foreign key to jobs table |
| phase_name | VARCHAR(100) | Phase name (unique per job) |
| state | VARCHAR(50) | Phase state |
| created_at | TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | Last update timestamp |
| metadata | JSONB | Optional JSON metadata |

## State Transitions

### Valid Job State Transitions

```
NOTSTARTED → INPROGRESS
INPROGRESS → COMPLETED
INPROGRESS → FAILED
```

### Terminal States

Jobs in `COMPLETED` or `FAILED` state cannot transition to any other state.

## Testing

The service includes comprehensive test coverage:

- **Unit Tests**: Test individual components and business logic
- **Property-Based Tests**: Verify universal properties across all inputs using jqwik
- **Integration Tests**: Test full HTTP request/response cycles

```bash
# Run all tests
mvn test

# Run only unit tests
mvn test -Dtest="*Test"

# Run only property tests
mvn test -Dtest="*Properties"

# Run only integration tests
mvn test -Dtest="*IntegrationTest"
```

## Development

### Project Structure

```
job-service/
├── src/
│   ├── main/
│   │   ├── java/org/sifunity/broker/jobservice/
│   │   │   ├── config/          # Spring configuration
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── domain/          # JPA entities
│   │   │   ├── dto/             # Request/Response DTOs
│   │   │   ├── exception/       # Custom exceptions
│   │   │   ├── mapper/          # Entity-DTO mappers
│   │   │   ├── repository/      # Data access layer
│   │   │   ├── service/         # Business logic
│   │   │   └── util/            # Utility classes
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/    # Flyway migrations
│   └── test/
│       ├── java/org/sifunity/broker/jobservice/
│       │   ├── integration/     # Integration tests
│       │   ├── property/        # Property-based tests
│       │   └── unit/            # Unit tests
│       └── resources/
│           └── application-test.yml
└── pom.xml
```

## License

This project is licensed under the MIT License. See [LICENSE](../LICENSE) for details.
