# Subscription Service

## Overview

The Subscription Service is a core microservice within the SIF Unity Broker system responsible for managing subscriptions that define message routing rules. It handles two distinct types of subscriptions:

1. **EVENT Subscriptions**: Consumers subscribe to event notifications from providers
2. **DELAYED_REQUEST Subscriptions**: Providers subscribe to their request queues for asynchronous request processing

## Technology Stack

- **Framework**: Spring Boot 3.2.0
- **JVM**: Amazon Corretto 17
- **Database**: PostgreSQL (subscription metadata)
- **Cache**: Redis (optional caching for frequently queried subscriptions)
- **Data Access**: Spring Data JPA
- **Build Tool**: Maven
- **Testing**: JUnit 5, jqwik (property-based testing), Testcontainers

## Architecture

The service follows a layered architecture:

- **Controller Layer**: REST API endpoints
- **Service Layer**: Business logic and validation
- **Repository Layer**: Data access with Spring Data JPA
- **Client Layer**: HTTP clients for other services (Queue Service)
- **Domain Layer**: JPA entities
- **Config Layer**: Spring configuration
- **Exception Layer**: Custom exceptions and global exception handler

## API Endpoints

Base path: `/subscriptions`

- `POST /` - Create a new subscription
- `GET /events` - Query EVENT subscriptions
- `GET /delayed-requests` - Query DELAYED_REQUEST subscription
- `GET /{id}` - Get subscription by ID
- `GET /?queueId={uuid}` - List subscriptions by queue
- `DELETE /{id}` - Delete subscription

## Configuration

The service is configured via environment variables:

### Database Configuration
- `DB_HOST` - PostgreSQL host (default: localhost)
- `DB_PORT` - PostgreSQL port (default: 5432)
- `DB_NAME` - Database name (default: sif_broker)
- `DB_USERNAME` - Database username (default: postgres)
- `DB_PASSWORD` - Database password (default: postgres)

### Redis Configuration
- `REDIS_HOST` - Redis host (default: localhost)
- `REDIS_PORT` - Redis port (default: 6379)
- `REDIS_PASSWORD` - Redis password (optional)

### Queue Service Configuration
- `QUEUE_SERVICE_URL` - Queue Service URL (default: http://localhost:8082)

### Server Configuration
- `SERVER_PORT` - Server port (default: 8083)

## Building and Running

### Prerequisites

- Java 17 (Amazon Corretto recommended)
- Maven 3.8+
- PostgreSQL 14+
- Redis 6+

### Build

```bash
mvn clean package
```

### Run

```bash
mvn spring-boot:run
```

Or run the JAR:

```bash
java -jar target/subscription-service-1.0.0-SNAPSHOT.jar
```

### Run with Docker Compose

For detailed Docker deployment instructions, see [DOCKER-DEPLOYMENT.md](DOCKER-DEPLOYMENT.md).

Quick start:

```bash
# Build and start all services
docker-compose up -d

# Check service health
curl http://localhost:8083/actuator/health

# View logs
docker-compose logs -f subscription-service

# Stop services
docker-compose down
```

The Docker Compose setup includes:
- Subscription Service (port 8083)
- PostgreSQL database (port 5433)
- Redis cache (port 6380)
- Mock Queue Service (port 8082)

## Testing

### Run All Tests

```bash
mvn test
```

### Run Unit Tests Only

```bash
mvn test -Dtest="*Test"
```

### Run Property-Based Tests Only

```bash
mvn test -Dtest="*Properties"
```

### Run Integration Tests Only

```bash
mvn test -Dtest="*IntegrationTest"
```

## Health Checks

The service exposes health check endpoints via Spring Boot Actuator:

- `/actuator/health` - Overall health status
- `/actuator/health/liveness` - Liveness probe
- `/actuator/health/readiness` - Readiness probe
- `/actuator/metrics` - Metrics endpoint
- `/actuator/prometheus` - Prometheus metrics

## Development

### Project Structure

```
subscription-service/
├── src/
│   ├── main/
│   │   ├── java/org/sifunity/broker/subscriptionservice/
│   │   │   ├── client/          # HTTP clients
│   │   │   ├── config/          # Spring configuration
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── domain/          # JPA entities
│   │   │   ├── exception/       # Custom exceptions
│   │   │   ├── repository/      # Data access
│   │   │   ├── service/         # Business logic
│   │   │   └── SubscriptionServiceApplication.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/    # Flyway migrations
│   └── test/
│       ├── java/org/sifunity/broker/subscriptionservice/
│       │   ├── controller/      # Controller tests
│       │   ├── integration/     # Integration tests
│       │   ├── property/        # Property-based tests
│       │   ├── repository/      # Repository tests
│       │   └── service/         # Service tests
│       └── resources/
│           └── application-test.yml
├── pom.xml
└── README.md
```

## Dependencies

The service depends on:

- **sif-broker-common**: Shared DTOs, exceptions, and utilities
- **Queue Service**: For queue existence validation

## License

This project is licensed under the MIT License. See [LICENSE](../LICENSE) for details.
