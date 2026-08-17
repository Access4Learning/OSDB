# Queue Service

The Queue Service is a foundational core service in the SIF Unity Broker system that manages message queues and provides asynchronous message delivery with at-least-once semantics.

## Overview

The Queue Service provides the messaging infrastructure required for DELAYED request/response patterns and EVENT distribution patterns in the SIF Unity Broker.

### Key Features

- **Queue Management**: Create, read, and delete message queues
- **Message Enqueuing**: Store messages with pluggable storage backends
- **Message Delivery**: Deliver messages with visibility timeout and retry logic
- **Message Acknowledgment**: Remove successfully processed messages
- **Dead-Letter Queue**: Automatic handling of failed messages
- **At-Least-Once Delivery**: Guaranteed message delivery semantics
- **Pluggable Storage**: Support for S3 and filesystem storage

### Architecture

The service uses a three-tier storage architecture:

1. **PostgreSQL**: Stores queue metadata (queue definitions, configuration)
2. **Redis**: Stores message metadata and delivery tracking (for scalability and performance)
3. **Pluggable Storage**: Stores actual message payloads (S3 or filesystem)

This separation allows the service to handle millions of messages without database bloat while maintaining durability and recoverability.

## Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Language**: Java 17
- **Database**: PostgreSQL (queue metadata)
- **Cache/Message Store**: Redis (message metadata and delivery tracking)
- **Storage**: AWS S3 or filesystem (pluggable)
- **Build Tool**: Maven
- **Testing**: JUnit 5, jqwik (property-based testing), Testcontainers

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.8 or higher
- PostgreSQL 14 or higher
- Redis 6 or higher
- (Optional) AWS S3 bucket for cloud storage

### Building the Service

```bash
# Build the service
mvn clean package

# Run tests
mvn test

# Run property-based tests
mvn test -Dtest=**/*Properties
```

### Running Locally

#### Using Filesystem Storage

```bash
# Set environment variables
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=sif_broker
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export REDIS_HOST=localhost
export REDIS_PORT=6379
export STORAGE_TYPE=filesystem
export STORAGE_PATH=./data/messages

# Run the service
mvn spring-boot:run
```

#### Using S3 Storage

```bash
# Set environment variables
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=sif_broker
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export REDIS_HOST=localhost
export REDIS_PORT=6379
export STORAGE_TYPE=s3
export S3_BUCKET=sif-broker-messages
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key

# Run the service
mvn spring-boot:run
```

### Running with Docker Compose

```bash
# Start all services (PostgreSQL, Redis, Queue Service)
docker-compose up -d

# View logs
docker-compose logs -f queue-service

# Stop all services
docker-compose down
```

## API Documentation

### Queue Management

#### Create Queue

```http
POST /api/queues
Content-Type: application/json

{
  "name": "consumer-responses",
  "ownerId": "uuid",
  "maxRetries": 3,
  "retryDelayMs": 5000
}
```

#### Get Queue

```http
GET /api/queues/{queueId}
```

#### Get Queues by Owner

```http
GET /api/queues?ownerId={environmentId}
```

#### Delete Queue

```http
DELETE /api/queues/{queueId}
```

#### Get Queue Statistics

```http
GET /api/queues/{queueId}/stats
```

### Message Operations

#### Enqueue Message

```http
POST /api/queues/{queueId}/messages
Content-Type: application/json

{
  "messageId": "uuid",
  "environment": "prod-consumer-1",
  "zone": "default",
  "context": "district-a",
  "service": "StudentPersonal",
  "payload": { ... }
}
```

#### Deliver Messages

```http
POST /api/queues/{queueId}/deliver
Content-Type: application/json

{
  "maxMessages": 10,
  "visibilityTimeoutSeconds": 30
}
```

#### Acknowledge Message

```http
POST /api/messages/{messageId}/acknowledge
```

### Dead-Letter Queue

#### Get Dead-Letter Messages

```http
GET /api/queues/{queueId}/dead-letter
```

#### Reprocess Dead-Letter Message

```http
POST /api/messages/{messageId}/reprocess
```

## Configuration

### Application Properties

Key configuration properties in `application.yml`:

```yaml
# Database
spring.datasource.url: jdbc:postgresql://localhost:5432/sif_broker
spring.datasource.username: postgres
spring.datasource.password: postgres

# Redis
spring.data.redis.host: localhost
spring.data.redis.port: 6379

# Storage
message.storage.type: filesystem  # or s3
message.storage.filesystem.path: ./data/messages
message.storage.s3.bucket: sif-broker-messages
message.storage.s3.region: us-east-1

# Queue Defaults
queue.defaults.maxRetries: 3
queue.defaults.retryDelayMs: 5000
queue.defaults.visibilityTimeoutSeconds: 30
```

### Environment Variables

- `DB_HOST`: PostgreSQL host (default: localhost)
- `DB_PORT`: PostgreSQL port (default: 5432)
- `DB_NAME`: Database name (default: sif_broker)
- `DB_USERNAME`: Database username (default: postgres)
- `DB_PASSWORD`: Database password (default: postgres)
- `REDIS_HOST`: Redis host (default: localhost)
- `REDIS_PORT`: Redis port (default: 6379)
- `REDIS_PASSWORD`: Redis password (optional)
- `STORAGE_TYPE`: Storage provider type (filesystem or s3, default: filesystem)
- `STORAGE_PATH`: Filesystem storage path (default: ./data/messages)
- `S3_BUCKET`: S3 bucket name (required if STORAGE_TYPE=s3)
- `AWS_REGION`: AWS region (required if STORAGE_TYPE=s3)

## Testing

### Unit Tests

```bash
# Run all unit tests
mvn test -Dtest=**/*Test

# Run specific test class
mvn test -Dtest=QueueServiceTest
```

### Property-Based Tests

```bash
# Run all property-based tests
mvn test -Dtest=**/*Properties

# Run specific property test
mvn test -Dtest=QueueManagementProperties
```

### Integration Tests

```bash
# Run all integration tests
mvn test -Dtest=**/*IntegrationTest

# Integration tests use Testcontainers for PostgreSQL and Redis
```

## Deployment

### Docker

```bash
# Build Docker image
docker build -t sif-broker/queue-service:latest .

# Run Docker container
docker run -p 8082:8082 \
  -e DB_HOST=postgres \
  -e REDIS_HOST=redis \
  sif-broker/queue-service:latest
```

### Kubernetes

```bash
# Apply Kubernetes manifests
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml

# Check deployment status
kubectl get pods -l app=queue-service
kubectl logs -f deployment/queue-service
```

## Monitoring

### Health Check

```http
GET /actuator/health
```

### Metrics

```http
GET /actuator/metrics
```

### Prometheus Metrics

```http
GET /actuator/prometheus
```

## Development

### Project Structure

```
queue-service/
├── src/main/java/org/sifunity/broker/queueservice/
│   ├── controller/          # REST API endpoints
│   ├── service/             # Business logic
│   ├── repository/          # Data access
│   ├── storage/             # Storage provider abstraction
│   ├── redis/               # Redis operations
│   ├── domain/              # Domain entities
│   ├── dto/                 # Request/Response DTOs
│   ├── config/              # Configuration
│   ├── exception/           # Custom exceptions
│   └── QueueServiceApplication.java
├── src/main/resources/
│   ├── application.yml      # Configuration
│   └── db/migration/        # Flyway migrations
└── src/test/java/org/sifunity/broker/queueservice/
    ├── unit/                # Unit tests
    ├── property/            # Property-based tests
    └── integration/         # Integration tests
```

### Adding New Features

1. Update the design document in `.kiro/specs/queue-service/design.md`
2. Add tasks to `.kiro/specs/queue-service/tasks.md`
3. Implement the feature following the layered architecture
4. Write unit tests and property-based tests
5. Update this README with new API endpoints or configuration

## License

This project is licensed under the MIT License. See [LICENSE](../LICENSE) for details.
